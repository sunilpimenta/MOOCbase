package edu.berkeley.cs186.database.common;

public enum PredicateOperator {
    EQUALS,
    NOT_EQUALS,
    LESS_THAN,
    LESS_THAN_EQUALS,
    GREATER_THAN,
    GREATER_THAN_EQUALS;

    public <T extends Comparable<T>> boolean evaluate(T a, T b) {
        switch (this) {
        case EQUALS:
            return a.compareTo(b) == 0;
        case NOT_EQUALS:
            return a.compareTo(b) != 0;
        case LESS_THAN:
            return a.compareTo(b) < 0;
        case LESS_THAN_EQUALS:
            return a.compareTo(b) <= 0;
        case GREATER_THAN:
            return a.compareTo(b) > 0;
        case GREATER_THAN_EQUALS:
            return a.compareTo(b) >= 0;
        }
        return false;
    }

    public static PredicateOperator fromSymbol(String s) {
        switch(s) {
            case "=":;
            case "==": return EQUALS;
            case "!=":;
            case "<>": return NOT_EQUALS;
            case "<": return LESS_THAN;
            case "<=": return LESS_THAN_EQUALS;
            case ">": return GREATER_THAN;
            case ">=": return GREATER_THAN_EQUALS;
            default: return null;
        }
    }

    public static PredicateOperator reverse(PredicateOperator p) {
        switch(p) {
            case LESS_THAN: return GREATER_THAN;
            case LESS_THAN_EQUALS: return GREATER_THAN_EQUALS;
            case GREATER_THAN: return LESS_THAN;
            case GREATER_THAN_EQUALS: return LESS_THAN_EQUALS;
            default: return p;
        }
    }
}
