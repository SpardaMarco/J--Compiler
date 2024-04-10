package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

import static pt.up.fe.comp2024.ast.Kind.*;

import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates OLLIR code from JmmNodes that are not expressions.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {
    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String L_BRACKET = " {\n";
    private final String R_BRACKET = "}\n";

    private final SymbolTable table;

    private final OllirExprGeneratorVisitor exprVisitor;

    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        exprVisitor = new OllirExprGeneratorVisitor(table);
    }

    @Override
    protected void buildVisitor() {
        addVisit(PROGRAM, this::visitProgram);
        addVisit(IMPORT_DECLARATION, this::visitImport);
        addVisit(CLASS_DECLARATION, this::visitClass);
        addVisit(METHOD_DECLARATION, this::visitMethodDecl);
        addVisit(MAIN_METHOD_DECLARATION, this::visitMainMethodDecl);
        addVisit(PARAMS, this::visitParam);
        addVisit(RETURN, this::visitReturn);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);

        setDefaultVisit(this::defaultVisit);
    }

    private String visitMainMethodDecl(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder(".method");
        code.append(SPACE);
        code.append("public");
        code.append(SPACE);
        code.append("static");
        code.append(SPACE);
        code.append("main");

        List<Symbol> parameters = table.getParameters("main");
        var paramCode = parameters.stream()
                .map(symbol -> symbol.getName() + OptUtils.toOllirType(symbol.getType()))
                .collect(Collectors.joining(", "));

        code.append("(" + paramCode + ")");

        var retType = table.getReturnType("main");
        code.append(OptUtils.toOllirType(retType));

        code.append(L_BRACKET);

        for (var i = 1; i < node.getNumChildren(); i++) {
            if (!PARAMS.check(node.getJmmChild(i))) {
                code.append(visit(node.getJmmChild(i)));
            }
        }

        code.append("ret.V");
        code.append(SPACE);
        code.append(END_STMT);
        code.append(R_BRACKET);

        return code.toString();
    }

    private String visitAssignStmt(JmmNode node, Void unused) {
        var lhs = node.get("name");
        var rhs = exprVisitor.visit(node.getJmmChild(0));

        StringBuilder code = new StringBuilder(lhs);

        // code to compute the children

        // code to compute self
        // statement has type of lhs
        Type thisType = TypeUtils.getExprType(node, table);
        String typeString = OptUtils.toOllirType(thisType);

        code.append(typeString);
        code.append(SPACE);

        code.append(ASSIGN);
        code.append(typeString);
        code.append(SPACE);

        code.append(rhs.getCode());

        code.append(END_STMT);

        return code.toString();
    }

    private String visitReturn(JmmNode node, Void unused) {
        String methodName = node.getAncestor(METHOD_DECLARATION).get().get("name");
        Type retType = table.getReturnType(methodName);

        StringBuilder code = new StringBuilder("ret");
        code.append(OptUtils.toOllirType(retType));

        var expr = OllirExprResult.EMPTY;

        if (node.getNumChildren() > 0) {
            code = new StringBuilder();
            expr = exprVisitor.visit(node.getJmmChild(0));
        }

        code.append(expr.getComputation());
        code.append("ret");
        code.append(OptUtils.toOllirType(retType));
        code.append(SPACE);
        code.append(expr.getCode());
        code.append(END_STMT);
        return code.toString();
    }

    private String visitParam(JmmNode node, Void unused) {
        var typeCode = OptUtils.toOllirType(node.getJmmChild(0));
        var id = node.get("name");

        String code = id + typeCode;

        return code;
    }

    private String visitMethodDecl(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder(".method ");

        boolean isPublic = NodeUtils.getBooleanAttribute(node, "isPublic", "false");

        if (isPublic) {
            code.append("public ");
        }

        var name = node.get("name");
        code.append(name);

        List<Symbol> parameters = table.getParameters(name);
        var paramCode = parameters.stream()
                        .map(symbol -> symbol.getName() + OptUtils.toOllirType(symbol.getType()))
                        .collect(Collectors.joining(", "));

        code.append("(" + paramCode + ")");

        var retType = table.getReturnType(name);
        code.append(OptUtils.toOllirType(retType));
        code.append(L_BRACKET);

        for (var i = 1; i < node.getNumChildren(); i++) {
            if (EXPR_STMT.check(node.getJmmChild(i))) {
                OllirExprResult expr = exprVisitor.visit(node.getJmmChild(i));

                code.append(expr.getCode());
            }

            if (!PARAMS.check(node.getJmmChild(i))) {
                code.append(visit(node.getJmmChild(i)));
            }
        }

        code.append(NL);
        code.append(R_BRACKET);

        return code.toString();
    }

    private String visitClass(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        code.append(table.getClassName());

        String superClass = table.getSuper();

        if (superClass != null) {
            code.append(SPACE);
            code.append("extends ");
            code.append(superClass);
            code.append(SPACE);
        }

        code.append(L_BRACKET);
        code.append(NL);
        var needNl = true;

        for (var field : table.getFields()) {
            code.append(".field");
            code.append(SPACE);
            code.append("public");
            code.append(SPACE);
            code.append(field.getName());
            code.append(OptUtils.toOllirType(field.getType()));
            code.append(END_STMT);
        }

        for (var child : node.getChildren()) {
            var result = visit(child);

            if (METHOD_DECLARATION.check(child) && needNl) {
                code.append(NL);
                needNl = false;
            }

            code.append(result);
        }

        code.append(buildConstructor());
        code.append(R_BRACKET);

        return code.toString();
    }

    private String buildConstructor() {

        return ".construct " + table.getClassName() + "().V {\n" +
                "invokespecial(this, \"<init>\").V;\n" +
                "}\n";
    }

    private String visitProgram(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        node.getChildren().stream()
                .map(this::visit)
                .forEach(code::append);

        return code.toString();
    }

    private String visitImport(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder("import");
        List<String> imports = table.getImports();

        for (var importDecl : imports) {
            if (importDecl.contains(node.get("ID"))) {
                code.append(SPACE);
                code.append(node.get("ID"));
                code.append(END_STMT);
            }
        }

        return code.toString();
    }

    /**
     * Default visitor. Visits every child node and return an empty string.
     *
     * @param node
     * @param unused
     * @return
     */
    private String defaultVisit(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        for (var child : node.getChildren()) {
            code.append(visit(child));
        }

        return code.toString();
    }
}
