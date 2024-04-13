package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.NodeUtils;

public class UndeclaredMethod extends AnalysisVisitor {

    @Override
    protected void buildVisitor() {
        addVisit("functionCall", this::visitFunctionCall);
    }

    private Void visitFunctionCall(JmmNode functionCall, SymbolTable table) {

        if (functionCall.get("type").equals("invalid")) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(functionCall),
                    NodeUtils.getColumn(functionCall),
                    String.format("Method %s is unknown.", functionCall.get("name")),
                    null)
            );
        }

        return null;
    }
}

