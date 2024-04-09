package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;

import java.util.List;

public class IncompatibleOperands extends AnalysisVisitor {

    @Override
    public List<Report> analyze(JmmNode root, SymbolTable table) {
        return null;
    }

    @Override
    protected void buildVisitor() {

    }
}