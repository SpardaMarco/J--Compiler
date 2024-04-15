package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.symboltable.JmmSymbolTable;
import pt.up.fe.comp2024.symboltable.ParamSymbol;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Duplicates extends AnalysisVisitor {

    @Override
    protected void buildVisitor() {
        addVisit("ImportDeclaration", this::visitImportDeclaration);
    }

    private Void visitImportDeclaration(JmmNode importDeclaration, JmmSymbolTable symbolTable) {

        String id = importDeclaration.get("ID");
        Set<String> uniqueIds = new HashSet<>();

        for (String importID : symbolTable.getImportsList()) {
            if (!uniqueIds.add(importID)) {

                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(importDeclaration),
                        NodeUtils.getColumn(importDeclaration),
                        String.format(
                                "Duplicated import of simple class %s",
                                id
                        ),
                        null)
                );

                return null;
            }
        }
        return null;
    }
}