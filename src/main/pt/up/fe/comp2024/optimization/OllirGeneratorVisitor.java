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
        code.append(NL);
        code.append(buildConstructor());
        code.append(NL);

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
                code.append(expr.getComputation());
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

    private String visitReturn(JmmNode returnNode, Void unused) {
        String methodName = returnNode.getAncestor(METHOD_DECLARATION).get().get("name");
        Type retType = table.getReturnType(methodName);

        StringBuilder code = new StringBuilder(RETURN_STMT);
        code.append(OptUtils.toOllirType(retType));

        var expr = OllirExprResult.EMPTY;

        if (returnNode.getNumChildren() > 0) {
            code = new StringBuilder();
            expr = exprVisitor.visit(returnNode.getJmmChild(0));
            var isMethodCall = isNodeType(METHOD_CALL.toString(), returnNode.getJmmChild(0));
            if (isMethodCall) {
                var temp = OptUtils.getTemp();
                var tempType = OptUtils.toOllirType(retType);
                code.append(expr.getComputation());
                code.append(temp).append(tempType).append(SPACE).append(ASSIGN).append(tempType).append(SPACE).append(expr.getCode());
                code.append(RETURN_STMT).append(OptUtils.toOllirType(retType)).append(SPACE).append(temp).append(tempType).append(END_STMT);
                return code.toString();
            }
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
        var methodName = (assignStmtNode.getAncestor(METHOD_DECLARATION).isPresent()) ?
                assignStmtNode.getAncestor(METHOD_DECLARATION).get().get("name") :
                assignStmtNode.getAncestor(MAIN_METHOD_DECLARATION).get().get("name");

        var locals = table.getLocalVariables(methodName);
        var params = table.getParameters(methodName);

        var isNotLocal = locals.stream().noneMatch(f -> f.getName().equals(lhs));
        var isNotParam = params.stream().noneMatch(p -> p.getName().equals(lhs));

        if (fields.stream().anyMatch(f -> f.getName().equals(lhs)) && isNotLocal && isNotParam) {
            code.append(rhs.getComputation());
            var isMethodCall = isNodeType(METHOD_CALL.toString(), child);
            var isIdentifier = isNodeType(IDENTIFIER.toString(), child);
            var isNotLocalId = true;
            var isNotParamId = true;
            if (isIdentifier) {
                isNotLocalId = locals.stream().noneMatch(f -> f.getName().equals(child.get("value")));
                isNotParamId = params.stream().noneMatch(p -> p.getName().equals(child.get("value")));
            }
            if (isMethodCall || (isNotLocalId && isNotParamId && isIdentifier)) {
                var temp = OptUtils.getTemp();
                var tempType = OptUtils.toOllirType(thisType);
                code.append(temp);
                code.append(tempType);
                code.append(SPACE);
                code.append(ASSIGN);
                code.append(tempType);
                code.append(SPACE);
                code.append(rhs.getCode());
                if (!code.toString().endsWith(END_STMT))
                    code.append(END_STMT);
                code.append("putfield(this,");
                code.append(SPACE);
                code.append(lhs);
                code.append(typeString);
                code.append(",");
                code.append(SPACE);
                code.append(temp);
                code.append(tempType);
                code.append(").V");
                code.append(END_STMT);
                return code.toString();
            }
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
        var isIdentifier = isNodeType(IDENTIFIER.toString(), child);
        if (isIdentifier) {
            if (fields.stream().anyMatch(f -> f.getName().equals(child.get("value")))) {
                code.append(lhs);
                code.append(typeString);
                code.append(SPACE);
                code.append(ASSIGN);
                code.append(typeString);
                code.append(SPACE);
                code.append(rhs.getCode());
                code.append(END_STMT);
                return code.toString();
            }
        }
        var isObjectDeclaration = isNodeType(OBJECT_DECLARATION.toString(), child);
        if (isObjectDeclaration) {
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
        var isMethodCall = isNodeType(METHOD_CALL.toString(), child);
        if (isMethodCall) {
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
