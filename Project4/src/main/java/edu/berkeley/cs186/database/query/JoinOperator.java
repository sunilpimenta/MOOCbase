package edu.berkeley.cs186.database.query;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import edu.berkeley.cs186.database.TransactionContext;
import edu.berkeley.cs186.database.common.iterator.BacktrackingIterator;
import edu.berkeley.cs186.database.databox.DataBox;
import edu.berkeley.cs186.database.databox.Type;
import edu.berkeley.cs186.database.memory.Page;
import edu.berkeley.cs186.database.table.Record;
import edu.berkeley.cs186.database.table.RecordId;
import edu.berkeley.cs186.database.table.Schema;
import edu.berkeley.cs186.database.table.stats.TableStats;

abstract class JoinOperator extends QueryOperator {
    enum JoinType {
        SNLJ,
        PNLJ,
        BNLJ,
        SORTMERGE
    }

    JoinType joinType;

    // the source operators
    private QueryOperator leftSource;
    private QueryOperator rightSource;

    // join column indices
    private int leftColumnIndex;
    private int rightColumnIndex;

    // join column names
    private String leftColumnName;
    private String rightColumnName;

    // current transaction
    private TransactionContext transaction;

    /**
     * Create a join operator that pulls tuples from leftSource and rightSource. Returns tuples for which
     * leftColumnName and rightColumnName are equal.
     *
     * @param leftSource the left source operator
     * @param rightSource the right source operator
     * @param leftColumnName the column to join on from leftSource
     * @param rightColumnName the column to join on from rightSource
     */
    JoinOperator(QueryOperator leftSource,
                 QueryOperator rightSource,
                 String leftColumnName,
                 String rightColumnName,
                 TransactionContext transaction,
                 JoinType joinType) {
        super(OperatorType.JOIN);
        this.joinType = joinType;
        this.leftSource = leftSource;
        this.rightSource = rightSource;
        this.leftColumnName = leftColumnName;
        this.rightColumnName = rightColumnName;
        this.setOutputSchema(this.computeSchema());
        this.transaction = transaction;
    }

    @Override
    public QueryOperator getSource() {
        throw new QueryPlanException("There is no single source for join operators. Please use " +
                                     "getRightSource and getLeftSource and the corresponding set methods.");
    }

    @Override
    public Schema computeSchema() {
        // Get lists of the field names of the records
        Schema leftSchema = this.leftSource.getOutputSchema();
        Schema rightSchema = this.rightSource.getOutputSchema();
        List<String> leftSchemaNames = new ArrayList<>(leftSchema.getFieldNames());
        List<String> rightSchemaNames = new ArrayList<>(rightSchema.getFieldNames());

        // Set up join column attributes
        this.leftColumnName = this.checkSchemaForColumn(leftSchema, this.leftColumnName);
        this.leftColumnIndex = leftSchemaNames.indexOf(leftColumnName);
        this.rightColumnName = this.checkSchemaForColumn(rightSchema, this.rightColumnName);
        this.rightColumnIndex = rightSchemaNames.indexOf(rightColumnName);

        // Check that the types of the columns of each input operator match
        List<Type> leftSchemaTypes = new ArrayList<>(leftSchema.getFieldTypes());
        List<Type> rightSchemaTypes = new ArrayList<>(rightSchema.getFieldTypes());
        if (!leftSchemaTypes.get(this.leftColumnIndex).getClass().equals(rightSchemaTypes.get(
                this.rightColumnIndex).getClass())) {
            throw new QueryPlanException("Mismatched types of columns " + leftColumnName + " and "
                    + rightColumnName + ".");
        }
        leftSchemaNames.addAll(rightSchemaNames);
        leftSchemaTypes.addAll(rightSchemaTypes);

        // Return concatenated schema
        return new Schema(leftSchemaNames, leftSchemaTypes);
    }

    @Override
    public String str() {
        return "type: " + this.joinType + " (cost: " + this.getIOCost() + ")" +
                "\nleftColumn: " + this.leftColumnName +
                "\nrightColumn: " + this.rightColumnName;
    }

    @Override
    public String toString() {
        String r = this.str();
        if (this.leftSource != null) {
            r += "\n" + ("(left)\n" + this.leftSource.toString()).replaceAll("(?m)^", "\t");
        }
        if (this.rightSource != null) {
            if (this.leftSource != null) {
                r += "\n";
            }
            r += "\n" + ("(right)\n" + this.rightSource.toString()).replaceAll("(?m)^", "\t");
        }
        return r;
    }

