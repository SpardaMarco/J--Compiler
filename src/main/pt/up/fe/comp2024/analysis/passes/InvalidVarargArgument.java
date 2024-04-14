package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.List;

public class InvalidVarargArgument extends AnalysisVisitor {

    String currentMethod;
    @Override
    protected void buildVisitor() {
        addVisit("MethodDecl", this::visitMethodDecl);
        addVisit("Params", this::visitParam);
    }

    private Void visitMethodDecl(JmmNode methodDecl, SymbolTable table) {

        currentMethod = methodDecl.get("name");
        return null;
    }
    private Void visitParam(JmmNode param, SymbolTable table) {

        if (param.getChildren("Params").isEmpty())
            return null;

        if (param.get("isVarArg").equals("true")) {

            String message = String.format(
                    "Invalid use of vararg \"%s\" in \"%s()\". Vararg parameter must be last in method declaration.",
                    param.get("name"),
                    currentMethod
            );
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(param),
                    NodeUtils.getColumn(param),
                    message,
                    null)
            );
        }

        return null;
    }
}
