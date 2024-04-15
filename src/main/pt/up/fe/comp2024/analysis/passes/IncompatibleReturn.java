package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.NodeUtils;

import java.util.List;

public class IncompatibleReturn extends AnalysisVisitor {

    @Override
    protected void buildVisitor() {
        addVisit("MethodDeclaration", this::visitMethodDecl);
    }

    public Void visitMethodDecl(JmmNode methodDecl, SymbolTable table) {

        JmmNode returnNode = methodDecl.getChild(methodDecl.getNumChildren() - 1);

        String methodName = methodDecl.get("name");
        Type returnType = table.getReturnType(methodName);

        String methodReturnType = returnType.getName();
        String returnNodeType = returnNode.get("type");

        if (returnNodeType.equals("invalid")) return null;

        Boolean isMethodReturnArray = returnType.isArray();

        if (returnNodeType.equals("undefined")) {
            returnNode.put("type", methodReturnType);
            returnNode.put("isArray", isMethodReturnArray ? "true" : "false");
            return null;
        }

        Boolean isReturnNodeArray = returnNode.get("isArray").equals("true");


        if (methodReturnType.equals(returnNodeType) && isMethodReturnArray.equals(isReturnNodeArray))
            return null;



        String message = String.format(
                "Return type \"%s\" does not coincide with \"%s\" method declaration's \"%s\".",
                returnNodeType + (isReturnNodeArray ? "[]" : ""),
                methodName,
                methodReturnType + (isMethodReturnArray ? "[]" : "")
        );

        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(returnNode),
                NodeUtils.getColumn(returnNode),
                message,
                null)
        );

        return null;
    }
}