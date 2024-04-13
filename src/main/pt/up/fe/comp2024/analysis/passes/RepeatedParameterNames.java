package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.symboltable.JmmSymbolTable;
import pt.up.fe.comp2024.symboltable.ParamSymbol;

import java.util.List;

public class RepeatedParameterNames extends AnalysisVisitor {

    @Override
    protected void buildVisitor() {
        addVisit("MethodDeclaration", this::visitMethodDecl);
    }

    public Void visitMethodDecl(JmmNode methodDecl, JmmSymbolTable table) {

        return null;
    }
}