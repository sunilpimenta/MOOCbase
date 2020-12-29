package edu.berkeley.cs186.database.concurrency;

import edu.berkeley.cs186.database.TransactionContext;
import edu.berkeley.cs186.database.common.Pair;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LockContext wraps around LockManager to provide the hierarchical structure
 * of multigranularity locking. Calls to acquire/release/etc. locks should
 * be mostly done through a LockContext, which provides access to locking
 * methods at a certain point in the hierarchy (database, table X, etc.)
 */
public class LockContext {
    // You should not remove any of these fields. You may add additional
    // fields/methods as you see fit.

    // The underlying lock manager.
    protected final LockManager lockman;

    // The parent LockContext object, or null if this LockContext is at the top of the hierarchy.
    protected final LockContext parent;

    // The name of the resource this LockContext represents.
    protected ResourceName name;

    // Whether this LockContext is readonly. If a LockContext is readonly, acquire/release/promote/escalate should
    // throw an UnsupportedOperationException.
    protected boolean readonly;

    // A mapping between transaction numbers, and the number of locks on children of this LockContext
    // that the transaction holds.
    protected final Map<Long, Integer> numChildLocks;

    // You should not modify or use this directly.
    protected final Map<Long, LockContext> children;

    // Whether or not any new child LockContexts should be marked readonly.
    protected boolean childLocksDisabled;

    protected int capacity;

    protected boolean autoEscalateEnabled;

    public LockContext(LockManager lockman, LockContext parent, Pair<String, Long> name) {
        this(lockman, parent, name, false);
    }

    protected LockContext(LockManager lockman, LockContext parent, Pair<String, Long> name,
                          boolean readonly) {
        this.lockman = lockman;
        this.parent = parent;
        if (parent == null) {
            this.name = new ResourceName(name);
        } else {
            this.name = new ResourceName(parent.getResourceName(), name);
        }
        this.readonly = readonly;
        this.numChildLocks = new ConcurrentHashMap<>();
        this.children = new ConcurrentHashMap<>();
        this.capacity = 0;
        this.childLocksDisabled = readonly;

        this.autoEscalateEnabled = false;
    }

    public boolean isAutoEscalateEnabled() {
        return this.autoEscalateEnabled;
    }


    /**
     * Gets a lock context corresponding to `name` from a lock manager.
     */
    public static LockContext fromResourceName(LockManager lockman, ResourceName name) {
        Iterator<Pair<String, Long>> names = name.getNames().iterator();
        LockContext ctx;
        Pair<String, Long> n1 = names.next();
        ctx = lockman.context(n1.getFirst(), n1.getSecond());
        while (names.hasNext()) {
            Pair<String, Long> p = names.next();
            ctx = ctx.childContext(p.getFirst(), p.getSecond());
        }
        return ctx;
    }


    /**
     * Get the name of the resource that this lock context pertains to.
     */
    public ResourceName getResourceName() {
        return name;
    }

    /**
     * Acquire a `lockType` lock, for transaction `transaction`.
     *
     * Note: you must make any necessary updates to numChildLocks, or else calls
     * to LockContext#getNumChildren will not work properly.
     *
     * @throws InvalidLockException if the request is invalid
     * @throws DuplicateLockRequestException if a lock is already held by the
     * transaction.
     * @throws UnsupportedOperationException if context is readonly
     */
    public void acquire(TransactionContext transaction, LockType lockType)
            throws InvalidLockException, DuplicateLockRequestException {
        if (transaction == null) {
            return;
        }
        if (this.readonly) {
            throw new UnsupportedOperationException("Context is readonly");
        }
        if (this.lockman.getLockType(transaction, this.getResourceName()) != LockType.NL) {
            throw new DuplicateLockRequestException("Lock is already held by TRANSACTION");
        }
        boolean canAcquire = true;
        if (this.saturation(transaction) > 0.0) {
            canAcquire = false;
        }

        if (!canAcquire) {
            throw new InvalidLockException("Request is invalid");
        }
        if (this.parentContext() != null) {
            LockType parentLock = this.lockman.getLockType(transaction, this.parentContext().getResourceName());
            if (!LockType.canBeParentLock(parentLock, lockType)) {
                throw new InvalidLockException("Request is invalid");
            }
        }
        if (this.hasSIXAncestor(transaction) && (lockType == LockType.IS || lockType == LockType.S)) {
            throw new InvalidLockException("Request is invalid");
        }
        this.lockman.acquire(transaction, this.getResourceName(), lockType);
        LockContext parent = this.parentContext();
        if (parent != null) {
            parent.numChildLocks.put(transaction.getTransNum(), parent.numChildLocks.getOrDefault(transaction.getTransNum(), 0) + 1);
        }
    }

