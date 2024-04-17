package pt.up.fe.comp2024.analysis;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PostorderJmmVisitor;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2024.symboltable.JmmSymbolTable;
import pt.up.fe.comp2024.symboltable.MethodSymbol;

import java.lang.reflect.Method;

public class ASTAnnotator extends PreorderJmmVisitor<JmmSymbolTable, Void> {

    String currentMethod;

    public ASTAnnotator(){
        setDefaultValue(() -> null);
    }
    @Override
    protected void buildVisitor() {

        addVisit("MethodDeclaration", this::visitMethodDecl);
        addVisit("MainMethodDeclaration", this::visitMethodDecl);
    }

    private Void visitMethodDecl(JmmNode method, JmmSymbolTable table) {
        currentMethod = method.get("name");

        new MethodVisitor(currentMethod).visit(method, table);

        return null;
    }

    class MethodVisitor extends PostorderJmmVisitor<JmmSymbolTable, Void> {

        String currentMethod;
        public MethodVisitor(String currentMethod) {
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
            addVisit("This", this::visitThis);
            addVisit("Attribute", this::visitAttribute);
            addVisit("ObjectDeclaration", this::visitObjectDeclaration);
            addVisit("ArrayDeclaration", this::visitArrayDeclaration);
            addVisit("AssignStmt", this::visitAssignStmt);
            addVisit("ArrayAssignStmt", this::visitArrayAssignStmt);
            addVisit("Return", this::visitReturn);
            addVisit("PrimitiveType", this::visitPrimitiveType);
            addVisit("ArrayType", this::visitArrayType);
            addVisit("VarDeclaration", this::visitVarDeclaration);
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

            if (arrayExpression.getNumChildren() == 0){
                arrayExpression.put("type", "empty_array");
                arrayExpression.put("isArray", "true");
                return null;
            }

            String type = arrayExpression.getChild(0).get("type");

            for (JmmNode element: arrayExpression.getChildren()){
                String elementType = element.get("type");
                if (!elementType.equals(type) && !elementType.equals("invalid")){
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
                operand.put("isArray", "false");
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

        private Void visitUnaryOp(JmmNode unaryOp, JmmSymbolTable table) {

            switch (unaryOp.get("op")) {
                case "!":
                    updateUndefinedOperand(unaryOp.getChild(0), "boolean");
                    unaryOp.put("type", "boolean");
                    unaryOp.put("isArray", "false");
            }
            return null;
        }
        private Void visitIdentifier(JmmNode identifier, JmmSymbolTable table) {

            String name = identifier.get("value");

            Symbol declaration = table.getVarDeclaration(name, currentMethod);

            if (declaration == null) {

                if (table.getClassName().equals(name)){
                    identifier.put("type", name);
                    return null;
                }
                if (table.getImportsList().contains(name)){
                    identifier.put("type", "undefined");
                    return null;
                }

                identifier.put("type", "invalid");

                return null;
            }

            String typeName = declaration.getType().getName();
            String isArray = declaration.getType().isArray() ? "true" : "false";

            identifier.put("reference", "variable");
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
            String type = array.get("type");

            if (type.equals("invalid")){
                arrayAccess.put("type", "invalid");
                return null;
            }
            else if (type.equals("undefined") || type.equals("empty_array")){
                arrayAccess.put("type", "undefined");
                return null;
            }
            else if (array.get("isArray") == "false") {
                arrayAccess.put("type", "invalid");
                return null;
            }

            arrayAccess.put("type", type);
            arrayAccess.put("isArray", "false");

            return null;
        }

        private Void visitMethodCall(JmmNode methodCall, JmmSymbolTable table){

            JmmNode object = methodCall.getChild(0);

            String methodName = methodCall.get("name");

            MethodSymbol methodSymbol = table.getMethodSymbol(methodName);

            if (object.isInstance("Identifier") && object.get("value").equals(table.getClassName())) {

                if (methodSymbol == null)
                    if (table.classExtends())
                        methodCall.put("type", "undefined");
                    else
                        methodCall.put("type", "invalid");
                else {
                    Type type = methodSymbol.getType();
                    methodCall.put("type", type.getName());
                    String isArray = type.isArray() ? "true" : "false";
                    methodCall.put("isArray", isArray);
                }
            }

            if (object.isInstance("This") || object.get("type").equals(table.getClassName())){

                if (methodSymbol != null){
                    Type type = methodSymbol.getType();
                    methodCall.put("type", type.getName());
                    String isArray = type.isArray() ? "true" : "false";
                    methodCall.put("isArray", isArray);
                    return null;
                }

                if (table.classExtends())
                    methodCall.put("type", "undefined");
                else
                    methodCall.put("type", "invalid");
                return null;
            }

            methodCall.put("type", "undefined");

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

            arrayDeclaration.put("type", "int");
            arrayDeclaration.put("isArray", "true");

            JmmNode length = arrayDeclaration.getChild(0);

            if (length.get("type").equals("undefined")){
                length.put("type", "int");
                length.put("isArray", "false");
            }

            return null;
        }

        public Void visitAssignStmt (JmmNode assignStmt, JmmSymbolTable table) {

            String variable = assignStmt.get("name");

            Symbol varDeclaration = table.getVarDeclaration(variable, currentMethod);

            if (varDeclaration == null) {
                if (table.classExtends() && !table.getMethodSymbol(currentMethod).isStatic())
                    assignStmt.put("type", "undefined");
                else
                    assignStmt.put("type", "invalid");
                return null;
            }

            String type = varDeclaration.getType().getName();
            Boolean isArray = varDeclaration.getType().isArray();
            assignStmt.put("type", type);
            assignStmt.put("isArray", isArray ? "true" : "false");

            JmmNode assignment = assignStmt.getChild(0);

            if (assignment.get("type").equals("undefined")) {
                assignment.put("type", type);
                assignment.put("isArray", isArray ? "true" : "false");
            }

            if (assignment.get("type").equals("empty_array") && isArray){
                assignment.put("type", type);
                assignment.put("isArray", "true");
            }

            return null;
        }

        public Void visitArrayAssignStmt (JmmNode arrayAssignStmt, JmmSymbolTable table) {

            String variable = arrayAssignStmt.get("name");

            Symbol varDeclaration = table.getVarDeclaration(variable, currentMethod);

            if (varDeclaration == null) {
                arrayAssignStmt.put("type", "invalid");
                return null;
            }

            String type = varDeclaration.getType().getName();
            Boolean isArray = varDeclaration.getType().isArray();
            arrayAssignStmt.put("type", type);
            arrayAssignStmt.put("isArray", isArray ? "true" : "false");

            JmmNode assignment = arrayAssignStmt.getChild(1);

            if (assignment.get("type").equals("undefined")) {
                assignment.put("type", type);
                assignment.put("isArray","false");
            }

            return null;
        }

        public Void visitReturn(JmmNode returnNode, JmmSymbolTable table) {
            JmmNode expression = returnNode.getChild(0);

            Type returnType = table.getReturnType(currentMethod);

            if (expression.get("type").equals("undefined")){
                expression.put("type", returnType.getName());
                expression.put("isArray", returnType.isArray() ? "true" : "false");
            }

            returnNode.put("type", expression.get("type"));

            if (!expression.get("type").equals("invalid"))
                returnNode.put("isArray", expression.get("isArray"));

            return null;
        }

        private Void visitPrimitiveType(JmmNode primitiveType, JmmSymbolTable symbolTable) {

            String type =  primitiveType.getChild(0).get("name");
            primitiveType.put("type", type);
            primitiveType.put("isArray", "false");

            return null;
        }
        private Void visitArrayType(JmmNode arrayType, JmmSymbolTable symbolTable) {

            String type =  arrayType.getChild(0).get("name");
            arrayType.put("type", type);
            arrayType.put("isArray", "true");

            return null;
        }
        private Void visitVarDeclaration(JmmNode varDeclaration, JmmSymbolTable symbolTable) {

            String type =  varDeclaration.getChild(0).get("type");
            String isArray = varDeclaration.getChild(0).get("isArray");
            varDeclaration.put("type", type);
            varDeclaration.put("isArray", isArray);
            return null;
        }
    }
}

