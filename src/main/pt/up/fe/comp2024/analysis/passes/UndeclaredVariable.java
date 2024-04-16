package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.symboltable.JmmSymbolTable;

/**
 * Checks if the type of the expression in a return statement is compatible with the method return type.
 *
 * @author JBispo
 */
public class UndeclaredVariable extends AnalysisVisitor {
    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit("MethodDeclaration", this::visitMethodDecl);
        addVisit("MainMethodDeclaration", this::visitMethodDecl);
        addVisit("AssignStmt", this::visitAssignment);
        addVisit("ArrayAssignStmt", this::visitAssignment);
        addVisit("Identifier", this::visitIdentifier);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitAssignment(JmmNode assignment, JmmSymbolTable table) {
        String variable = assignment.get("name");

        if (table.getVarDeclaration(variable, currentMethod) != null)
            return null;

        // Create error report
        var message = String.format("Variable '%s' does not exist.", variable);
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(assignment),
                NodeUtils.getColumn(assignment),
                message,
                null)
        );

        return null;
    }

    private Void visitIdentifier(JmmNode identifier, SymbolTable table) {

        if (!identifier.get("type").equals("invalid")) return null;

        String variable = identifier.get("value");

        var message = String.format("Variable \"%s\" does not exist.", variable);
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(identifier),
                NodeUtils.getColumn(identifier),
                message,
                null)
        );

        return null;
    }
}
