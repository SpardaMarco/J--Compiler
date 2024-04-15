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
        addVisit("MainMethodDeclaration", this::visitMainMethodDeclaration);
        addVisit("Identifier", this::visitIdentifier);
    }

    private Void visitMainMethodDeclaration(JmmNode methodDeclaration, JmmSymbolTable symbolTable){

        currentMethod = methodDeclaration.get("name");

        return null;
    }

    private Void visitIdentifier(JmmNode identifier, JmmSymbolTable symbolTable) {

        if (currentMethod == null) return null;

        String varName = identifier.get("value");

        if (symbolTable.getParameters(currentMethod).contains(varName))
            return null;

        if (symbolTable.getLocalVariables(currentMethod).contains(varName))
            return null;

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
        return null;
    }
}