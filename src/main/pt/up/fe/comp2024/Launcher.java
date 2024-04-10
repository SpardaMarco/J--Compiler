package pt.up.fe.comp2024;

import pt.up.fe.comp.TestUtils;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.jasmin.JasminResult;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp2024.analysis.ASTAnnotator;
import pt.up.fe.comp2024.analysis.JmmAnalysisImpl;
import pt.up.fe.comp2024.backend.JasminBackendImpl;
import pt.up.fe.comp2024.optimization.JmmOptimizationImpl;
import pt.up.fe.comp2024.parser.JmmParserImpl;
import pt.up.fe.comp2024.symboltable.JmmSymbolTableBuilder;
import pt.up.fe.specs.util.SpecsIo;
import pt.up.fe.specs.util.SpecsSystem;

import java.util.Map;
import java.util.Optional;

public class Launcher {
    public static void printSymbolTable(SymbolTable table) {
        System.out.println("Symbol Table:");

        System.out.println("Imports:");
        for (var imp: table.getImports()){
            System.out.println(imp);
        }

        System.out.printf("Class: %s\n",table.getClassName());

        System.out.printf("Superclass: %s\n", table.getSuper());

        for (var field: table.getFields()){
            if (field.getType().isArray())
                System.out.printf("Field: %s[] %s\n", field.getType().getName(), field.getName());
            else
                System.out.printf("Field: %s %s\n", field.getType().getName(), field.getName());
        }

        System.out.println();

        for (var method: table.getMethods()) {
            System.out.printf("Method %s\n", method);
            var type = table.getReturnType(method);
            if (type.isArray())
                System.out.printf("Return Type: %s[]\n", type.getName());
            else
                System.out.printf("Return Type: %s\n", type.getName());
            System.out.println();

            System.out.println("Parameters:");
            for (var param: table.getParameters(method)){
                if (param.getType().isArray())
                    System.out.printf("%s[] %s\n", param.getType().getName(), param.getName());
                else
                    System.out.printf("%s %s\n", param.getType().getName(), param.getName());
            }

            System.out.println("Local Variables:");
            for (var local: table.getLocalVariables(method)){
                if (local.getType().isArray())
                    System.out.printf("%s[] %s\n", local.getType().getName(), local.getName());
                else
                    System.out.printf("%s %s\n", local.getType().getName(), local.getName());
            }
        }
    }

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
        System.out.println(parserResult.getRootNode().toTree());
        SymbolTable table = JmmSymbolTableBuilder.build(parserResult.getRootNode());
        new ASTAnnotator().visit(parserResult.getRootNode(), table);
        System.out.println(parserResult.getRootNode().toTree());

//        SymbolTable table = JmmSymbolTableBuilder.build(parserResult.getRootNode());
//        printSymbolTable(table);

        // Semantic Analysis stage
        JmmAnalysisImpl sema = new JmmAnalysisImpl();
        JmmSemanticsResult semanticsResult = sema.semanticAnalysis(parserResult);

        System.out.println(parserResult.getRootNode().toTree());


        System.out.println("Number of reports: " + semanticsResult.getReports().size());
        for (Report report: semanticsResult.getReports()) {

            Optional<Exception> exception = report.getException();
            String msgExc = "";
            if (exception.isPresent())
                msgExc = exception.get().getMessage();
            System.out.println(String.format(
                    "Report: %s (%s,%s)\n%s\n",
                    report.getMessage(), report.getLine(), report.getColumn(), msgExc
            ));
        }
        //TestUtils.noErrors(semanticsResult.getReports());

        // Optimization stage
        //JmmOptimizationImpl ollirGen = new JmmOptimizationImpl();
        //OllirResult ollirResult = ollirGen.toOllir(semanticsResult);
        //TestUtils.noErrors(ollirResult.getReports());

        // Print OLLIR code
        //System.out.println(ollirResult.getOllirCode());

        // Code generation stage
         //JasminBackendImpl jasminGen = new JasminBackendImpl();
         //JasminResult jasminResult = jasminGen.toJasmin(ollirResult);
         //TestUtils.noErrors(jasminResult.getReports());

        // Print Jasmin code
        //System.out.println(jasminResult.getJasminCode());
    }
}
