package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.symboltable.JmmSymbolTable;

/**
 * Checks if the type of the expression in a return statement is compatible with the method return type.
 *
 * @author JBispo
 */
public class NotImportedClass extends AnalysisVisitor {

    @Override
    public void buildVisitor() {

        addVisit("Identifier", this::visitIdentifier);
        addVisit("NamedType", this::visitNamedType);
    }

    private Void visitNamedType(JmmNode namedType, JmmSymbolTable table) {

        checkClass(namedType, table, "name");

        return null;
    }

    private Void visitIdentifier(JmmNode identifier, JmmSymbolTable table) {

        if (identifier.get("type").equals("undefined"))
            checkClass(identifier, table, "value");

        return null;
    }

    private void checkClass(JmmNode identifier, JmmSymbolTable table, String nameAttribute) {

        String className = identifier.get(nameAttribute);

        if (className.equals(table.getClassName())) return;

        if (table.getImportsList().contains(className))
            return;

        var message = String.format("Class '%s' not imported.", className);
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(identifier),
                NodeUtils.getColumn(identifier),
                message,
                null)
        );
        return;
    }


}
