package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.NodeUtils;

import java.util.List;

public class InvalidArrayIndex extends AnalysisVisitor {

    @Override
    protected void buildVisitor() {

        addVisit("ArrayAccessOp", this::visitArrayAccessOp);
    }

    private Void visitArrayAccessOp(JmmNode arrayAccess, SymbolTable table) {

        JmmNode index = arrayAccess.getChild(1);

        if (index.get("type").equals("int"))
            return null;

        String message = String.format(
                "Invalid array access operation with index of type %s",
                index.get("type")
        );

        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(index),
                NodeUtils.getColumn(index),
                message,
                null)
        );

        return null;
    }
}
