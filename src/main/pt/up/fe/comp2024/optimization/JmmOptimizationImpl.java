package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp2024.optimization.optimizers.ASTOptimizer;
import pt.up.fe.comp2024.symboltable.JmmSymbolTable;

import java.util.Collections;

public class JmmOptimizationImpl implements JmmOptimization {

    @Override
    public OllirResult toOllir(JmmSemanticsResult semanticsResult) {
        var visitor = new OllirGeneratorVisitor(semanticsResult.getSymbolTable());
        var ollirCode = visitor.visit(semanticsResult.getRootNode());

        return new OllirResult(semanticsResult, ollirCode, Collections.emptyList());
    }

    @Override
    public OllirResult optimize(OllirResult ollirResult) {

        //TODO: Do your OLLIR-based optimizations here

        return ollirResult;
    }

    @Override
    public JmmSemanticsResult optimize(JmmSemanticsResult semanticsResult) {

        if (semanticsResult.getConfig().get("optimize") == null || !semanticsResult.getConfig().get("optimize").equals("true")) {
            return semanticsResult;
        }

        new ASTOptimizer().visit(semanticsResult.getRootNode(), (JmmSymbolTable) semanticsResult.getSymbolTable());

        return semanticsResult;
    }
}
