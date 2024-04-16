package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.symboltable.JmmSymbolTable;

import java.util.List;

public class AttributeInStaticMethod extends AnalysisVisitor {

    String currentMethod;
    @Override
    protected void buildVisitor() {
        addVisit("MainMethodDeclaration", this::visitMethodDeclaration);
        addVisit("MethodDeclaration", this::visitMethodDeclaration);
        addVisit("Identifier", this::visitIdentifier);
        addVisit("AssignStmt", this::visitAssignStmt);
    }

    private Void visitMethodDeclaration(JmmNode methodDeclaration, JmmSymbolTable symbolTable){

        currentMethod = methodDeclaration.get("name");

        return null;
    }

    private Void visitIdentifier(JmmNode identifier, JmmSymbolTable symbolTable) {

        if (currentMethod == null) return null;

        if (!symbolTable.getMethodSymbol(currentMethod).isStatic())
            return null;

        String varName = identifier.get("value");

        checkFieldReference(identifier, symbolTable, varName);

        return null;
    }

    private Void visitAssignStmt(JmmNode assignStmt, JmmSymbolTable symbolTable) {

        if (currentMethod == null) return null;

        if (!symbolTable.getMethodSymbol(currentMethod).isStatic())
            return null;

        String varName = assignStmt.get("name");

        checkFieldReference(assignStmt, symbolTable, varName);

        return null;
    }

    private void checkFieldReference(JmmNode identifier, JmmSymbolTable symbolTable, String varName) {
        if (symbolTable.getParameters(currentMethod).stream().anyMatch(
                symbol -> symbol.getName().equals(varName)
        )) return;

        if (symbolTable.getLocalVariables(currentMethod).stream().anyMatch(
                symbol -> symbol.getName().equals(varName))
        ) return;

        if (symbolTable.getFields().stream().anyMatch(
                symbol -> symbol.getName().equals(varName)
        )){
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(identifier),
                    NodeUtils.getColumn(identifier),
                    String.format(
                            "Invalid use of non static field %s to in static method '%s()'.",
                            varName,
                            currentMethod
                    ),
                    null)
            );
        }
    }


}