    /**
     * Release `transaction`'s lock on `name`.
     *
     * Note: you *must* make any necessary updates to numChildLocks, or
     * else calls to LockContext#getNumChildren will not work properly.
     *
     * @throws NoLockHeldException if no lock on `name` is held by `transaction`
     * @throws InvalidLockException if the lock cannot be released because
     * doing so would violate multigranularity locking constraints
     * @throws UnsupportedOperationException if context is readonly
     */
    public void release(TransactionContext transaction)
            throws NoLockHeldException, InvalidLockException {
        if (transaction == null) {
            return;
        }
        if (this.readonly) {
            throw new UnsupportedOperationException("Context is readonly");
        }
        if (this.lockman.getLockType(transaction, this.getResourceName()) == LockType.NL) {
            throw new NoLockHeldException("No lock on NAME is held by TRANSACTION");
        }
        boolean canRelease = true;
        if (this.saturation(transaction) > 0.0) {
            canRelease = false;
        }
        if (!canRelease) {
            throw new InvalidLockException("Lock cannot be released");
        }

        this.lockman.release(transaction, this.getResourceName());
        LockContext parent = this.parentContext();
        if (parent != null) {
            parent.numChildLocks.put(transaction.getTransNum(), parent.numChildLocks.get(transaction.getTransNum()) - 1);
        }
    }

    /**
     * Promote `transaction`'s lock to `newLockType`. For promotion to SIX from
     * IS/IX, all S and IS locks on descendants must be simultaneously
     * released. The helper function sisDescendants may be helpful here.
     *
     * Note: you *must* make any necessary updates to numChildLocks, or else
     * calls to LockContext#getNumChildren will not work properly.
     *
     * @throws DuplicateLockRequestException if `transaction` already has a
     * `newLockType` lock
     * @throws NoLockHeldException if `transaction` has no lock
     * @throws InvalidLockException if the requested lock type is not a
     * promotion or promoting would cause the lock manager to enter an invalid
     * state (e.g. IS(parent), X(child)). A promotion from lock type A to lock
     * type B is valid if B is substitutable for A and B is not equal to A, or
     * if B is SIX and A is IS/IX/S, and invalid otherwise. hasSIXAncestor may
     * be helpful here.
     * @throws UnsupportedOperationException if context is readonly
     */
    public void promote(TransactionContext transaction, LockType newLockType)
            throws DuplicateLockRequestException, NoLockHeldException, InvalidLockException {
        if (transaction == null) {
            return;
        }
        LockType lock = this.lockman.getLockType(transaction, this.getResourceName());
        if (this.readonly) {
            throw new UnsupportedOperationException("Context is readonly");
        }
        if (lock == newLockType) {
            throw new DuplicateLockRequestException("TRANSACTION already has NEWLOCKTYPE lock");
        }
        if (lock == LockType.NL) {
            throw new NoLockHeldException("TRANSACTION has no lock");
        }
        if (!LockType.substitutable(newLockType, lock) || (this.hasSIXAncestor(transaction) && newLockType == LockType.SIX)) {
            throw new InvalidLockException("Invalid promotion");
        }
        if (newLockType == LockType.SIX) {
            List<ResourceName> releaseNames = this.sisDescendants(transaction);
            releaseNames.add(this.getResourceName());

            for (ResourceName r : releaseNames) {
                if (LockContext.fromResourceName(this.lockman, r).parentContext() != null) {
                    LockContext parent = LockContext.fromResourceName(this.lockman, r).parentContext();
                    parent.numChildLocks.put(transaction.getTransNum(), parent.numChildLocks.get(transaction.getTransNum()) - 1);
                }
            }
            this.lockman.acquireAndRelease(transaction, this.getResourceName(), newLockType, releaseNames);
            return;
        }
        this.lockman.promote(transaction, this.getResourceName(), newLockType);
    }

    /**
     * Escalate `transaction`'s lock from descendants of this context to this
     * level, using either an S or X lock. There should be no descendant locks
     * after this call, and every operation valid on descendants of this context
     * before this call must still be valid. You should only make *one* mutating
     * call to the lock manager, and should only request information about
     * TRANSACTION from the lock manager.
     *
     * For example, if a transaction has the following locks:
     *
     *                    IX(database)
     *                    /         \
     *               IX(table1)    S(table2)
     *                /      \
     *    S(table1 page3)  X(table1 page5)
     *
     * then after table1Context.escalate(transaction) is called, we should have:
     *
     *                    IX(database)
     *                    /         \
     *               X(table1)     S(table2)
     *
     * You should not make any mutating calls if the locks held by the
     * transaction do not change (such as when you call escalate multiple times
     * in a row).
     *
     * Note: you *must* make any necessary updates to numChildLocks of all
     * relevant contexts, or else calls to LockContext#getNumChildren will not
     * work properly.
     *
     * @throws NoLockHeldException if `transaction` has no lock at this level
     * @throws UnsupportedOperationException if context is readonly
     */
    public void escalate(TransactionContext transaction) throws NoLockHeldException {
        if (transaction == null) {
            return;
        }
        if (this.readonly) {
            throw new UnsupportedOperationException("Context is readonly");
        }
        if (getExplicitLockType(transaction) == LockType.NL) {
            throw new NoLockHeldException("TRANSACTION has no lock at this level");
        }
        if (this.getExplicitLockType(transaction) == LockType.S || this.getExplicitLockType(transaction) == LockType.X) {
            return;
        }
        List<ResourceName> releaseLocks = new ArrayList<>();
        List<Lock> locks = this.lockman.getLocks(transaction);
        for (Lock l : locks) {
            if (l.name.isDescendantOf(this.getResourceName())) {
                releaseLocks.add(l.name);
            }
        }
        releaseLocks.add(this.getResourceName());

        for (ResourceName r : releaseLocks) {
            if (LockContext.fromResourceName(this.lockman, r).parentContext() != null) {
                LockContext parent = LockContext.fromResourceName(this.lockman, r).parentContext();
                parent.numChildLocks.put(transaction.getTransNum(), parent.numChildLocks.get(transaction.getTransNum()) - 1);
            }
        }

        if (this.getExplicitLockType(transaction) == LockType.IS) {
            this.lockman.acquireAndRelease(transaction, this.getResourceName(), LockType.S, releaseLocks);
        } else {
            this.lockman.acquireAndRelease(transaction, this.getResourceName(), LockType.X, releaseLocks);
        }
        this.numChildLocks.put(transaction.getTransNum(), 0);
    }


