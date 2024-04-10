package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.NodeUtils;

import java.util.Arrays;
import java.util.Optional;

public class ArithmeticArrayOp extends AnalysisVisitor {

    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit("MethodDeclaration", this::visitMethodDecl);
        addVisit("MainMethodDeclaration", this::visitMethodDecl);
        addVisit("BinaryOp", this::visitBinaryOp);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }
    private Void visitBinaryOp(JmmNode binaryOp, SymbolTable table) {

        String op = binaryOp.get("op");

        if (!Arrays.asList("+", "-", "*", "/").contains(op)){
            return null;
        }

        JmmNode leftOperand = binaryOp.getChild(0);
        JmmNode rightOperand = binaryOp.getChild(1);

        validateOperand(leftOperand, table);
        validateOperand(rightOperand, table);

        return null;
    }

    private Void validateOperand(JmmNode operand, SymbolTable table) {

        if (isArrayType(operand, table)) {
            var message = String.format("Invalid use of array in arithmetic expression.");
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(operand),
                    NodeUtils.getColumn(operand),
                    message,
                    null)
            );
        }

        return null;
    }

    private boolean isArrayType(JmmNode operand, SymbolTable table) {

        if (operand.getKind().equals("ArrayExpression")) return true;

        if (operand.getKind().equals("Identifier")) {

            String varName = operand.get("value");

            Symbol declaration = getVarDeclaration(varName, currentMethod, table);

            if (declaration != null)
                return declaration.getType().isArray();
        }

        return false;
    }
}
