package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.symboltable.JmmSymbolTable;

public class InvalidFieldAccess extends AnalysisVisitor {

    @Override
    protected void buildVisitor() {

        addVisit("Attribute", this::visitAttribute);
    }

    private Void visitAttribute(JmmNode attribute, JmmSymbolTable table) {

        JmmNode object = attribute.getChild(0);

        if (!object.get("type").equals(table.getClassName()) || table.classExtends())
            return null;

        String attributeName = attribute.get("name");

        if (!table.getFields().stream().anyMatch(
                field -> field.getName().equals(attributeName)
        )) {

            String message = String.format(
                    "Attribute '%s' does not exist in class '%s'.",
                    attributeName,
                    table.getClassName()
            );

            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(attribute),
                    NodeUtils.getColumn(attribute),
                    message,
                    null)
            );
        }
        return null;
    }
}
