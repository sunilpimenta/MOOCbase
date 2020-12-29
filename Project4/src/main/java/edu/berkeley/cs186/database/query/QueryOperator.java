package edu.berkeley.cs186.database.query;

import java.util.Iterator;
import java.util.List;

import edu.berkeley.cs186.database.table.Record;
import edu.berkeley.cs186.database.table.Schema;
import edu.berkeley.cs186.database.table.stats.TableStats;

public abstract class QueryOperator implements Iterable<Record> {
    private QueryOperator source;
    private QueryOperator destination;
    private Schema outputSchema;
    protected TableStats stats;
    protected int cost;

    public enum OperatorType {
        JOIN,
        PROJECT,
        SELECT,
        GROUPBY,
        SEQSCAN,
        INDEXSCAN,
        MATERIALIZE,
    }

    private OperatorType type;

    /**
     * Creates a QueryOperator without a set source, destination, or schema.
     * @param type the operator's type (Join, Project, Select, etc...)
     */
    public QueryOperator(OperatorType type) {
        this.type = type;
        this.source = null;
        this.outputSchema = null;
        this.destination = null;
    }

    /**
     * Creates a QueryOperator with a set source, and computes the output schema accordingly.
     * @param type the operator's type (Join, Project, Select, etc...)
     * @param source the source operator
     */
    protected QueryOperator(OperatorType type, QueryOperator source) {
        this.source = source;
        this.type = type;
        this.outputSchema = this.computeSchema();
        this.destination = null;
    }

    /**
     * @return an enum value representing the type of this operator (Join, Project, Select, etc...)
     */
    public OperatorType getType() {
        return this.type;
    }

    /**
     * @return True if this operator is a join operator, false otherwise.
     */
    public boolean isJoin() {
        return this.type.equals(OperatorType.JOIN);
    }

    /**
     * @return True if this operator is a select operator, false otherwise.
     */
    public boolean isSelect() {
        return this.type.equals(OperatorType.SELECT);
    }

    /**
     * @return True if this operator is a project operator, false otherwise.
     */
    public boolean isProject() {
        return this.type.equals(OperatorType.PROJECT);
    }

    /**
     * @return True if this operator is a group by operator, false otherwise.
     */
    public boolean isGroupBy() {
        return this.type.equals(OperatorType.GROUPBY);
    }

    /**
     * @return True if this operator is a sequential scan operator, false otherwise.
     */
    public boolean isSequentialScan() {
        return this.type.equals(OperatorType.SEQSCAN);
    }

    /**
     * @return True if this operator is an index scan operator, false otherwise.
     */
    public boolean isIndexScan() {
        return this.type.equals(OperatorType.INDEXSCAN);
    }

    /**
     * @return the source operator from which this operator draws records from
     */
    public QueryOperator getSource() {
        return this.source;
    }

    /**
     * @return the destination operator to which this operator feeds record to
     */
    public QueryOperator getDestination() {
        return this.destination;
    }

    /**
     * Sets the source of this operator and uses it to compute the output schema
     */
    void setSource(QueryOperator source) {
        this.source = source;
        this.outputSchema = this.computeSchema();
    }

    /**
     * Sets the destination of this operator.
     */
    public void setDestination(QueryOperator destination) {
        this.destination = destination;
    }

    /**
     * @return the schema of the records obtained when executing this operator
     */
    Schema getOutputSchema() {
        return this.outputSchema;
    }

    /**
     * Sets the output schema of this operator. This should match the schema of the records of the iterator obtained
     * by calling execute().
     */
    void setOutputSchema(Schema schema) {
        this.outputSchema = schema;
    }

    /**
     * Computes the schema of this operator's output records.
     * @return a schema matching the schema of the records of the iterator obtained by calling execute()
     */
    protected abstract Schema computeSchema();

    /**
     * @return an iterator over the output records of this operator
     */
    public Iterator<Record> execute() {
        return iterator();
    }

    /**
     * @return an iterator over the output records of this operator
     */
    public abstract Iterator<Record> iterator();

    /**
     * Utility method that checks to see if a column is found in a schema using dot notation.
     *
     * @param fromSchema the schema to search in
     * @param specified the column name to search for
     * @return
     */
    public boolean checkColumnNameEquality(String fromSchema, String specified) {
        if (fromSchema.equals(specified)) {
            return true;
        }
        if (!specified.contains(".")) {
            String schemaColName = fromSchema;
            if (fromSchema.contains(".")) {
                String[] splits = fromSchema.split("\\.");
                schemaColName = splits[1];
            }

            return schemaColName.equals(specified);
        }
        return false;
    }

    /**
     * Utility method to determine whether or not a specified column name is valid with a given schema.
     *
     * @param schema
     * @param columnName
     */
    public String checkSchemaForColumn(Schema schema, String columnName) {
        List<String> schemaColumnNames = schema.getFieldNames();
        boolean found = false;
        String foundName = null;
        for (String sourceColumnName : schemaColumnNames) {
            if (this.checkColumnNameEquality(sourceColumnName, columnName)) {
                if (found) {
                    throw new QueryPlanException("Column " + columnName + " specified twice without disambiguation.");
                }
                found = true;
                foundName = sourceColumnName;
            }
        }
        if (!found) {
            throw new QueryPlanException("No column " + columnName + " found.");
        }
        return foundName;
    }

    public String str() {
        return "type: " + this.getType() + " (cost: " + this.getIOCost() + ")";
    }

    public String toString() {
        String r = this.str();
        if (this.source != null) {
            r += "\n" + this.source.toString().replaceAll("(?m)^", "\t");
        }
        return r;
    }

    /**
     * Estimates the table statistics for the result of executing this query operator.
     *
     * @return estimated TableStats
     */
    protected abstract TableStats estimateStats();

    /**
     * Estimates the IO cost of executing this query operator.
     *
     * @return estimated number of IO's performed
     */
    public abstract int estimateIOCost();

    public TableStats getStats() {
        return this.stats;
    }

    public int getIOCost() {
        return this.cost;
    }
}
