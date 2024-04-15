package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.NodeUtils;

import java.util.List;

public class IncompatibleOperands extends AnalysisVisitor {

    @Override
    protected void buildVisitor() {

        addVisit("BinaryOp", this::visitBinaryOp);
        addVisit("UnaryOp", this::visitUnaryOp);
    }

    private Void visitUnaryOp(JmmNode unaryOp, SymbolTable table) {

        JmmNode operand = unaryOp.getChild(0);

        String operation = unaryOp.get("op");

        switch (operation) {
            case "!" -> {
                checkOperand(operand, operation, "boolean");
            }
        }
        return null;
    }

    private Void visitBinaryOp(JmmNode binaryOp, SymbolTable table) {

        JmmNode leftOperand = binaryOp.getChild(0);
        JmmNode rightOperand = binaryOp.getChild(1);

        String operation = binaryOp.get("op");

        switch (operation) {

            case "+", "-", "*", "/", "<" -> {
                checkOperand(leftOperand, operation, "int");
                checkOperand(rightOperand, operation, "int");
            }
            case "%" -> {
                checkOperand(leftOperand, operation, "boolean");
                checkOperand(rightOperand, operation, "boolean");
            }
        }

        return null;
    }

    private void checkOperand(JmmNode operand, String operation, String type) {

        String operandType = operand.get("type");

        if (operandType.equals("invalid"))
            return;

        Boolean isArray = operand.get("isArray").equals("true");

        if (operandType.equals(type) && !isArray) return;


        var message = String.format(
                "Operand of wrong type, \"%s\". Operation \"%s\" only takes \"%s\" as argument",
                operandType + (isArray ? "[]" : ""), operation, type);
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(operand),
                NodeUtils.getColumn(operand),
                message,
                null)
        );
    }
}