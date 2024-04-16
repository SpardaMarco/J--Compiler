package pt.up.fe.comp2024.analysis;

import pt.up.fe.comp.jmm.analysis.JmmAnalysis;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.passes.*;
import pt.up.fe.comp2024.symboltable.JmmSymbolTable;
import pt.up.fe.comp2024.symboltable.JmmSymbolTableBuilder;

import java.util.ArrayList;
import java.util.List;

public class JmmAnalysisImpl implements JmmAnalysis {


    private final List<AnalysisPass> analysisPasses;

    public JmmAnalysisImpl() {

        this.analysisPasses = List.of(
                new AttributeInStaticMethod(),
                new Duplicates(),
                new IncompatibleAssignment(),
                new IncompatibleArguments(),
                new IncompatibleOperands(),
                new IncompatibleReturn(),
                new InvalidArrayAccess(),
                new InvalidArrayIndex(),
                new InvalidArrayInit(),
                new InvalidFieldAccess(),
                new InvalidThisInStaticMethod(),
                new InvalidVarargArgument(),
                new NonBooleanCondition(),
                new NotImportedClass(),
                new UndeclaredVariable(),
                new UndeclaredMethod()
        );

    }

    @Override
    public JmmSemanticsResult semanticAnalysis(JmmParserResult parserResult) {

        JmmNode rootNode = parserResult.getRootNode();

        JmmSymbolTable table = JmmSymbolTableBuilder.build(rootNode);

        new ASTAnnotator().visit(parserResult.getRootNode(), table);

        List<Report> reports = new ArrayList<>();

        for (var analysisPass : analysisPasses) {
            try {
                var passReports = analysisPass.analyze(rootNode, table);
                reports.addAll(passReports);
            } catch (Exception e) {
                reports.add(Report.newError(Stage.SEMANTIC,
                        -1,
                        -1,
                        "Problem while executing analysis pass '" + analysisPass.getClass() + "'",
                        e)
                );
            }
        }

        return new JmmSemanticsResult(parserResult, table, reports);
    }
}
