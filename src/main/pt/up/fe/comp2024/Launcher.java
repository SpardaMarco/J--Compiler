package pt.up.fe.comp2024;

import pt.up.fe.comp.TestUtils;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.jasmin.JasminResult;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp2024.analysis.ASTAnnotator;
import pt.up.fe.comp2024.analysis.JmmAnalysisImpl;
import pt.up.fe.comp2024.backend.JasminBackendImpl;
import pt.up.fe.comp2024.optimization.JmmOptimizationImpl;
import pt.up.fe.comp2024.parser.JmmParserImpl;
import pt.up.fe.comp2024.symboltable.JmmSymbolTable;
import pt.up.fe.comp2024.symboltable.JmmSymbolTableBuilder;
import pt.up.fe.specs.util.SpecsIo;
import pt.up.fe.specs.util.SpecsSystem;

import java.util.Map;

public class Launcher {

    public static void main(String[] args) {
        SpecsSystem.programStandardInit();
        Map<String, String> config = CompilerConfig.parseArgs(args);

        var inputFile = CompilerConfig.getInputFile(config).orElseThrow();
        if (!inputFile.isFile()) {
            throw new RuntimeException("Option '-i' expects a path to an existing input file, got '" + args[0] + "'.");
        }
        String code = SpecsIo.read(inputFile);

        // Parsing stage
        JmmParserImpl parser = new JmmParserImpl();
        JmmParserResult parserResult = parser.parse(code, config);
        for (var report : parserResult.getReports()) {
            System.out.println(report);
        }
        TestUtils.noErrors(parserResult.getReports());

        // Print AST
        //System.out.println(parserResult.getRootNode().toTree());

        JmmSymbolTable table = JmmSymbolTableBuilder.build(parserResult.getRootNode());
        //table.print();
        new ASTAnnotator().visit(parserResult.getRootNode(), table);

        // SymbolTable table = JmmSymbolTableBuilder.build(parserResult.getRootNode());
        // printSymbolTable(table);

        // Semantic Analysis stage
        JmmAnalysisImpl sema = new JmmAnalysisImpl();
        JmmSemanticsResult semanticsResult = sema.semanticAnalysis(parserResult);

        System.out.println(semanticsResult.getRootNode().toTree());
        //System.out.println(semanticsResult.getReports());
        TestUtils.noErrors(semanticsResult.getReports());

        // Optimization stage
        JmmOptimizationImpl ollirGen = new JmmOptimizationImpl();
        ollirGen.optimize(semanticsResult);
        System.out.println(semanticsResult.getRootNode().toTree());
        OllirResult ollirResult = ollirGen.toOllir(semanticsResult);
        TestUtils.noErrors(ollirResult.getReports());
        System.out.println(ollirResult.getOllirCode());
        ollirResult = ollirGen.optimize(ollirResult);

        TestUtils.noErrors(ollirResult.getReports());
        // Print OLLIR code
        System.out.println(ollirResult.getOllirCode());

        // Code generation stage
        JasminBackendImpl jasminGen = new JasminBackendImpl();

//        OllirResult ollirResult = new OllirResult(code, config);
        JasminResult jasminResult = jasminGen.toJasmin(ollirResult);
        System.out.println(jasminResult.getJasminCode());
        TestUtils.noErrors(jasminResult.getReports());
    }
}
