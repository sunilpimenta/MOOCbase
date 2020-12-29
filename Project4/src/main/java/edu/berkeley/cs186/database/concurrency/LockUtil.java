package edu.berkeley.cs186.database.concurrency;

import edu.berkeley.cs186.database.TransactionContext;

/**
 * LockUtil is a declarative layer which simplifies multigranularity lock
 * acquisition for the user (you, in the last task of Part 2). Generally
 * speaking, you should use LockUtil for lock acquisition instead of calling
 * LockContext methods directly.
 */
public class LockUtil {
    /**
     * Ensure that the current transaction can perform actions requiring
     * `requestType` on `lockContext`.
     *
     * `requestType` is guaranteed to be one of: S, X, NL.
     *
     * This method should promote/escalate/acquire as needed, but should only
     * grant the least permissive set of locks needed. We recommend that you
     * think about what to do in each of the following cases:
     * - The current lock type can effectively substitute the requested type
     * - The current lock type is IX and the requested lock is S
     * - The current lock type is an intent lock
     * - None of the above
     *
     * You may find it useful to create a helper method that ensures you have
     * the appropriate locks on all ancestors.
     */
    public static void ensureSufficientLockHeld(LockContext lockContext, LockType requestType) {
        // requestType must be S, X, or NL
        assert (requestType == LockType.S || requestType == LockType.X || requestType == LockType.NL);

        // Do nothing if the transaction or lockContext is null
        TransactionContext transaction = TransactionContext.getTransaction();
        if (transaction == null || lockContext == null || requestType == LockType.NL) return;

        // You may find these variables useful
        LockContext parentContext = lockContext.parentContext();
        LockType effectiveLockType = lockContext.getEffectiveLockType(transaction);
        LockType explicitLockType = lockContext.getExplicitLockType(transaction);

        if (effectiveLockType == LockType.X) {
            return;
        }
        if (effectiveLockType == requestType && requestType == LockType.S) {
            return;
        }
        if (parentContext != null) {
            if (parentContext.isAutoEscalateEnabled() && parentContext.saturation(transaction) >= 0.2 && parentContext.capacity() >= 10) {
                parentContext.escalate(transaction);
                ensureSufficientLockHeld(lockContext, requestType);
                return;
            }
        }
        if (parentContext != null) {
            checkAncestors(transaction, parentContext, requestType);
        }
        acquireLock(transaction, lockContext, requestType);

    }

    public static void checkAncestors(TransactionContext transaction, LockContext lockContext, LockType lockType) {
        if (lockContext.parentContext() != null) {
            checkAncestors(transaction, lockContext.parentContext(), lockType);
        }

        if (lockType == LockType.S) {
            if (lockContext.getExplicitLockType(transaction) == LockType.NL) {
                lockContext.acquire(transaction, LockType.IS);
            }
        } else if (lockType == LockType.X) {
            if (lockContext.getExplicitLockType(transaction) == LockType.NL) {
                lockContext.acquire(transaction, LockType.IX);
            } else if (lockContext.getExplicitLockType(transaction) == LockType.IS){
                lockContext.promote(transaction, LockType.IX);
            }
        }
    }

    public static void acquireLock(TransactionContext transaction, LockContext lockContext, LockType lockType) {
        if (lockType == LockType.S) {
            if (lockContext.getExplicitLockType(transaction) == LockType.NL) {
                lockContext.acquire(transaction, lockType);
            } else if (lockContext.getExplicitLockType(transaction) == LockType.IX) {
                lockContext.promote(transaction, LockType.SIX);
            } else if (lockContext.getExplicitLockType(transaction) == LockType.IS) {
                lockContext.escalate(transaction);
            }
        } else if (lockType == LockType.X) {
            if (lockContext.getExplicitLockType(transaction) == LockType.NL) {
                LockContext parent = lockContext.parentContext();
                if (parent != null && parent.getExplicitLockType(transaction) == LockType.S) {
                    parent.promote(transaction, LockType.SIX);
                }
                lockContext.acquire(transaction, lockType);
            } else if (lockContext.getExplicitLockType(transaction) == LockType.S){
                lockContext.escalate(transaction);
                lockContext.promote(transaction, lockType);
            }
        }
    }
}
