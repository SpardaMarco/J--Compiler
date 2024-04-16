package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
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

    String currentMethod;

    Set<String> methods = new HashSet<>();
    Set<String> importIDs = new HashSet<>();
    Set<String> fields = new HashSet<>();
    Set<String> parameters = new HashSet<>();
    Set<String> locals = new HashSet<>();

    @Override
    protected void buildVisitor() {
        addVisit("MethodDeclaration", this::visitMethodDeclaration);
        addVisit("MainMethodDeclaration", this::visitMethodDeclaration);
        addVisit("ImportDeclaration", this::visitImportDeclaration);
        addVisit("VarDeclaration", this::visitVarDeclaration);
        addVisit("Params", this::visitParam);
    }

    private Void visitMethodDeclaration(JmmNode methodDeclaration, JmmSymbolTable symbolTable) {

        currentMethod = methodDeclaration.get("name");

        if (!methods.add(currentMethod)) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(methodDeclaration),
                    NodeUtils.getColumn(methodDeclaration),
                    String.format(
                            "Duplicated declaration of method '%s()'.",
                            currentMethod
                    ),
                    null)
            );
        }

        locals = new HashSet<>();
        parameters = new HashSet<>();
        return null;
    }

    private Void visitImportDeclaration(JmmNode importDeclaration, JmmSymbolTable symbolTable) {

        String id = importDeclaration.get("ID");

        if (!importIDs.add(id)) {

            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(importDeclaration),
                    NodeUtils.getColumn(importDeclaration),
                    String.format(
                            "Duplicated import of class '%s'.",
                            id
                    ),
                    null)
            );
        }
        return null;
    }

    private Void visitVarDeclaration(JmmNode varDeclaration, JmmSymbolTable symbolTable) {

        String varName = varDeclaration.get("name");

        if (currentMethod != null) {
            if (!locals.add(varName)) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(varDeclaration),
                        NodeUtils.getColumn(varDeclaration),
                        String.format(
                                "Duplicated declaration of local variable '%s' in method '%s()'.",
                                varName, currentMethod
                        ),
                        null)
                );
            }
        } else {
            if (!fields.add(varName))
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(varDeclaration),
                        NodeUtils.getColumn(varDeclaration),
                        String.format(
                                "Duplicated declaration of field '%s'.",
                                varName
                        ),
                        null)
                );
        }
        return null;
    }

    private Void visitParam(JmmNode param, JmmSymbolTable symbolTable) {

        String paramName = param.get("name");

        if (!parameters.add(paramName)) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(param),
                    NodeUtils.getColumn(param),
                    String.format(
                            "Duplicated declaration of parameter '%s' in method '%s()'.",
                            paramName, currentMethod
                    ),
                    null)
            );
        }
        return null;
    }
}