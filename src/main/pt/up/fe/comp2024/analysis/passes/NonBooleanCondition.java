package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.NodeUtils;

import java.util.List;

public class NonBooleanCondition extends AnalysisVisitor {

    @Override
    protected void buildVisitor() {
        addVisit("IfStmt", this::visitConditionalNode);
        addVisit("WhileStmt", this::visitConditionalNode);
    }

    private Void visitConditionalNode(JmmNode conditionalNode, SymbolTable table) {

        JmmNode conditionalExpression = conditionalNode.getChild(0);

        String type = conditionalExpression.get("type");
        Boolean isArray = conditionalExpression.get("isArray").equals("true");

        if (!type.equals("boolean") || isArray) {

            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(conditionalExpression),
                    NodeUtils.getColumn(conditionalExpression),
                    "Invalid use of non-boolean expression as condition.",
                    null)
            );
        }

        return null;

    }
}
