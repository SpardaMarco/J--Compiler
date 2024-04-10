package pt.up.fe.comp2024.analysis;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PostorderJmmVisitor;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;

public class ASTAnnotator extends PreorderJmmVisitor<SymbolTable, Void> {

    String currentMethod;

    public ASTAnnotator(){
        setDefaultValue(() -> null);
    }
    @Override
    protected void buildVisitor() {

        addVisit("MethodDeclaration", this::visitMethodDecl);
        addVisit("MainMethodDeclaration", this::visitMethodDecl);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");

        new TypeAnnotator(currentMethod).visit(method, table);

        return null;
    }
}


class TypeAnnotator extends PostorderJmmVisitor<SymbolTable, Void> {

    String currentMethod;
    public TypeAnnotator(String currentMethod) {
        this.currentMethod = currentMethod;
        setDefaultValue(() -> null);
    }
    @Override
    protected void buildVisitor() {

        addVisit("IntegerLiteral", this::visitIntegerLiteral);
        addVisit("BooleanLiteral", this::visitBooleanLiteral);
        addVisit("ArrayExpression", this::visitArrayExpression);
        addVisit("Identifier", this::visitIdentifier);
        addVisit("BinaryOp", this::visitBinaryOp);
        addVisit("ExprStmt", this::visitExpr);
        addVisit("ParenExpr", this::visitExpr);
        addVisit("ArrayAccessOp", this::visitArrayAccessOp);
        addVisit("MethodCall", this::visitMethodCall);
    }

    protected Symbol getVarDeclaration(String varName, String currentMethod, SymbolTable table) {

        for (Symbol varDecl : table.getLocalVariables(currentMethod)) {
            if (varDecl.getName().equals(varName)) {
                return varDecl;
            }
        }
        for (Symbol varDecl : table.getParameters(currentMethod)) {
            if (varDecl.getName().equals(varName)) {
                return varDecl;
            }
        }
        for (Symbol varDecl : table.getFields()) {
            if (varDecl.getName().equals(varName)) {
                return varDecl;
            }
        }
        return null;
    }

    private Void visitIntegerLiteral(JmmNode integerLiteral, SymbolTable table) {

        integerLiteral.put("type", "int");
        integerLiteral.put("isArray", "false");
        return null;
    }

    private Void visitBooleanLiteral(JmmNode booleanLiteral, SymbolTable table) {

        booleanLiteral.put("type", "boolean");
        booleanLiteral.put("isArray", "false");
        return null;
    }

    private Void visitArrayExpression(JmmNode arrayExpression, SymbolTable table) {

        if (arrayExpression.getChildren().size() == 0){
            arrayExpression.put("type", "?");
            arrayExpression.put("isArray", "true");
        }

        String type = arrayExpression.getChild(0).get("type");

        for (JmmNode element: arrayExpression.getChildren()){
            if (!element.get("type").equals(type)){
                arrayExpression.put("type", "invalid");
                return null;
            }
        }

        arrayExpression.put("type", type);
        arrayExpression.put("isArray", "true");

        return null;
    }

    private Void visitBinaryOp(JmmNode binaryOp, SymbolTable table) {

        switch (binaryOp.get("op")){
            case "+":
                binaryOp.put("type", "int");
                break;
            case "-":
                binaryOp.put("type", "int");
                break;
            case "*":
                binaryOp.put("type", "int");
                break;
            case "/":
                binaryOp.put("type", "int");
                break;
            case "&&":
                binaryOp.put("type", "boolean");
                break;
            case "<":
                binaryOp.put("type", "boolean");
                break;
        }

        binaryOp.put("isArray", "false");

        return null;
    }
    private Void visitIdentifier(JmmNode identifier, SymbolTable table) {

        String varName = identifier.get("value");

        Symbol declaration = getVarDeclaration(varName, currentMethod, table);

        String typeName = declaration.getType().getName();
        String isArray = declaration.getType().isArray() ? "true" : "false";

        identifier.put("type", typeName);
        identifier.put("isArray", isArray);

        return null;
    }

    private Void visitExpr(JmmNode expr, SymbolTable table) {

        String type = expr.getChild(0).get("type");
        expr.put("type", type);
        if (type != "invalid") {
            String isArray = expr.getChild(0).get("isArray");
            expr.put("isArray", isArray);
        }
        return null;
    }

    private Void visitArrayAccessOp(JmmNode arrayAccess, SymbolTable table) {

        JmmNode array = arrayAccess.getChild(0);

        if (!array.isInstance("ArrayExpression") && !array.isInstance("Identifier")){
            arrayAccess.put("type", "invalid");
        }


        if (array.isInstance("Identifier")) {
            if (array.get("isArray") == "false") {
                arrayAccess.put("type", "invalid");
                return null;
            }
        }

        String type = array.get("type");
        arrayAccess.put("type", type);
        arrayAccess.put("isArray", "false");

        return null;
    }

    private Void visitMethodCall(JmmNode methodCall, SymbolTable table){

        JmmNode object = methodCall.getChild(0);

        if (object.isInstance("This")){

            String methodName = methodCall.get("name");

            for (String method: table.getMethods()){
                if (method.equals(methodName)){
                    Type type = table.getReturnType(method);
                    methodCall.put("type", type.getName());
                    String isArray = type.isArray() ? "true" : "false";
                    methodCall.put("isArray", isArray);
                }
            }
        }
        return null;
    }
}
