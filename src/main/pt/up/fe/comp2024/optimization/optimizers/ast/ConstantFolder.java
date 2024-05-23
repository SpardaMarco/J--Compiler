package pt.up.fe.comp2024.optimization.optimizers.ast;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp2024.symboltable.JmmSymbolTable;

public class ConstantFolder extends ConstantOptimizer {


    public ConstantFolder() {
        setDefaultValue(() -> null);
    }

    @Override
    protected void cleanUp() {
    }

    @Override
    protected void buildVisitor() {
        addVisit("ParenExpr", this::visitParenExpr);
        addVisit("BinaryOp", this::visitBinaryOp);
    }

    private Void visitParenExpr(JmmNode parenExpr, JmmSymbolTable table) {
        JmmNode expr = parenExpr.getChildren().get(0);
        if (expr.getKind().equals("IntegerLiteral") || expr.getKind().equals("BooleanLiteral")) {
            parenExpr.replace(expr);
            addOptimization();
        }
        return null;
    }

    private Void visitBinaryOp(JmmNode binaryOp, JmmSymbolTable table) {

        String op = binaryOp.get("op");
        JmmNode left = binaryOp.getChildren().get(0);
        JmmNode right = binaryOp.getChildren().get(1);

        switch (op) {
            case "+", "-", "*", "/" -> {
                if (left.getKind().equals("IntegerLiteral") && right.getKind().equals("IntegerLiteral")) {
                    JmmNode resultNode = foldArithmetic(op, left, right);
                    binaryOp.replace(resultNode);
                    addOptimization();
                }
            }
            case "&&" -> {
                if (left.getKind().equals("BooleanLiteral") && right.getKind().equals("BooleanLiteral")) {
                    JmmNode resultNode = foldConjunction(left, right);
                    binaryOp.getChildren().forEach(JmmNode::detach);
                    binaryOp.replace(resultNode);
                    addOptimization();
                }
            }
            case "<" -> {
                if (left.getKind().equals("IntegerLiteral") && right.getKind().equals("IntegerLiteral")) {
                    JmmNode resultNode = foldLessThan(left, right);
                    binaryOp.replace(resultNode);
                    addOptimization();
                }
            }
        }
        return null;
    }

    private JmmNode foldArithmetic(String op, JmmNode left, JmmNode right) {
        int leftValue = Integer.parseInt(left.get("value"));
        int rightValue = Integer.parseInt(right.get("value"));
        int result = switch (op) {
            case "+" -> leftValue + rightValue;
            case "-" -> leftValue - rightValue;
            case "*" -> leftValue * rightValue;
            case "/" -> leftValue / rightValue;
            default -> throw new RuntimeException("Invalid operator");
        };
        JmmNode resultNode = new JmmNodeImpl("IntegerLiteral");
        resultNode.put("value", Integer.toString(result));
        resultNode.put("type", "int");
        resultNode.put("isArray", "false");
        return resultNode;
    }

    private JmmNode foldConjunction(JmmNode left, JmmNode right) {
        boolean leftValue = Boolean.parseBoolean(left.get("value"));
        boolean rightValue = Boolean.parseBoolean(right.get("value"));
        boolean result = leftValue && rightValue;
        JmmNode resultNode = new JmmNodeImpl("BooleanLiteral");
        resultNode.put("value", Boolean.toString(result));
        resultNode.put("type", "boolean");
        resultNode.put("isArray", "false");
        return resultNode;
    }

    private JmmNode foldLessThan(JmmNode left, JmmNode right) {
        int leftValue = Integer.parseInt(left.get("value"));
        int rightValue = Integer.parseInt(right.get("value"));
        boolean result = leftValue < rightValue;
        JmmNode resultNode = new JmmNodeImpl("BooleanLiteral");
        resultNode.put("value", Boolean.toString(result));
        resultNode.put("type", "boolean");
        resultNode.put("isArray", "false");
        return resultNode;
    }
}