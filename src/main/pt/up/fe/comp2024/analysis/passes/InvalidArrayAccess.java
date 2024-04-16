package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.NodeUtils;

import java.util.List;

public class InvalidArrayAccess extends AnalysisVisitor {

    @Override
    protected void buildVisitor() {

        addVisit("ArrayAccessOp", this::visitArrayAccessOp);
        addVisit("ArrayAssignStmt", this::visitArrayAssignStmt);
    }

    private Void visitArrayAccessOp(JmmNode arrayAccess, SymbolTable table) {

        if (arrayAccess.getChild(0).get("type").equals("invalid"))
            return null;

        if (arrayAccess.getChild(1).get("type").equals("invalid"))
            return null;

        if (arrayAccess.get("type").equals("invalid")) {

            JmmNode accessed = arrayAccess.getChild(0);

            String message = String.format(
                "Invalid array access operation on expression of type '%s'.",
                    accessed.get("type")
            );

            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(arrayAccess),
                    NodeUtils.getColumn(arrayAccess),
                    message,
                    null)
            );
        }

        return null;
    }

    private Void visitArrayAssignStmt(JmmNode arrayAssignStmt, SymbolTable table) {

        checkIndex(arrayAssignStmt);
        checkArray(arrayAssignStmt);

        return null;
    }

    private void checkIndex(JmmNode arrayAssignStmt) {

        JmmNode index = arrayAssignStmt.getChild(0);

        String indexType = index.get("type");
        if (indexType.equals("invalid"))
            return;
        else if (indexType.equals("undefined")){
            index.put("type", "int");
            index.put("isArray", "false");
            return;
        } else if (indexType.equals("int") && index.get("isArray").equals("false"))
            return;

        String message = String.format(
                "Invalid array access operation on '%s' with bad index.",
                arrayAssignStmt.get("name")
        );

        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(arrayAssignStmt),
                NodeUtils.getColumn(arrayAssignStmt),
                message,
                null)
        );
    }

    private void checkArray(JmmNode arrayAssignStmt) {

        String arrayType = arrayAssignStmt.get("type");

        if (arrayType.equals("invalid"))
            return;

        Boolean isArray = arrayAssignStmt.get("isArray").equals("true");

        if (!isArray){
            String message = String.format(
                    "Invalid array access operation on non-array '%s'.",
                    arrayAssignStmt.get("name")
            );

            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(arrayAssignStmt),
                    NodeUtils.getColumn(arrayAssignStmt),
                    message,
                    null)
            );
        }
    }
}