    /**
     * Get the type of lock that `transaction` holds at this level, or NL if no
     * lock is held at this level.
     */
    public LockType getExplicitLockType(TransactionContext transaction) {
        if (transaction == null) return LockType.NL;
        return lockman.getLockType(transaction, name);
    }

    /**
     * Gets the type of lock that the transaction has at this level, either
     * implicitly (e.g. explicit S lock at higher level implies S lock at this
     * level) or explicitly. Returns NL if there is no explicit nor implicit
     * lock.
     */
    public LockType getEffectiveLockType(TransactionContext transaction) {
        if (transaction == null) return LockType.NL;
        LockType result = getExplicitLockType(transaction);
        if (result == LockType.NL && parent != null) {
            result = parent.getEffectiveLockType(transaction);
            if (result == LockType.IS || result == LockType.IX) {
                return LockType.NL;
            }
        }
        return result;
    }

    /**
     * Helper method to see if the transaction holds a SIX lock at an ancestor
     * of this context
     * @param transaction the transaction
     * @return true if holds a SIX at an ancestor, false if not
     */
    private boolean hasSIXAncestor(TransactionContext transaction) {
        if (this.parentContext() == null) {
            return false;
        }
        List<Lock> locks = this.lockman.getLocks(transaction);
        for (Lock l : locks) {
            if (this.getResourceName().isDescendantOf(l.name) && l.lockType == LockType.SIX) {
                return true;
            }
        }
        return false;
    }

    /**
     * Helper method to get a list of resourceNames of all locks that are S or
     * IS and are descendants of current context for the given transaction.
     * @param transaction the given transaction
     * @return a list of ResourceNames of descendants which the transaction
     * holds an S or IS lock.
     */
    private List<ResourceName> sisDescendants(TransactionContext transaction) {
        List<ResourceName> descendants = new ArrayList<>();
        List<Lock> locks = this.lockman.getLocks(transaction);
        for (Lock l : locks) {
            if (l.name.isDescendantOf(this.getResourceName()) && (l.lockType == LockType.S || l.lockType == LockType.IS)) {
                descendants.add(l.name);
            }
        }
        return descendants;
    }

    /**
     * Disables locking descendants. This causes all new child contexts of this
     * context to be readonly. This is used for indices and temporary tables
     * (where we disallow finer-grain locks), the former due to complexity
     * locking B+ trees, and the latter due to the fact that temporary tables
     * are only accessible to one transaction, so finer-grain locks make no
     * sense.
     */
    public void disableChildLocks() {
        this.childLocksDisabled = true;
    }

    /**
     * Gets the parent context.
     */
    public LockContext parentContext() {
        return parent;
    }

    /**
     * Gets the context for the child with name `name` and readable name
     * `readable`
     */
    public synchronized LockContext childContext(String readable, long name) {
        LockContext temp = new LockContext(lockman, this, new Pair<>(readable, name),
                this.childLocksDisabled || this.readonly);
        LockContext child = this.children.putIfAbsent(name, temp);
        if (child == null) {
            child = temp;
        }
        if (child.name.getCurrentName().getFirst() == null && readable != null) {
            child.name = new ResourceName(this.name, new Pair<>(readable, name));
        }
        return child;
    }

    /**
     * Gets the context for the child with name `name`.
     */
    public synchronized LockContext childContext(long name) {
        return childContext(Long.toString(name), name);
    }

    /**
     * Gets the number of locks held on children a single transaction.
     */
    public int getNumChildren(TransactionContext transaction) {
        return numChildLocks.getOrDefault(transaction.getTransNum(), 0);
    }


    /**
     * Gets the capacity. Defaults to number of child contexts if never explicitly set.
     */
    public int capacity() {
        return this.capacity == 0 ? this.children.size() : this.capacity;
    }

    public double saturation(TransactionContext transaction) {
        if (transaction == null || capacity() == 0) {
            return 0.0;
        }
        return ((double) numChildLocks.getOrDefault(transaction.getTransNum(), 0)) / capacity();
    }

    @Override
    public String toString() {
        return "LockContext(" + name.toString() + ")";
    }
}

