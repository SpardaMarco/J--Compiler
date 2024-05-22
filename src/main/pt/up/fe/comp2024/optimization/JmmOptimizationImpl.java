package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp2024.optimization.optimizers.ast.ASTOptimizer;
import pt.up.fe.comp2024.optimization.optimizers.ollir.RegisterOptimizer;
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


        String numRegisters = ollirResult.getConfig().get("registerAllocation");

        if (numRegisters == null || numRegisters.equals("-1")) return ollirResult;

        return new RegisterOptimizer(ollirResult, Integer.parseInt(numRegisters)).optimize();
    }

    @Override
    public JmmSemanticsResult optimize(JmmSemanticsResult semanticsResult) {

        String optFlag = semanticsResult.getConfig().get("optimize");

        if (optFlag != null && optFlag.equals("true")) {
            new ASTOptimizer().visit(
                    semanticsResult.getRootNode(),
                    (JmmSymbolTable) semanticsResult.getSymbolTable()
            );
        }

        return semanticsResult;
    }
}
