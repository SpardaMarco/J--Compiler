package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.comp2024.symboltable.JmmSymbolTable;

import java.util.ArrayList;

import static pt.up.fe.comp2024.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends AJmmVisitor<Void, OllirExprResult> {
    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NOT = "!";
    private final String ARRAY_LENGTH = "arraylength";

    private final JmmSymbolTable table;

    public OllirExprGeneratorVisitor(SymbolTable table) {
        this.table = (JmmSymbolTable) table;
    }

    @Override
    protected void buildVisitor() {
        addVisit(OBJECT_DECLARATION, this::visitObjectDecl);
        addVisit(METHOD_CALL, this::visitMethodCall);
        addVisit(UNARY_OP, this::visitUnaryExpr);
        addVisit(BINARY_OP, this::visitBinExpr);
        addVisit(IDENTIFIER, this::visitVarRef);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit(BOOLEAN_LITERAL, this::visitBoolean);
        addVisit(ARRAY_ACCESS_OP, this::visitArrayAccess);
        addVisit(ARRAY_DECLARATION, this::visitArrayDecl);
        addVisit(ATTRIBUTE, this::visitAttribute);

        setDefaultVisit(this::defaultVisit);
    }

    private OllirExprResult visitObjectDecl(JmmNode objectDeclNode, Void unused) {
        var code = new StringBuilder();
        var computation = new StringBuilder();

        var name = objectDeclNode.get("name");

        var temp = OptUtils.getTemp();
        var type = "." + name;

        computation.append(temp)
                .append("." + name).append(SPACE).append(ASSIGN).append(type).append(SPACE)
                .append("new").append("(" + name + ")").append(type).append(END_STMT);

        computation.append("invokespecial(").append(temp).append(type).append(", \"<init>\")").append(".V").append(END_STMT);

        code.append(temp).append(type);

        return new OllirExprResult(code.toString(), computation.toString());
    }

    private OllirExprResult visitArrayAccess(JmmNode arrayAccessNode, Void unused) {
        var code = new StringBuilder();
        var computation = new StringBuilder();

        var firstChild = arrayAccessNode.getJmmChild(0);
        var firstChildValue = firstChild.get("value");

        var secondChild = arrayAccessNode.getJmmChild(1);
        var secondChildValue = secondChild.get("value");
        var secondChildType = TypeUtils.getExprType(secondChild, table);

        code.append(firstChildValue).append("[").append(secondChildValue).append(OptUtils.toOllirType(secondChildType)).append("]");
        var type = TypeUtils.getExprType(arrayAccessNode, table);
        code.append(OptUtils.toOllirType(type));

        return new OllirExprResult(code.toString(), computation.toString());
    }

    private OllirExprResult visitArrayDecl(JmmNode arrayDeclNode, Void unused) {
        var code = new StringBuilder();
        var computation = new StringBuilder();

        var thisType = arrayDeclNode.get("type");
        var type = OptUtils.toOllirType(new Type(thisType, true));

        var expr = visit(arrayDeclNode.getJmmChild(0));

        computation.append(expr.getComputation());

        code.append("new(array,").append(SPACE);
        code.append(expr.getCode()).append(")");

        code.append(type);

        return new OllirExprResult(code.toString(), computation.toString());
    }

    private OllirExprResult visitAttribute(JmmNode attributeNode, Void unused) {
        var code = new StringBuilder();

        var child = attributeNode.getChild(0);

        var id = child.get("value");
        var idType = OptUtils.toOllirType(TypeUtils.getExprType(child, table));

        code.append(ARRAY_LENGTH);
        code.append("(").append(id).append(idType).append(")");

        var thisType = attributeNode.get("type");
        var type = OptUtils.toOllirType(new Type(thisType, false));
        code.append(type);

        return new OllirExprResult(code.toString(), "");
    }

    private OllirExprResult visitMethodCall(JmmNode methodCallNode, Void unused) {
        var code = new StringBuilder();
        var computation = new StringBuilder();

        var exprName = "";

        if (methodCallNode.getJmmChild(0).getKind().equals(PAREN_EXPR.toString())) {
            var result = visit(methodCallNode.getJmmChild(0));
            computation.append(result.getComputation());
            exprName = result.getCode();
        } else {
            var exprHasValue = methodCallNode.getJmmChild(0).hasAttribute("value");

            var methodName = (methodCallNode.getAncestor(METHOD_DECLARATION).isPresent()) ?
                    methodCallNode.getAncestor(METHOD_DECLARATION).get().get("name") :
                    methodCallNode.getAncestor(MAIN_METHOD_DECLARATION).get().get("name");

            var params = table.getParameters(methodName);
            var locals = table.getLocalVariables(methodName);
            var fields = table.getFields();

            var isNotLocal = locals.stream().noneMatch(l -> l.getName().equals(methodCallNode.getJmmChild(0).get("value")));
            var isNotParam = params.stream().noneMatch(p -> p.getName().equals(methodCallNode.getJmmChild(0).get("value")));
            var isField = fields.stream().anyMatch(f -> f.getName().equals(methodCallNode.getJmmChild(0).get("value")));

            if (isNotLocal && isNotParam && isField) {
                var result = visit(methodCallNode.getJmmChild(0));

                var temp = OptUtils.getTemp();
                var tempType = OptUtils.toOllirType(TypeUtils.getExprType(methodCallNode.getJmmChild(0), table));

                computation.append(result.getComputation());
                computation.append(temp).append(tempType).append(SPACE)
                        .append(ASSIGN).append(tempType).append(SPACE);
                computation.append(result.getCode());

                if (!computation.toString().endsWith(END_STMT))
                    computation.append(END_STMT);

                exprName = temp;
            } else {
                exprName = (exprHasValue) ?
                        methodCallNode.getJmmChild(0).get("value") : methodCallNode.getJmmChild(0).get("name");
            }
        }

        var exprType = methodCallNode.getJmmChild(0).get("type");
        var name = methodCallNode.get("name");
        var type = TypeUtils.getExprType(methodCallNode, table);

        var importsList = table.getImportsList();
        var methodSymbol = table.getMethodSymbol(name);

        if (exprName.equals("this") || exprType.equals(table.getClassName())) {
            if (methodSymbol.isStatic()) {
                code.append("invokestatic(");
            } else code.append("invokevirtual(");
        } else if (exprName.equals(table.getClassName()) || importsList.contains(exprName)) {
            code.append("invokestatic(");
        } else {
            code.append("invokevirtual(");
        }

        code.append(exprName);

        if (!exprType.equals("invalid") && !exprType.equals("undefined") && !methodCallNode.getJmmChild(0).getKind().equals(PAREN_EXPR.toString())) {
            code.append("." + exprType);
        }

        code.append(", ");
        code.append("\"" + name + "\"");

        if (methodCallNode.getNumChildren() > 1) {
            var ancestor = methodCallNode.getAncestor(METHOD_DECLARATION).isPresent() ?
                    methodCallNode.getAncestor(METHOD_DECLARATION).get() :
                    methodCallNode.getAncestor(MAIN_METHOD_DECLARATION).get();

            var locals = table.getLocalVariables(ancestor.get("name"));
            var params = table.getParameters(ancestor.get("name"));
            int index = 1;
            boolean isVararg = false;
            for (index = 1; index < methodCallNode.getNumChildren(); index++) {
                code.append(",");
                code.append(SPACE);

                // get method in symbol table
                var method = table.getMethodSymbol(name);
                if (method != null) {
                    isVararg = method.getParams().get(index - 1).isVararg();
                    if (isVararg) {
                        break;
                    }
                }


                OllirExprResult result = visit(methodCallNode.getJmmChild(index));

                var isLiteral = isNodeType(INTEGER_LITERAL.toString(), methodCallNode.getJmmChild(index)) || isNodeType(BOOLEAN_LITERAL.toString(), methodCallNode.getJmmChild(index));
                String childValue;
                var child = methodCallNode.getJmmChild(index);

                while (true) {
                    if (child.getKind().equals(IDENTIFIER.toString())) {
                        childValue = child.get("value");
                        break;
                    } else if (child.getKind().equals(PAREN_EXPR.toString())) {
                        child = child.getJmmChild(0);
                    } else {
                        childValue = "";
                        break;
                    }
                }

                var isNotLocal = locals == null || locals.stream().noneMatch(l -> l.getName().equals(childValue));
                var isNotParam = params == null || params.stream().noneMatch(p -> p.getName().equals(childValue));
                var isBinExpr = isNodeType(BINARY_OP.toString(), methodCallNode.getJmmChild(index));
                var isObjectDecl = isNodeType(OBJECT_DECLARATION.toString(), methodCallNode.getJmmChild(index));

                if (!(isLiteral || !isNotLocal || !isNotParam || isObjectDecl || isBinExpr)) {
                    var temp = OptUtils.getTemp();
                    var tempType = OptUtils.toOllirType(TypeUtils.getExprType(methodCallNode.getJmmChild(index), table));

                    computation.append(result.getComputation());
                    computation.append(temp).append(tempType).append(SPACE)
                            .append(ASSIGN).append(tempType).append(SPACE);
                    computation.append(result.getCode());

                    if (!computation.toString().endsWith(END_STMT))
                        computation.append(END_STMT);

                    code.append(temp).append(tempType);
                } else {
                    computation.append(result.getComputation());
                    code.append(result.getCode());
                }
            }

            if (isVararg) {
                var thisType = methodCallNode.getJmmChild(index).get("type");
                var typeVarArg = OptUtils.toOllirType(new Type(thisType, true));
                var temp = OptUtils.getTemp();
                var numOfArgs = methodCallNode.getNumChildren() - index;
                var currArg = 0;

                computation.append(temp).append(typeVarArg).append(SPACE)
                        .append(ASSIGN).append(typeVarArg).append(SPACE);
                computation.append("new(array,").append(SPACE);
                computation.append(numOfArgs).append(".int32").append(")").append(typeVarArg).append(END_STMT);
                code.append(temp).append(typeVarArg);

                for (index = index; index < methodCallNode.getNumChildren(); index++) {
                    var result = visit(methodCallNode.getJmmChild(index));
                    var tempType = OptUtils.toOllirType(TypeUtils.getExprType(methodCallNode.getJmmChild(index), table));
                    computation.append(result.getComputation());
                    var newTemp = OptUtils.getTemp();

                    computation.append(newTemp).append(tempType).append(SPACE)
                            .append(ASSIGN).append(tempType).append(SPACE).append(result.getCode());
                    if (!computation.toString().endsWith(END_STMT))
                        computation.append(END_STMT);

                    computation.append(temp).append('[').append(currArg).append(".int32").append(']').append(tempType).append(SPACE)
                            .append(ASSIGN).append(typeVarArg).append(SPACE).append(newTemp).append(tempType);
                    if (!computation.toString().endsWith(END_STMT))
                        computation.append(END_STMT);

                    currArg++;
                }
            }
        }

        code.append(")");

        var ollirType = OptUtils.toOllirType(type);
        code.append(ollirType);
        code.append(END_STMT);

        return new OllirExprResult(code.toString(), computation.toString());
    }

    private OllirExprResult visitUnaryExpr(JmmNode unaryExprNode, Void unused) {
        String code = NOT + ".bool" + SPACE +
                visit(unaryExprNode.getJmmChild(0)).getCode();

        return new OllirExprResult(code);
    }

    private ArrayList<JmmNode> buildAndChildren(JmmNode binExprNode) {
        ArrayList<JmmNode> children = new ArrayList<>();
        for (var i = 0; i < binExprNode.getNumChildren(); i++) {
            var child = binExprNode.getJmmChild(i);
            if (child.getKind().equals(BINARY_OP.toString()) && child.get("op").equals("&&")) {
                children.addAll(buildAndChildren(child));
            } else {
                children.add(child);
            }
        }
        return children;
    }

    private OllirExprResult visitAndExpr(JmmNode binExprNode) {
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();
        ArrayList<JmmNode> children = buildAndChildren(binExprNode);

        String falseLabel = "falseLabel" + (OptUtils.getCurrentTempNum() + 1);
        String endLabel = "endLabel" + (OptUtils.getCurrentTempNum() + 1);
        String result = OptUtils.getTemp() + ".bool";
        for (var i = 0; i < children.size() - 1; i++) {
            var child = children.get(i);
            var childResult = visit(child);

            var temp = OptUtils.getTemp();
            var tempType = OptUtils.toOllirType(TypeUtils.getExprType(child, table));
            computation.append(temp).append(tempType).append(SPACE)
                    .append(ASSIGN).append(tempType).append(SPACE).append(NOT).append(".bool").append(SPACE).append(childResult.getCode());
            if (!computation.toString().endsWith(END_STMT))
                computation.append(END_STMT);
            computation.append("if (").append(temp).append(tempType).append(") goto ").append(falseLabel).append(END_STMT);
        }

        var lastChild = children.get(children.size() - 1);
        var lastChildResult = visit(lastChild);

        computation.append(result).append(SPACE).append(ASSIGN).append(".bool").append(SPACE).append(lastChildResult.getCode());
        if (!computation.toString().endsWith(END_STMT))
            computation.append(END_STMT);
        computation.append("goto ").append(endLabel).append(END_STMT);
        computation.append(falseLabel).append(":").append('\n');
        computation.append(result).append(SPACE).append(ASSIGN).append(".bool").append(SPACE).append("0.bool").append(END_STMT);
        computation.append(endLabel).append(":").append('\n');
        code.append(result);

        return new OllirExprResult(code.toString(), computation.toString());
    }

    private OllirExprResult visitBinExpr(JmmNode binExprNode, Void unused) {
        var op = binExprNode.get("op");

        if (op.equals("&&")) {
            return visitAndExpr(binExprNode);
        }

        var lhs = visit(binExprNode.getJmmChild(0));

        var lhsToAppend = "";
        var rhsToAppend = "";

        var lhsIsBinExpr = isNodeType(BINARY_OP.toString(), binExprNode.getJmmChild(0));
        var rhsIsBinExpr = isNodeType(BINARY_OP.toString(), binExprNode.getJmmChild(1));

        StringBuilder computation = new StringBuilder();

        var methodName = (binExprNode.getAncestor(METHOD_DECLARATION).isPresent()) ?
                binExprNode.getAncestor(METHOD_DECLARATION).get().get("name") :
                binExprNode.getAncestor(MAIN_METHOD_DECLARATION).get().get("name");

        var locals = table.getLocalVariables(methodName);
        var params = table.getParameters(methodName);
        var fields = table.getFields();

        String firstChildValue;
        var firstChild = binExprNode.getJmmChild(0);

        while (true) {
            if (firstChild.getKind().equals(IDENTIFIER.toString())) {
                firstChildValue = firstChild.get("value");
                break;
            } else if (firstChild.getKind().equals(PAREN_EXPR.toString())) {
                firstChild = firstChild.getJmmChild(0);
            } else {
                firstChildValue = "";
                break;
            }
        }

        String secondChildValue;
        var secondChild = binExprNode.getJmmChild(1);

        while (true) {
            if (secondChild.getKind().equals(IDENTIFIER.toString())) {
                secondChildValue = secondChild.get("value");
                break;
            } else if (secondChild.getKind().equals(PAREN_EXPR.toString())) {
                secondChild = secondChild.getJmmChild(0);
            } else {
                secondChildValue = "";
                break;
            }
        }

        var isNotLocal = locals.stream().noneMatch(l -> l.getName().equals(firstChildValue));
        var isNotParam = params.stream().noneMatch(p -> p.getName().equals(firstChildValue));
        var isIntLiteral = isNodeType(INTEGER_LITERAL.toString(), binExprNode.getJmmChild(0));
        var isBoolLiteral = isNodeType(BOOLEAN_LITERAL.toString(), binExprNode.getJmmChild(0));

        if (!(!isNotLocal || !isNotParam || isIntLiteral || isBoolLiteral)) {
            if (lhsIsBinExpr) {
                computation.append(lhs.getComputation());
                lhsToAppend = lhs.getCode();
            } else if (lhs.getCode().startsWith("invokevirtual")) {
                computation.append(lhs.getComputation());
                var temp = OptUtils.getTemp();
                var tempType = OptUtils.toOllirType(TypeUtils.getExprType(binExprNode.getJmmChild(0), table));
                computation.append(temp).append(tempType).append(SPACE)
                        .append(ASSIGN).append(tempType).append(SPACE);
                computation.append(lhs.getCode());
                lhsToAppend = temp + tempType;
            } else {
                var temp = OptUtils.getTemp();
                var tempType = OptUtils.toOllirType(TypeUtils.getExprType(binExprNode.getJmmChild(0), table));
                computation.append(temp).append(tempType).append(SPACE)
                        .append(ASSIGN).append(tempType).append(SPACE);
                computation.append(lhs.getCode());
                lhsToAppend = temp + tempType;
            }
            if (!computation.toString().endsWith(END_STMT))
                computation.append(END_STMT);
        } else {
            lhsToAppend = lhs.getCode();
        }

        var rhs = visit(binExprNode.getJmmChild(1));

        isNotLocal = locals.stream().noneMatch(l -> l.getName().equals(secondChildValue));
        isNotParam = params.stream().noneMatch(p -> p.getName().equals(secondChildValue));
        isIntLiteral = isNodeType(INTEGER_LITERAL.toString(), binExprNode.getJmmChild(1));
        isBoolLiteral = isNodeType(BOOLEAN_LITERAL.toString(), binExprNode.getJmmChild(1));

        if (!(!isNotLocal || !isNotParam || isIntLiteral || isBoolLiteral)) {
            if (rhsIsBinExpr) {
                computation.append(rhs.getComputation());
                rhsToAppend = rhs.getCode();
            } else if (rhs.getCode().startsWith("invokevirtual")) {
                computation.append(rhs.getComputation());
                var temp = OptUtils.getTemp();
                var tempType = OptUtils.toOllirType(TypeUtils.getExprType(binExprNode.getJmmChild(0), table));
                computation.append(temp).append(tempType).append(SPACE)
                        .append(ASSIGN).append(tempType).append(SPACE);
                computation.append(rhs.getCode());
                rhsToAppend = temp + tempType;
            } else {
                var temp = OptUtils.getTemp();
                var tempType = OptUtils.toOllirType(TypeUtils.getExprType(binExprNode.getJmmChild(1), table));
                computation.append(temp).append(tempType).append(SPACE)
                        .append(ASSIGN).append(tempType).append(SPACE);
                computation.append(rhs.getCode());
                rhsToAppend = temp + tempType;
            }
            if (!computation.toString().endsWith(END_STMT))
                computation.append(END_STMT);
        } else {
            rhsToAppend = rhs.getCode();
        }

        Type resType = TypeUtils.getExprType(binExprNode, table);
        String resOllirType = OptUtils.toOllirType(resType);
        String code = OptUtils.getTemp() + resOllirType;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE);

        isNotLocal = locals.stream().noneMatch(l -> l.getName().equals(firstChildValue));
        isNotParam = params.stream().noneMatch(p -> p.getName().equals(firstChildValue));

        var isLiteral = isNodeType(INTEGER_LITERAL.toString(), binExprNode.getJmmChild(0)) || isNodeType(BOOLEAN_LITERAL.toString(), binExprNode.getJmmChild(0));
        var hasValue = binExprNode.getJmmChild(0).hasAttribute("value");
        var isField = hasValue && fields.stream().anyMatch(f -> f.getName().equals(binExprNode.getJmmChild(0).get("value")));

        if (!binExprNode.getJmmChild(0).hasAttribute("value") || isLiteral || !isNotLocal || !isNotParam || isField) {
            computation.append(lhsToAppend).append(SPACE);
        }

        Type type = TypeUtils.getExprType(binExprNode, table);
        computation.append(binExprNode.get("op")).append(OptUtils.toOllirType(type)).append(SPACE)
                .append(rhsToAppend);

        if (!computation.toString().endsWith(END_STMT))
            computation.append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitVarRef(JmmNode varRefNode, Void unused) {
        var id = varRefNode.get("value");
        Type type = TypeUtils.getExprType(varRefNode, table);
        String ollirType = OptUtils.toOllirType(type);

        var methodName = (varRefNode.getAncestor(METHOD_DECLARATION).isPresent()) ?
                varRefNode.getAncestor(METHOD_DECLARATION).get().get("name") :
                varRefNode.getAncestor(MAIN_METHOD_DECLARATION).get().get("name");

        var locals = table.getLocalVariables(methodName);
        var params = table.getParameters(methodName);

        if (locals.stream().anyMatch(l -> l.getName().equals(id))) {
            String code = id + ollirType;
            return new OllirExprResult(code);
        }

        if (params.stream().anyMatch(p -> p.getName().equals(id))) {
            String code = id + ollirType;
            return new OllirExprResult(code);
        }

        var fields = table.getFields();
        if (fields.stream().anyMatch(f -> f.getName().equals(id))) {
            String code = "getfield(this, " + id + ollirType + ")" + ollirType;
            return new OllirExprResult(code);
        }

        String code = id + ollirType;

        return new OllirExprResult(code);
    }

    private OllirExprResult visitInteger(JmmNode integerNode, Void unused) {
        var intType = new Type(TypeUtils.getIntTypeName(), false);
        String ollirIntType = OptUtils.toOllirType(intType);
        String code = integerNode.get("value") + ollirIntType;

        return new OllirExprResult(code);
    }

    private OllirExprResult visitBoolean(JmmNode booleanNode, Void unused) {
        var booleanType = new Type(TypeUtils.getBooleanTypeName(), false);
        String ollirBooleanType = OptUtils.toOllirType(booleanType);
        String value = booleanNode.get("value").equals("true") ? "1" : "0";
        String code = value + ollirBooleanType;

        return new OllirExprResult(code);
    }

    private OllirExprResult defaultVisit(JmmNode node, Void unused) {
        var code = new StringBuilder();
        var computation = new StringBuilder();

        for (var child : node.getChildren()) {
            OllirExprResult result = visit(child);
            code.append(result.getCode());
            computation.append(result.getComputation());
        }

        return new OllirExprResult(code.toString(), computation.toString());
    }

    private boolean isNodeType(String nodeType, JmmNode node) {
        var value = true;

        while (value) {
            if (node.getKind().equals(nodeType)) {
                break;
            } else if (node.getKind().equals(PAREN_EXPR.toString())) {
                node = node.getJmmChild(0);
            } else {
                value = false;
                break;
            }
        }
        return value;
    }
}