    /**
     * Estimates the table statistics for the result of executing this query operator.
     *
     * @return estimated TableStats
     */
    @Override
    public TableStats estimateStats() {
        TableStats leftStats = this.leftSource.getStats();
        TableStats rightStats = this.rightSource.getStats();

        return leftStats.copyWithJoin(this.leftColumnIndex,
                rightStats,
                this.rightColumnIndex);
    }

    /**
     * @return the query operator which supplies the left records of the join
     */
    QueryOperator getLeftSource() {
        return this.leftSource;
    }

    /**
     * @return the query operator which supplies the right records of the join
     */
    QueryOperator getRightSource() {
        return this.rightSource;
    }

    /**
     * @return the name of the column being joined on in the left relation
     */
    public String getLeftColumnName() {
        return this.leftColumnName;
    }

    /**
     * @return the name of the column being joined on in the right relation
     */
    public String getRightColumnName() {
        return this.rightColumnName;
    }

    // Helpers /////////////////////////////////////////////////////////////////

    /**
     * @return the position of the column being joined on in the left relation's schema. Can be used to determine which
     * value in the left relation's records to check for equality on.
     */
    public int getLeftColumnIndex() {
        return this.leftColumnIndex;
    }

    /**
     * @return the position of the column being joined on in the right relation's schema. Can be used to determine which
     * value in the right relation's records to check for equality on.
     */
    public int getRightColumnIndex() {
        return this.rightColumnIndex;
    }

    /**
     * @return the transaction context this operator is being executed within
     */
    public TransactionContext getTransaction() {
        return this.transaction;
    }

    /**
     * @return a backtracking iterator over records in the table specified by `tableName`. This method will consume as
     * many pages as it can from `pageIter` up to a maximum of `maxPages` pages, and return an iterator over the records
     * on those pages.
     */
    public BacktrackingIterator<Record> getBlockIterator(String tableName, Iterator<Page> pageIter,
            int maxPages) {
        return this.transaction.getBlockIterator(tableName, pageIter, maxPages);
    }

    /**
     * @return a backtracking iterator over the records of the table specified by `tableName`
     */
    public BacktrackingIterator<Record> getRecordIterator(String tableName) {
        return this.transaction.getRecordIterator(tableName);
    }

    // Iterator ////////////////////////////////////////////////////////////////

    /**
     * All iterators for subclasses of JoinOperator should subclass from
     * JoinIterator; JoinIterator handles creating temporary tables out of the left and right
     * input operators.
     */
    protected abstract class JoinIterator implements Iterator<Record> {
        private String leftTableName;
        private String rightTableName;
        private TransactionContext transaction;

        public JoinIterator() {
            this.transaction = JoinOperator.this.transaction;
            if (JoinOperator.this.getLeftSource().isSequentialScan()) {
                this.leftTableName = ((SequentialScanOperator) JoinOperator.this.getLeftSource()).getTableName();
            } else {
                this.leftTableName = this.transaction.createTempTable(
                                         JoinOperator.this.getLeftSource().getOutputSchema());
                Iterator<Record> leftIter = JoinOperator.this.getLeftSource().iterator();
                while (leftIter.hasNext()) {
                    this.transaction.addRecord(this.leftTableName, leftIter.next().getValues());
                }
            }
            if (JoinOperator.this.getRightSource().isSequentialScan()) {
                this.rightTableName = ((SequentialScanOperator) JoinOperator.this.getRightSource()).getTableName();
            } else {
                this.rightTableName = this.transaction.createTempTable(
                                          JoinOperator.this.getRightSource().getOutputSchema());
                Iterator<Record> rightIter = JoinOperator.this.getRightSource().iterator();
                while (rightIter.hasNext()) {
                    this.transaction.addRecord(this.rightTableName, rightIter.next().getValues());
                }
            }
        }

        /**
         * @return the name of the table supplying left records to this join iterator
         */
        protected String getLeftTableName() {
            return this.leftTableName;
        }

        /**
         * @return the name of the table supplying right records to this join iterator
         */
        protected String getRightTableName() { return this.rightTableName; }

        /**
         * @return an iterator over the pages of the table supplying left records to this join iterator
         */
        protected BacktrackingIterator<Page> getLeftPageIterator() {
            return this.transaction.getPageIterator(this.leftTableName);
        }

        /**
         * @return an iterator over the pages of the table supplying right records to this join iterator
         */
        protected BacktrackingIterator<Page> getRightPageIterator() {
            return this.transaction.getPageIterator(this.rightTableName);
        }
    }
}
