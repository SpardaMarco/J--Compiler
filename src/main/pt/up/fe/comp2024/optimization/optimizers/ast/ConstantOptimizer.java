package pt.up.fe.comp2024.optimization.optimizers.ast;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PostorderJmmVisitor;
import pt.up.fe.comp2024.symboltable.JmmSymbolTable;

public abstract class ConstantOptimizer extends PostorderJmmVisitor<JmmSymbolTable, Void> {

    private int optimizations = 0;

    public boolean optimize(JmmNode method, JmmSymbolTable table) {

        visit(method, table);

        cleanUp();

        return optimizations > 0;
    }

    protected void addOptimization() {
        optimizations++;
    }

    protected void resetOptimization() {
        optimizations = 0;
    }

    abstract protected void cleanUp();
}
