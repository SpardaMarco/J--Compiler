package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;

import java.util.List;
import java.util.Optional;

public class IncompatibleAssignment extends AnalysisVisitor {

    String currentMethod;
    @Override
    protected void buildVisitor() {
        addVisit("MethodDeclaration", this::visitMethodDecl);
        addVisit("MainMethodDeclaration", this::visitMethodDecl);
        addVisit("AssignStmt", this::visitAssignment);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitAssignment(JmmNode assignment, SymbolTable table) {

        String varName = assignment.get("name");

        Symbol variable = getVarDeclaration(varName, currentMethod, table);

        return null;
    }
}
