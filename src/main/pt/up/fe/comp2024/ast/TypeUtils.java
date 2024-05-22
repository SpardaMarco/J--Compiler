package pt.up.fe.comp2024.ast;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;

import static pt.up.fe.comp2024.ast.Kind.*;

public class TypeUtils {
    private static final String INT_TYPE_NAME = "int";
    private static final String BOOLEAN_TYPE_NAME = "boolean";
    private static final String VOID_TYPE_NAME = "void";

    public static String getIntTypeName() {
        return INT_TYPE_NAME;
    }

    public static String getBooleanTypeName() {
        return BOOLEAN_TYPE_NAME;
    }

    public static String getVoidTypeName() {
        return VOID_TYPE_NAME;
    }

    /**
     * Gets the {@link Type} of an arbitrary expression.
     *
     * @param expr
     * @param table
     * @return
     */
    public static Type getExprType(JmmNode expr, SymbolTable table) {
        var kind = Kind.fromString(expr.getKind());

        Type type = switch (kind) {
            case METHOD_CALL -> getMethodCallType(expr);
            case BINARY_OP -> getBinExprType(expr);
            case IDENTIFIER, ASSIGN_STMT, ARRAY_ASSIGN_STMT -> getVarExprType(expr, table);
            case INTEGER_LITERAL -> new Type(INT_TYPE_NAME, false);
            case BOOLEAN_LITERAL -> new Type(BOOLEAN_TYPE_NAME, false);
            case PAREN_EXPR -> getExprType(expr.getChildren().get(0), table);
            case OBJECT_DECLARATION, ARRAY_ACCESS_OP, ARRAY_EXPRESSION, ATTRIBUTE, UNARY_OP ->
                    new Type(expr.get("type"), expr.get("isArray").equals("true"));
            default -> throw new UnsupportedOperationException("Can't compute type for expression kind '" + kind + "'");
        };

        return type;
    }

    private static Type getMethodCallType(JmmNode methodCall) {
        if (methodCall.get("type").equals("void")) {
            return new Type(VOID_TYPE_NAME, false);
        }

        return new Type(methodCall.get("type"), methodCall.get("isArray").equals("true"));
    }

    private static Type getBinExprType(JmmNode binaryExpr) {
        String operator = binaryExpr.get("op");

        return switch (operator) {
            case "*", "/", "+", "-" -> new Type(INT_TYPE_NAME, false);
            case "<", "&&" -> new Type(BOOLEAN_TYPE_NAME, false);

            default ->
                    throw new RuntimeException("Unknown operator '" + operator + "' of expression '" + binaryExpr + "'");
        };
    }

    private static Type getVarExprType(JmmNode varRefExpr, SymbolTable table) {
        String methodName;

        if (varRefExpr.getAncestor(METHOD_DECLARATION).isPresent()) {
            methodName = varRefExpr.getAncestor(METHOD_DECLARATION).get().get("name");
        } else methodName = varRefExpr.getAncestor(MAIN_METHOD_DECLARATION).get().get("name");

        String id;

        var isAssignStmt = varRefExpr.getKind().equals(ASSIGN_STMT.toString());
        var isArrayAssignStmt = varRefExpr.getKind().equals(ARRAY_ASSIGN_STMT.toString());

        if (isAssignStmt || isArrayAssignStmt) {
            id = varRefExpr.get("name");
        } else id = varRefExpr.get("value");

        var locals = table.getLocalVariables(methodName);
        for (var local : locals) {
            if (id.equals(local.getName())) {
                return local.getType();
            }
        }

        var params = table.getParameters(methodName);
        for (var param : params) {
            if (id.equals(param.getName())) {
                return param.getType();
            }
        }

        var fields = table.getFields();
        for (var field : fields) {
            if (id.equals(field.getName())) {
                return field.getType();
            }
        }

        var isArray = varRefExpr.get("isArray").equals("true");

        return new Type(VOID_TYPE_NAME, isArray);
    }

    /**
     * @param sourceType
     * @param destinationType
     * @return true if sourceType can be assigned to destinationType
     */
    public static boolean areTypesAssignable(Type sourceType, Type destinationType) {
        // TODO: Simple implementation that needs to be expanded
        return sourceType.getName().equals(destinationType.getName());
    }
}
