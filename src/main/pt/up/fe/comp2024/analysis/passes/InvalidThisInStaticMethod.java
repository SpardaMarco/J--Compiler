package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.symboltable.JmmSymbolTable;
import pt.up.fe.comp2024.symboltable.MethodSymbol;

public class InvalidThisInStaticMethod extends AnalysisVisitor {

    String currentMethod;
    @Override
    protected void buildVisitor() {

        addVisit("MethodDeclaration", this::visitMethodDeclaration);
        addVisit("MainMethodDeclaration", this::visitMethodDeclaration);
        addVisit("This", this::visitThis);
    }

    public Void visitMethodDeclaration(JmmNode methodDecl, SymbolTable table) {

        currentMethod = methodDecl.get("name");
        return null;
    }

    public Void visitThis(JmmNode thisNode, JmmSymbolTable table) {

        if (currentMethod.equals("main")) {
            addThisNodeInvalidUseReport(thisNode);
            return null;
        }

        MethodSymbol method = table.getMethodSymbol(currentMethod);

        if (method.isStatic()){
            addThisNodeInvalidUseReport(thisNode);
        }

        return null;
    }

    private void addThisNodeInvalidUseReport(JmmNode thisNode) {
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(thisNode),
                NodeUtils.getColumn(thisNode),
                String.format(
                        "Invalid reference to \"this\" in static method \"%s()\".",
                        currentMethod
                ),
                null)
        );
    }
}