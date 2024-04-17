package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.comp2024.symboltable.JmmSymbolTable;

import java.util.ArrayList;
import java.util.List;

import static pt.up.fe.comp2024.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends AJmmVisitor<Void, OllirExprResult> {
    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";

    private final JmmSymbolTable table;

    public OllirExprGeneratorVisitor(SymbolTable table) {
        this.table = (JmmSymbolTable) table;
    }

    @Override
    protected void buildVisitor() {
        addVisit(OBJECT_DECLARATION, this::visitObjectDecl);
        addVisit(METHOD_CALL, this::visitMethodCall);
        addVisit(FUNCTION_CALL, this::visitFunctionCall);
        addVisit(BINARY_OP, this::visitBinExpr);
        addVisit(IDENTIFIER, this::visitVarRef);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit(BOOLEAN_LITERAL, this::visitBoolean);

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

    private OllirExprResult visitMethodCall(JmmNode methodCallNode, Void unused) {
        var code = new StringBuilder();
        var computation = new StringBuilder();
        var exprName = "";
        if (methodCallNode.getJmmChild(0).getKind().equals(PAREN_EXPR.toString())) {
            var result = visit(methodCallNode.getJmmChild(0));
//            code.append(result.getCode());
            computation.append(result.getComputation());
            exprName = result.getCode();
        }
        else{
            var exprHasValue = methodCallNode.getJmmChild(0).hasAttribute("value");
            exprName = (exprHasValue) ?
                    methodCallNode.getJmmChild(0).get("value") : methodCallNode.getJmmChild(0).get("name");
        }

        var exprType = methodCallNode.getJmmChild(0).get("type");
        var name = methodCallNode.get("name");
        var type = TypeUtils.getExprType(methodCallNode, table);

        var importsList = table.getImportsList();
        var methodSymbol = table.getMethodSymbol(name);

        if (exprName.equals("this") || exprType.equals(table.getClassName())) {
            if (methodSymbol.isStatic()) {
                code.append("invokestatic(");
            }
            else code.append("invokevirtual(");
        }

        else if (exprName.equals(table.getClassName()) || importsList.contains(exprName)) {
            code.append("invokestatic(");
        }

        else {
            code.append("invokevirtual(");
        }

        code.append(exprName);

        if (!exprType.equals("invalid") && !exprType.equals("undefined") && !exprName.equals("this") && !methodCallNode.getJmmChild(0).getKind().equals(PAREN_EXPR.toString()))
            code.append("." + exprType);

        code.append(", ");

        code.append("\"" + name + "\"");

        if (methodCallNode.getNumChildren() > 1) {
            var ancestor = methodCallNode.getAncestor(METHOD_DECLARATION).isPresent() ?
                    methodCallNode.getAncestor(METHOD_DECLARATION).get() :
                    methodCallNode.getAncestor(MAIN_METHOD_DECLARATION).get();
            var locals = table.getLocalVariables(ancestor.get("name"));
            var params = table.getParameters(ancestor.get("name"));
            for (int i = 1; i < methodCallNode.getNumChildren(); i++) {
                code.append(",");
                code.append(SPACE);
                OllirExprResult result = visit(methodCallNode.getJmmChild(i));
                var isLiteral = isNodeType(INTEGER_LITERAL.toString(), methodCallNode.getJmmChild(i)) || isNodeType(BOOLEAN_LITERAL.toString(), methodCallNode.getJmmChild(i));
                String childValue;
                var child = methodCallNode.getJmmChild(i);
                while (true) {
                    if (child.getKind().equals(IDENTIFIER.toString())) {
                        childValue = child.get("value");
                        break;
                    }
                    else if (child.getKind().equals(PAREN_EXPR.toString())) {
                        child = child.getJmmChild(0);
                    }
                    else {
                        childValue = "";
                        break;
                    }
                }

                var isNotLocal = locals == null || locals.stream().noneMatch(l -> l.getName().equals(childValue));
                var isNotParam = params == null || params.stream().noneMatch(p -> p.getName().equals(childValue));
                var isObjectDecl = isNodeType(OBJECT_DECLARATION.toString(), methodCallNode.getJmmChild(i));
                if (!(isLiteral || !isNotLocal || !isNotParam || isObjectDecl)) {
                    var temp = OptUtils.getTemp();
                    var tempType = OptUtils.toOllirType(TypeUtils.getExprType(methodCallNode.getJmmChild(i), table));
                    computation.append(result.getComputation());
                    computation.append(temp).append(tempType).append(SPACE)
                            .append(ASSIGN).append(tempType).append(SPACE);
                    computation.append(result.getCode());
                    if (!computation.toString().endsWith(END_STMT))
                        computation.append(END_STMT);

                    code.append(temp).append(tempType);
                }
                else {
                    computation.append(result.getComputation());
                    code.append(result.getCode());
                }
            }
        }

        code.append(")");

        var ollirType = OptUtils.toOllirType(type);
        code.append(ollirType);
        code.append(END_STMT);

        return new OllirExprResult(code.toString(), computation.toString());


    }

    private OllirExprResult visitFunctionCall(JmmNode functionCallNode, Void unused) {
        var code = new StringBuilder();

        var name = functionCallNode.get("name");
        var type = functionCallNode.get("type");

        var methodSymbol = table.getMethodSymbol(name);

        if (methodSymbol.isStatic()) {
            code.append("invokestatic(");
        }
        else code.append("invokevirtual(");

        code.append(name);

        if (!type.equals("invalid") && !type.equals("undefined")) {
            code.append("." + type);
        }

        if (functionCallNode.getNumChildren() > 1) {
            for (int i = 1; i < functionCallNode.getNumChildren(); i++) {
                code.append(",");
                code.append(SPACE);
                code.append(visit(functionCallNode.getJmmChild(i)).getCode());
            }
        }

        code.append(")");
        code.append("." + type);
        code.append(END_STMT);

        return new OllirExprResult(code.toString());
    }

    private boolean isNodeType (String nodeType, JmmNode node) {
        var value = true;
        while (value) {
            if (node.getKind().equals(nodeType)) {
                break;
            }
            else if (node.getKind().equals(PAREN_EXPR.toString())) {
                node = node.getJmmChild(0);
            }
            else {
                value = false;
                break;
            }
        }
        return value;
    }

    private OllirExprResult visitBinExpr(JmmNode BinExprNode, Void unused) {
        var lhs = visit(BinExprNode.getJmmChild(0));
        var lhsToAppend = "";
        var rhsToAppend = "";
        var lhsIsBinExpr = isNodeType(BINARY_OP.toString(), BinExprNode.getJmmChild(0));
        var rhsIsBinExpr = isNodeType(BINARY_OP.toString(), BinExprNode.getJmmChild(1));

        StringBuilder computation = new StringBuilder();

        var methodName = (BinExprNode.getAncestor(METHOD_DECLARATION).isPresent()) ?
                BinExprNode.getAncestor(METHOD_DECLARATION).get().get("name") :
                BinExprNode.getAncestor(MAIN_METHOD_DECLARATION).get().get("name");

        var locals = table.getLocalVariables(methodName);
        var params = table.getParameters(methodName);
        var fields = table.getFields();

        String firstChildValue;
        var firstChild = BinExprNode.getJmmChild(0);
        while (true) {
            if (firstChild.getKind().equals(IDENTIFIER.toString())) {
                firstChildValue = firstChild.get("value");
                break;
            }
            else if (firstChild.getKind().equals(PAREN_EXPR.toString())) {
                firstChild = firstChild.getJmmChild(0);
            }
            else {
                firstChildValue = "";
                break;
            }
        }

        String secondChildValue;
        var secondChild = BinExprNode.getJmmChild(1);
        while (true) {
            if (secondChild.getKind().equals(IDENTIFIER.toString())) {
                secondChildValue = secondChild.get("value");
                break;
            }
            else if (secondChild.getKind().equals(PAREN_EXPR.toString())) {
                secondChild = secondChild.getJmmChild(0);
            }
            else {
                secondChildValue = "";
                break;
            }
        }


        var isNotLocal = locals.stream().noneMatch(l -> l.getName().equals(firstChildValue));
        var isNotParam = params.stream().noneMatch(p -> p.getName().equals(firstChildValue));
        var isIntLiteral = isNodeType(INTEGER_LITERAL.toString(), BinExprNode.getJmmChild(0));
        var isBoolLiteral = isNodeType(BOOLEAN_LITERAL.toString(), BinExprNode.getJmmChild(0));

        if (!(!isNotLocal || !isNotParam || isIntLiteral || isBoolLiteral)) {
            if (lhsIsBinExpr) {
                computation.append(lhs.getComputation());
                lhsToAppend = lhs.getCode();
            }
            else {
                var temp = OptUtils.getTemp();
                var tempType = OptUtils.toOllirType(TypeUtils.getExprType(BinExprNode.getJmmChild(0), table));
                computation.append(temp).append(tempType).append(SPACE)
                        .append(ASSIGN).append(tempType).append(SPACE);
                computation.append(lhs.getCode());
                lhsToAppend = temp + tempType;
            }
            if (!computation.toString().endsWith(END_STMT))
                computation.append(END_STMT);
        }
        else {
            lhsToAppend = lhs.getCode();
        }
        var rhs = visit(BinExprNode.getJmmChild(1));

        isNotLocal = locals.stream().noneMatch(l -> l.getName().equals(secondChildValue));
        isNotParam = params.stream().noneMatch(p -> p.getName().equals(secondChildValue));
        isIntLiteral = isNodeType(INTEGER_LITERAL.toString(), BinExprNode.getJmmChild(1));
        isBoolLiteral = isNodeType(BOOLEAN_LITERAL.toString(), BinExprNode.getJmmChild(1));

        if (!(!isNotLocal || !isNotParam || isIntLiteral || isBoolLiteral)) {
            if (rhsIsBinExpr) {
                computation.append(rhs.getComputation());
                rhsToAppend = rhs.getCode();
            }
            else {
                var temp = OptUtils.getTemp();
                var tempType = OptUtils.toOllirType(TypeUtils.getExprType(BinExprNode.getJmmChild(1), table));
                computation.append(temp).append(tempType).append(SPACE)
                        .append(ASSIGN).append(tempType).append(SPACE);
                computation.append(rhs.getCode());
                rhsToAppend = temp + tempType;
            }
            if (!computation.toString().endsWith(END_STMT))
                computation.append(END_STMT);
        }
        else {
            rhsToAppend = rhs.getCode();
        }

        Type resType = TypeUtils.getExprType(BinExprNode, table);
        String resOllirType = OptUtils.toOllirType(resType);
        String code = OptUtils.getTemp() + resOllirType;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE);



//        isNotLocal = locals.stream().noneMatch(l -> l.getName().equals(firstChildValue));
//        isNotParam = params.stream().noneMatch(p -> p.getName().equals(firstChildValue));
//        if (isNotLocal && isNotParam && BinExprNode.getJmmChild(0).hasAttribute("value") && fields.stream().anyMatch(f -> f.getName().equals(BinExprNode.getJmmChild(0).get("value")))) {
//            var temp = OptUtils.getTemp();
//
//            computation.append("getfield(this, ").append(BinExprNode.getJmmChild(0).get("value")).append(OptUtils.toOllirType(TypeUtils.getExprType(BinExprNode.getJmmChild(0), table))).append(")").append(OptUtils.toOllirType(TypeUtils.getExprType(BinExprNode.getJmmChild(0), table))).append(END_STMT);
//            computation.append(temp + resOllirType);
//            computation.append(SPACE).append(ASSIGN).append(resOllirType).append(SPACE);
//            computation.append(code).append(SPACE);
//
//            code = temp + resOllirType;
//        }
//
//        isNotLocal = locals.stream().noneMatch(l -> l.getName().equals(secondChildValue));
//        isNotParam = params.stream().noneMatch(p -> p.getName().equals(secondChildValue));
//
//        if (isNotLocal && isNotParam && BinExprNode.getJmmChild(1).hasAttribute("value") && fields.stream().anyMatch(f -> f.getName().equals(BinExprNode.getJmmChild(1).get("value")))) {
//            var temp = OptUtils.getTemp();
//
//            computation.append("getfield(this, ").append(BinExprNode.getJmmChild(1).get("value")).append(OptUtils.toOllirType(TypeUtils.getExprType(BinExprNode.getJmmChild(1), table))).append(")").append(OptUtils.toOllirType(TypeUtils.getExprType(BinExprNode.getJmmChild(1), table))).append(END_STMT);
//            computation.append(temp + resOllirType);
//            computation.append(SPACE).append(ASSIGN).append(resOllirType).append(SPACE);
//            computation.append(lhs.getCode()).append(SPACE);
//
//            Type type = TypeUtils.getExprType(BinExprNode, table);
//            computation.append(BinExprNode.get("op")).append(OptUtils.toOllirType(type)).append(SPACE)
//                    .append(code);
//
//            code = temp + resOllirType;
//
//            if (!computation.toString().endsWith(END_STMT))
//                computation.append(END_STMT);
//
//            return new OllirExprResult(code, computation);
//        }

//        if (BinExprNode.getJmmChild(0).getKind().equals(METHOD_CALL.toString()) || BinExprNode.getJmmChild(1).getKind().equals(METHOD_CALL.toString())) {
//            computation.append(rhsToAppend);
//
//            var newCode = new StringBuilder();
//            Type type = TypeUtils.getExprType(BinExprNode, table);
//
//            newCode.append(lhsToAppend);
//            newCode.append(SPACE);
//            newCode.append(BinExprNode.get("op"));
//            newCode.append(OptUtils.toOllirType(type));
//            newCode.append(SPACE);
//            newCode.append(code);
//
//            return new OllirExprResult(newCode.toString(), computation);
//        }

        isNotLocal = locals.stream().noneMatch(l -> l.getName().equals(firstChildValue));
        isNotParam = params.stream().noneMatch(p -> p.getName().equals(firstChildValue));
        var isLiteral = isNodeType(INTEGER_LITERAL.toString(), BinExprNode.getJmmChild(0)) || isNodeType(BOOLEAN_LITERAL.toString(), BinExprNode.getJmmChild(0));
        var hasValue = BinExprNode.getJmmChild(0).hasAttribute("value");
        var isField = hasValue && fields.stream().anyMatch(f -> f.getName().equals(BinExprNode.getJmmChild(0).get("value")));
        if (!BinExprNode.getJmmChild(0).hasAttribute("value") || isLiteral || !isNotLocal || !isNotParam || isField) {
            computation.append(lhsToAppend).append(SPACE);
        }

        Type type = TypeUtils.getExprType(BinExprNode, table);
        computation.append(BinExprNode.get("op")).append(OptUtils.toOllirType(type)).append(SPACE)
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

        return  new OllirExprResult(code);
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
}
