package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;

import java.util.ArrayList;
import java.util.List;

public class ArithmeticArrayOp extends AnalysisVisitor {

    @Override
    public List<Report> analyze(JmmNode root, SymbolTable table) {
        return new ArrayList<>();
    }

    @Override
    protected void buildVisitor() {

    }
}
