package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.NodeUtils;

import java.util.List;
import java.util.Optional;

public class IncompatibleAssignment extends AnalysisVisitor {

    @Override
    protected void buildVisitor() {
        addVisit("AssignStmt", this::visitAssignStmt);
    }

    private Void visitAssignStmt(JmmNode assignStmt, SymbolTable table) {

        if (invalidAssignment(assignStmt))
            return null;

        if (isLiteralAssignment(assignStmt)) {
            checkLiteralAssignment(assignStmt);
        } else {
            checkObjectAssignment(assignStmt, table);
        }
        return null;
    }

    private static boolean invalidAssignment(JmmNode assignStmt) {
        return assignStmt.getChild(0).get("type").equals("invalid") ||
                assignStmt.get("type").equals("invalid");
    }

    private boolean isLiteralAssignment(JmmNode assignStmt) {

        String assigneeType = assignStmt.get("type");
        String assignmentType = assignStmt.getChild(0).get("type");

        return assigneeType.equals("int") ||
                assigneeType.equals("boolean") ||
                assignmentType.equals("int") ||
                assignmentType.equals("boolean");
    }

    private void checkLiteralAssignment(JmmNode assignStmt) {

        String assigneeType = assignStmt.get("type");
        String assignmentType = assignStmt.getChild(0).get("type");
        Boolean isAssigneeArray = assignStmt.get("isArray").equals("true");
        Boolean isAssignmentArray = assignStmt.getChild(0).get("isArray").equals("true");

        if (!assigneeType.equals(assignmentType) || !isAssigneeArray.equals(isAssignmentArray)){
            addIncompabilityReport(
                    assignStmt,
                    "Incompatible assignment. Attempted to assign expression of type %s to variable of type %s."
            );
        }
    }

    private void checkObjectAssignment(JmmNode assignStmt, SymbolTable table) {

        String assigneeClass = assignStmt.get("type");
        String assignmentClass = assignStmt.getChild(0).get("type");
        Boolean isAssigneeArray = assignStmt.get("isArray").equals("true");
        Boolean isAssignmentArray = assignStmt.getChild(0).get("isArray").equals("true");

        if (!isAssigneeArray.equals(isAssignmentArray)) {
            addIncompabilityReport(
                    assignStmt,
                    "Incompatible assignment. Attempted to assign expression of type %s to variable of type %s."
            );
            return;
        }

        if (assigneeClass.equals(assignmentClass))
            return;

        if (!assigneeClass.equals(table.getSuper()) && assignmentClass.equals(table.getClassName()))
            addIncompabilityReport(
                    assignStmt,
                    "Incompatible assignment. Class %s does not extend class %s."
            );
    }

    private void addIncompabilityReport (JmmNode assignStmt, String format){

        String assigneeType = assignStmt.get("type");
        String assignmentType = assignStmt.getChild(0).get("type");
        Boolean isAssigneeArray = assignStmt.get("isArray").equals("true");
        Boolean isAssignmentArray = assignStmt.getChild(0).get("isArray").equals("true");

        String message = String.format(
                format,
                assignmentType + (isAssignmentArray ? "[]" : ""),
                assigneeType + (isAssigneeArray ? "[]" : "")
        );

        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(assignStmt),
                NodeUtils.getColumn(assignStmt),
                message,
                null)
        );

    }
}
