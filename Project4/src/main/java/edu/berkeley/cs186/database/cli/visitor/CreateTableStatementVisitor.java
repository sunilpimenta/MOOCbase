package edu.berkeley.cs186.database.cli.visitor;

import java.util.ArrayList;
import java.util.List;

import edu.berkeley.cs186.database.Transaction;
import edu.berkeley.cs186.database.databox.Type;
import edu.berkeley.cs186.database.cli.parser.*;
import edu.berkeley.cs186.database.table.Schema;

public class CreateTableStatementVisitor extends StatementVisitor {
    public String tableName;
    public List<String> fieldNames = new ArrayList<>();
    public List<Type> fieldTypes = new ArrayList<>();
    public List<String> errorMessages = new ArrayList<>();

    @Override
    public void visit(ASTTableName node, Object data) {
        this.tableName = (String) node.jjtGetValue();
    }

    @Override
    public void visit(ASTColumnDef node, Object data) {
        Object[] components = (Object[]) node.jjtGetValue();
        String fieldName = (String) components[0];
        String fieldType = (String) components[1];
        Token param = (Token) components[2];
        fieldNames.add(fieldName);
        switch(fieldType.toLowerCase()) {
            case "int":;
            case "integer":
                fieldTypes.add(Type.intType());
                break;
            case "char":;
            case "varchar":;
            case "string":
                if(param == null) {
                    errorMessages.add(String.format("Missing length for %s(n).", fieldType));
                    return;
                }
                String s = param.image;
                if (s.indexOf('.') >= 0) {
                    errorMessages.add(String.format("Length of %s(n) must be integer, not `%s`.", fieldType, s));
                    return;
                }
                fieldTypes.add(Type.stringType(Integer.parseInt(s)));
                break;
            case "float":
                fieldTypes.add(Type.floatType());
                break;
            case "long":
                fieldTypes.add(Type.longType());
                break;
            case "bool":;
            case "boolean":
                fieldTypes.add(Type.boolType());
                break;
            default:
                assert false: String.format(
                    "Invalid field type \"%s\"",
                    fieldType
                );
        }
    }

    public void prettyPrint() {
        System.out.println("CREATE TABLE " + this.tableName + "(");
        for(int i = 0; i < fieldTypes.size(); i++) {
            if (i > 0) System.out.println(",");
            System.out.print("   " + fieldNames.get(i) + " " + fieldTypes.get(i));
        }
        System.out.println("\n)");
    }

    public void execute(Transaction transaction) {
        // transaction
        if (this.errorMessages.size() > 0) {
            for(String msg: errorMessages) {
                System.out.println(msg);
            }
            System.out.println("Failed to execute CREATE TABLE.");
        } else {
            Schema schema = new Schema(this.fieldNames, this.fieldTypes);
            transaction.createTable(schema, this.tableName);
            System.out.println("CREATE TABLE " + tableName);
        }
    }

    @Override
    public StatementType getType() {
        return StatementType.CREATE_TABLE;
    }
}