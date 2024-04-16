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
    private final String PUBLIC = "public";
    private final String METHOD = ".method";
    private final String STATIC = "static";
    private final String RETURN_STMT = "ret";

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
        addVisit(RETURN, this::visitReturn);
        addVisit(PARAMS, this::visitParam);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);

        setDefaultVisit(this::defaultVisit);
    }

    private String visitProgram(JmmNode programNode, Void unused) {
        StringBuilder code = new StringBuilder();

        programNode.getChildren().stream()
                .map(this::visit)
                .forEach(code::append);

        return code.toString();
    }

    private String visitImport(JmmNode importNode, Void unused) {
        StringBuilder code = new StringBuilder("import ");
        String importDecl = importNode.get("name");

        String importName =  importDecl.replace("[", "").replace("]", "");
        List<String> importParts = List.of(importName.split(", "));

        for (int i = 0; i < importParts.size(); i++) {
            code.append(importParts.get(i));
            if (i < importParts.size() - 1) {
                code.append(".");
            }
        }

        code.append(END_STMT);

        return code.toString();
    }

    private String visitClass(JmmNode classNode, Void unused) {
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
        var needNl = true;

        for (var field : table.getFields()) {
            code.append(".field");
            code.append(SPACE);
            code.append(PUBLIC);
            code.append(SPACE);
            code.append(field.getName());
            code.append(OptUtils.toOllirType(field.getType()));
            code.append(END_STMT);
        }

        code.append(buildConstructor());

        for (var child : classNode.getChildren()) {
            var result = visit(child);

            if (METHOD_DECLARATION.check(child) && needNl) {
                code.append(NL);
                needNl = false;
            }

            code.append(result);
        }

        code.append(R_BRACKET);

        return code.toString();
    }

    private String visitMethodDecl(JmmNode methodDeclNode, Void unused) {
        StringBuilder code = new StringBuilder(METHOD);
        code.append(SPACE);

        boolean isPublic = NodeUtils.getBooleanAttribute(methodDeclNode, "isPublic", "false");

        if (isPublic) {
            code.append(PUBLIC);
            code.append(SPACE);
        }

        var name = methodDeclNode.get("name");
        code.append(name);

        List<Symbol> parameters = table.getParameters(name);
        var paramCode = parameters.stream()
                .map(symbol -> symbol.getName() + OptUtils.toOllirType(symbol.getType()))
                .collect(Collectors.joining(", "));

        code.append("(" + paramCode + ")");

        var retType = table.getReturnType(name);
        code.append(OptUtils.toOllirType(retType));
        code.append(L_BRACKET);

        for (var i = 0; i < methodDeclNode.getNumChildren(); i++) {
            if (EXPR_STMT.check(methodDeclNode.getJmmChild(i))) {
                OllirExprResult expr = exprVisitor.visit(methodDeclNode.getJmmChild(i));
                code.append(expr.getCode());
            }

            if (!PARAMS.check(methodDeclNode.getJmmChild(i))) {
                code.append(visit(methodDeclNode.getJmmChild(i)));
            }
        }

        code.append(R_BRACKET);
        code.append(NL);

        return code.toString();
    }

    private String visitMainMethodDecl(JmmNode mainMethodDeclNode, Void unused) {
        StringBuilder code = new StringBuilder(METHOD);
        code.append(SPACE);
        code.append(PUBLIC);
        code.append(SPACE);
        code.append(STATIC);
        code.append(SPACE);
        code.append("main");

        var paramCode = "args.array.String";

        code.append("(" + paramCode + ")");

        var retType = table.getReturnType("main");
        code.append(OptUtils.toOllirType(retType));

        code.append(L_BRACKET);

        for (var i = 0; i < mainMethodDeclNode.getNumChildren(); i++) {
            if (EXPR_STMT.check(mainMethodDeclNode.getJmmChild(i))) {
                OllirExprResult expr = exprVisitor.visit(mainMethodDeclNode.getJmmChild(i));
                code.append(expr.getCode());
            }

            if (!PARAMS.check(mainMethodDeclNode.getJmmChild(i))) {
                code.append(visit(mainMethodDeclNode.getJmmChild(i)));
            }
        }

        code.append(RETURN_STMT);
        code.append(".V");
        code.append(SPACE);
        code.append(END_STMT);
        code.append(R_BRACKET);
        code.append(NL);

        return code.toString();
    }

    private String visitReturn(JmmNode returnNode, Void unused) {
        String methodName = returnNode.getAncestor(METHOD_DECLARATION).get().get("name");
        Type retType = table.getReturnType(methodName);

        StringBuilder code = new StringBuilder(RETURN_STMT);
        code.append(OptUtils.toOllirType(retType));

        var expr = OllirExprResult.EMPTY;

        if (returnNode.getNumChildren() > 0) {
            code = new StringBuilder();
            expr = exprVisitor.visit(returnNode.getJmmChild(0));
        }

        code.append(expr.getComputation());
        code.append(RETURN_STMT);
        code.append(OptUtils.toOllirType(retType));
        code.append(SPACE);
        code.append(expr.getCode());
        code.append(END_STMT);
        return code.toString();
    }

    private String visitParam(JmmNode paramNode, Void unused) {
        var typeCode = OptUtils.toOllirType(paramNode.getJmmChild(0));
        var id = paramNode.get("name");

        String code = id + typeCode;

        return code;
    }

    private String visitAssignStmt(JmmNode assignStmtNode, Void unused) {
        var lhs = assignStmtNode.get("name");
        var child = assignStmtNode.getJmmChild(0);
        var rhs = exprVisitor.visit(child);

        StringBuilder code = new StringBuilder();
        Type thisType = TypeUtils.getExprType(assignStmtNode, table);
        String typeString = OptUtils.toOllirType(thisType);

        var fields = table.getFields();

        if (fields.stream().anyMatch(f -> f.getName().equals(lhs))) {
            code.append("putfield(this,");
            code.append(SPACE);
            code.append(lhs);
            code.append(typeString);
            code.append(",");
            code.append(SPACE);
            code.append(rhs.getCode());
            code.append(").V");
            code.append(END_STMT);
            return code.toString();
        }

        if (child.getKind().equals(IDENTIFIER.toString())) {
            if (fields.stream().anyMatch(f -> f.getName().equals(child.get("value")))) {
                var temp = OptUtils.getTemp();
                var tempType = OptUtils.toOllirType(thisType);
                code.append(temp);
                code.append(tempType);
                code.append(SPACE);
                code.append(ASSIGN);
                code.append(tempType);
                code.append(SPACE);
                code.append(rhs.getCode());
                code.append(END_STMT);
                code.append(lhs);
                code.append(typeString);
                code.append(SPACE);
                code.append(ASSIGN);
                code.append(typeString);
                code.append(SPACE);
                code.append(temp);
                code.append(tempType);
                code.append(END_STMT);
                return code.toString();
            }
        }

        if (child.getKind().equals(OBJECT_DECLARATION.toString())) {
            code.append(rhs.getComputation());
            code.append(lhs);
            code.append(typeString);
            code.append(SPACE);

            code.append(ASSIGN);
            code.append(typeString);
            code.append(SPACE);

            code.append(rhs.getCode());

            if (!code.toString().endsWith(END_STMT))
                code.append(END_STMT);

            return code.toString();
        }

        if (child.getKind().equals(METHOD_CALL.toString()) || child.getKind().equals(FUNCTION_CALL.toString())) {
            var newCode = new StringBuilder();
            var temp = OptUtils.getTemp();
            var tempType = OptUtils.toOllirType(thisType);
            newCode.append(rhs.getComputation());

            newCode.append(temp);
            newCode.append(tempType);
            newCode.append(SPACE);
            newCode.append(ASSIGN);
            newCode.append(tempType);
            newCode.append(SPACE);
            newCode.append(rhs.getCode());
            newCode.append(lhs);
            newCode.append(typeString);
            newCode.append(SPACE);
            newCode.append(ASSIGN);
            newCode.append(typeString);
            newCode.append(SPACE);
            newCode.append(temp);
            newCode.append(tempType);
            newCode.append(END_STMT);
            return newCode.toString();
        }

        var rhsCode = rhs.getComputation();
        code.append(rhsCode);
        code.append(lhs);

        code.append(typeString);
        code.append(SPACE);

        code.append(ASSIGN);
        code.append(typeString);
        code.append(SPACE);

        code.append(rhs.getCode());

        if (!code.toString().endsWith(END_STMT))
            code.append(END_STMT);

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

    private String buildConstructor() {

        return ".construct " + table.getClassName() + "().V {\n" +
                "invokespecial(this, \"<init>\").V;\n" +
                "}\n";
    }
}
