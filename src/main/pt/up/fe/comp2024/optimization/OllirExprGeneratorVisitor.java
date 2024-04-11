package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2024.ast.TypeUtils;

import static pt.up.fe.comp2024.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends PreorderJmmVisitor<Void, OllirExprResult> {
    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";

    private final SymbolTable table;

    public OllirExprGeneratorVisitor(SymbolTable table) {
        this.table = table;
    }

    @Override
    protected void buildVisitor() {
        addVisit(METHOD_CALL, this::visitMethodCall);
        addVisit(BINARY_OP, this::visitBinExpr);
        addVisit(IDENTIFIER, this::visitVarRef);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit(BOOLEAN_LITERAL, this::visitBoolean);

        setDefaultVisit(this::defaultVisit);
    }

    private OllirExprResult visitMethodCall(JmmNode methodCallNode, Void unused) {
        var code = new StringBuilder();
        var expr = visit(methodCallNode.getJmmChild(0));
        var exprName = expr.getCode().substring(0, expr.getCode().indexOf("."));
        var name = methodCallNode.get("name");

        if (exprName.equals("this")) {
            code.append("invokespecial(");
        }

        var methods = table.getMethods();

        if (methods.contains(exprName)) {
            code.append("invokevirtual(");
        }

        else {
            code.append("invokestatic(");
        }

        code.append(exprName);
        code.append(", ");
        code.append("\"" + name + "\"");
        code.append(")");

        var type = TypeUtils.getExprType(methodCallNode, table);
        var ollirType = OptUtils.toOllirType(type);
        code.append(ollirType);
        code.append(END_STMT);

        var codeStr = code.toString();
        return new OllirExprResult(codeStr);
    }

    private OllirExprResult visitBinExpr(JmmNode BinExprNode, Void unused) {
        var lhs = visit(BinExprNode.getJmmChild(0));
        var rhs = visit(BinExprNode.getJmmChild(1));

        StringBuilder computation = new StringBuilder();

        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        Type resType = TypeUtils.getExprType(BinExprNode, table);
        String resOllirType = OptUtils.toOllirType(resType);
        String code = OptUtils.getTemp() + resOllirType;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append(lhs.getCode()).append(SPACE);

        Type type = TypeUtils.getExprType(BinExprNode, table);
        computation.append(BinExprNode.get("op")).append(OptUtils.toOllirType(type)).append(SPACE)
                .append(rhs.getCode()).append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitVarRef(JmmNode varRefNode, Void unused) {
        var id = varRefNode.get("value");
        Type type = TypeUtils.getExprType(varRefNode, table);
        String ollirType = OptUtils.toOllirType(type);

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
        String code = booleanNode.get("value") + ollirBooleanType;
        return  new OllirExprResult(code);
    }

    private OllirExprResult defaultVisit(JmmNode node, Void unused) {
        var code = new StringBuilder();

        for (var child : node.getChildren()) {
            code.append(visit(child).getCode());
        }

        return new OllirExprResult(code.toString());
    }
}
