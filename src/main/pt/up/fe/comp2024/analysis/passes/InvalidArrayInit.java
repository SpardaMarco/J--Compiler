package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.NodeUtils;

public class InvalidArrayInit extends AnalysisVisitor {

    @Override
    protected void buildVisitor() {

        addVisit("ArrayExpression", this::visitArrayExpression);
    }

    private Void visitArrayExpression(JmmNode arrayExpression, SymbolTable table) {

        if (!arrayExpression.get("type").equals("invalid"))
            return null;

        String message = String.format(
                "Invalid array initialization. Array contains invalid values or of different types."
        );

        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(arrayExpression),
                NodeUtils.getColumn(arrayExpression),
                message,
                null)
        );

        return null;
    }
}
