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
        addVisit("FunctionCall", this::visitMethodCall);
        addVisit("MethodCall", this::visitMethodCall);
    }

    private Void visitMethodCall(JmmNode methodCall, SymbolTable table) {

        if (methodCall.get("type").equals("invalid")) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(methodCall),
                    NodeUtils.getColumn(methodCall),
                    String.format("Method \"%s()\" is unknown.", methodCall.get("name")),
                    null)
            );
        }

        return null;
    }
}

