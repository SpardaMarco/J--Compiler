package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.comp2024.symboltable.JmmSymbolTable;

import static pt.up.fe.comp2024.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends PreorderJmmVisitor<Void, OllirExprResult> {
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

        code.append(temp).append(type).append(END_STMT);

        return new OllirExprResult(code.toString(), computation.toString());
    }

    private OllirExprResult visitMethodCall(JmmNode methodCallNode, Void unused) {
        var code = new StringBuilder();
        var exprName = methodCallNode.getJmmChild(0).get("value");
        var exprType = methodCallNode.getJmmChild(0).get("type");
        var name = methodCallNode.get("name");

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
            code.append("invokespecial(");
        }

        code.append(exprName);

        if (!exprType.equals("invalid") && !exprType.equals("undefined") && !exprName.equals("this"))
            code.append("." + exprType);

        code.append(", ");

        code.append("\"" + name + "\"");

        if (methodCallNode.getNumChildren() > 1) {
            for (int i = 1; i < methodCallNode.getNumChildren(); i++) {
                code.append(",");
                code.append(SPACE);
                code.append(visit(methodCallNode.getJmmChild(i)).getCode());
            }
        }

        code.append(")");

        var type = TypeUtils.getExprType(methodCallNode, table);
        var ollirType = OptUtils.toOllirType(type);
        code.append(ollirType);
        code.append(END_STMT);

        return new OllirExprResult(code.toString());
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
                .append(ASSIGN).append(resOllirType).append(SPACE);


        if (BinExprNode.getJmmChild(0).getKind().equals(METHOD_CALL.toString()) || BinExprNode.getJmmChild(1).getKind().equals(METHOD_CALL.toString())) {
            computation.append(rhs.getCode());

            var newCode = new StringBuilder();
            Type type = TypeUtils.getExprType(BinExprNode, table);

            newCode.append("$");
            newCode.append(OptUtils.getNextTempNum());
            newCode.append(".");
            newCode.append(lhs.getCode());
            newCode.append(SPACE);
            newCode.append(BinExprNode.get("op"));
            newCode.append(OptUtils.toOllirType(type));
            newCode.append(SPACE);
            newCode.append(code);

            return new OllirExprResult(newCode.toString(), computation);
        }

        else {
            computation.append(lhs.getCode()).append(SPACE);
        }

        Type type = TypeUtils.getExprType(BinExprNode, table);
        computation.append(BinExprNode.get("op")).append(OptUtils.toOllirType(type)).append(SPACE)
                .append(rhs.getCode());

        if (!computation.toString().endsWith(END_STMT))
            computation.append(END_STMT);

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
        var computation = new StringBuilder();

        for (var child : node.getChildren()) {
            code.append(visit(child).getCode());
            computation.append(visit(child).getComputation());
        }

        return new OllirExprResult(code.toString(), computation.toString());
    }
}
