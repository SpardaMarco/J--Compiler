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
        addVisit("UnaryOp", this::visitUnaryOp);
        addVisit("ExprStmt", this::visitExprStmt);
        addVisit("ParenExpr", this::visitExprStmt);
        addVisit("ArrayAccessOp", this::visitArrayAccessOp);
        addVisit("MethodCall", this::visitMethodCall);
        addVisit("FunctionCall", this::visitFunctionCall);
        addVisit("This", this::visitThis);
        addVisit("Attribute", this::visitAttribute);
        addVisit("ObjectDeclaration", this::visitObjectDeclaration);
        addVisit("ArrayDeclaration", this::visitArrayDeclaration);
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

    private void updateUndefinedOperand(JmmNode operand, String type) {
        if (operand.get("type") == "undefined") {
            operand.put("type", type);
        }
    }

    private void updateUndefinedBinaryOperands(JmmNode binaryOp, String type) {
        JmmNode leftOperand = binaryOp.getChild(0);
        JmmNode rightOperand = binaryOp.getChild(1);
        updateUndefinedOperand(leftOperand, type);
        updateUndefinedOperand(rightOperand, type);
    }

    private Void visitBinaryOp(JmmNode binaryOp, SymbolTable table) {

        switch (binaryOp.get("op")){
            case "+", "-", "*", "/" -> {
                updateUndefinedBinaryOperands(binaryOp, "int");
                binaryOp.put("type", "int");
                break;
            }
            case "<" -> {
                updateUndefinedBinaryOperands(binaryOp, "int");
                binaryOp.put("type", "boolean");
                break;
            }
            case "&&" -> {
                updateUndefinedBinaryOperands(binaryOp, "boolean");
                binaryOp.put("type", "boolean");
                break;
            }
        }

        binaryOp.put("isArray", "false");

        return null;
    }

    private Void visitUnaryOp(JmmNode unaryOp, SymbolTable table) {

        switch (unaryOp.get("op")) {
            case "!":
                updateUndefinedOperand(unaryOp.getChild(0), "boolean");
                unaryOp.put("type", "boolean");

        }
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

    private Void visitExprStmt(JmmNode exprStmt, SymbolTable table) {

        String type = exprStmt.getChild(0).get("type");

        if (type == "undefined") {
            exprStmt.getChild(0).put("type", "void");
            exprStmt.put("type", "void");
            return null;
        }
        exprStmt.put("type", type);

        if (type != "invalid") {
            String isArray = exprStmt.getChild(0).get("isArray");
            exprStmt.put("isArray", isArray);
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
        } else {

            methodCall.put("type", "undefined");
        }
        return null;
    }

    private Void visitFunctionCall(JmmNode functionCall, SymbolTable table){

        String functionName = functionCall.get("name");

        for (String method: table.getMethods()) {

            if (method.equals(functionName)) {

                Type type = table.getReturnType(method);

                functionCall.put("type", type.getName());
                functionCall.put("isArray", type.isArray() ? "true" : "false");

                return null;
            }
        }

        for (String importList: table.getImports()) {

            String[] words = importList.replaceAll("[\\[\\]]", "").split(", ");
            String importedFunction = words[words.length - 1];

            if (importedFunction.equals(functionName)){
                functionCall.put("type", "undefined");
                return null;
            }
        }

        functionCall.put("type", "invalid");

        return null;
    }

    private Void visitThis(JmmNode thisNode, SymbolTable table) {

        thisNode.put("type", table.getClassName());
        thisNode.put("isArray", "false");

        return null;
    }

    private Void visitAttribute(JmmNode attribute, SymbolTable table) {

        JmmNode object = attribute.getChild(0);

        String attributeName = attribute.get("name");

        if (object.get("type").equals(table.getClassName()) || object.isInstance("This")) {

            for (Symbol field: table.getFields()) {
                if (field.getName().equals(attributeName)) {

                    attribute.put("type", field.getType().getName());
                    attribute.put("isArray", field.getType().isArray() ? "true" : "false");
                    return null;
                }
            }
        }

        attribute.put("type", "undefined");

        return null;
    }

    public Void visitObjectDeclaration (JmmNode objectDeclaration, SymbolTable table) {

        objectDeclaration.put("type", objectDeclaration.get("name"));
        objectDeclaration.put("isArray", "false");

        return null;
    }

    public Void visitArrayDeclaration (JmmNode arrayDeclaration, SymbolTable table) {

        arrayDeclaration.put("type", arrayDeclaration.getChild(0).get("type"));
        arrayDeclaration.put("isArray", "true");

        return null;
    }
}
