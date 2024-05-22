package pt.up.fe.comp2024.optimization.optimizers;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2024.symboltable.JmmSymbolTable;

public class ASTOptimizer extends PreorderJmmVisitor<JmmSymbolTable, Void> {

    String currentMethod;

    public ASTOptimizer() {
        setDefaultValue(() -> null);
    }

    @Override
    protected void buildVisitor() {

        addVisit("MethodDeclaration", this::visitMethodDecl);
        addVisit("MainMethodDeclaration", this::visitMethodDecl);
    }

    private Void visitMethodDecl(JmmNode method, JmmSymbolTable table) {
        currentMethod = method.get("name");

        while (new ConstantPropagator().optimize(method, table) || new ConstantFolder().optimize(method, table)) ;

        return null;
    }
}