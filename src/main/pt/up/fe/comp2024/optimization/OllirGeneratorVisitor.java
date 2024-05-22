package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.List;
import java.util.stream.Collectors;

import static pt.up.fe.comp2024.ast.Kind.*;

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
    private final String GOTO = "goto";
    private final String IF = "if";
    private final String ENDIF = "endif";
    private final String WHILE_COND = "whileCond";
    private final String WHILE_END = "whileEnd";
    private final String WHILE_LOOP = "whileLoop";

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
        addVisit(IF_STMT, this::visitIfStmt);
        addVisit(WHILE_STMT, this::visitWhileStmt);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit(ARRAY_ASSIGN_STMT, this::visitArrayAssignStmt);

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
        StringBuilder code = new StringBuilder("import");
        code.append(SPACE);
        String importDecl = importNode.get("name");

        String importName = importDecl.replace("[", "").replace("]", "");
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
            code.append("extends");
            code.append(SPACE);
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
                code.append(expr.getComputation());
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

            var isMethodCall = isNodeType(METHOD_CALL.toString(), returnNode.getJmmChild(0));
            var isIdentifier = isNodeType(IDENTIFIER.toString(), returnNode.getJmmChild(0));
            var isNotLocal = true;
            var isNotParam = true;

            if (isIdentifier) {
                isNotLocal = table.getLocalVariables(methodName).stream().noneMatch(f -> f.getName().equals(returnNode.getJmmChild(0).get("value")));
                isNotParam = table.getParameters(methodName).stream().noneMatch(p -> p.getName().equals(returnNode.getJmmChild(0).get("value")));
            }

            var value = returnNode.getJmmChild(0).hasAttribute("value") ? returnNode.getJmmChild(0).get("value") : "";

            if (table.getFields().stream().anyMatch(f -> f.getName().equals(value)) && isNotLocal && isNotParam) {
                code.append(expr.getComputation());

                var temp = OptUtils.getTemp();
                var tempType = OptUtils.toOllirType(retType);

                code.append(temp).append(tempType).append(SPACE).append(ASSIGN).append(tempType).append(SPACE).append(expr.getCode());

                if (!code.toString().endsWith(END_STMT))
                    code.append(END_STMT);

                code.append(RETURN_STMT).append(OptUtils.toOllirType(retType)).append(SPACE).append(temp).append(tempType).append(END_STMT);
                return code.toString();
            }

            if (isMethodCall) {
                var temp = OptUtils.getTemp();
                var tempType = OptUtils.toOllirType(retType);

                code.append(expr.getComputation());
                code.append(temp).append(tempType).append(SPACE).append(ASSIGN).append(tempType).append(SPACE).append(expr.getCode());
                code.append(RETURN_STMT).append(OptUtils.toOllirType(retType)).append(SPACE).append(temp).append(tempType).append(END_STMT);

                return code.toString();
            }

        }

        if (returnNode.getChild(0).getKind().equals(ARRAY_ACCESS_OP.toString())) {
            var temp = OptUtils.getTemp();
            var tempType = OptUtils.toOllirType(retType);

            code.append(expr.getComputation());
            code.append(temp).append(tempType).append(SPACE).append(ASSIGN).append(tempType).append(SPACE).append(expr.getCode()).append(END_STMT);
            code.append(RETURN_STMT).append(OptUtils.toOllirType(retType)).append(SPACE).append(temp).append(tempType).append(END_STMT);

            return code.toString();
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

        return id + typeCode;
    }

    private String visitIfStmt(JmmNode ifStmt, Void unused) {
        StringBuilder code = new StringBuilder();


        var expr = exprVisitor.visit(ifStmt.getJmmChild(0));
        var exprCode = expr.getCode();
        code.append(expr.getComputation());
        code.append(IF);
        code.append("(").append(exprCode).append(")");

        code.append(SPACE);
        code.append(GOTO);
        code.append(SPACE);
        code.append(IF);
        var temp = OptUtils.getCurrentTempNum() + 1;
        code.append(temp);
        code.append(END_STMT);

        var stmt = ifStmt.getJmmChild(2);
        code.append(visit(stmt));

        code.append(GOTO);
        code.append(SPACE);
        code.append(ENDIF);
        code.append(temp);
        code.append(END_STMT);

        code.append(IF);
        code.append(temp);
        code.append(":").append("\n");

        stmt = ifStmt.getJmmChild(1);
        code.append(visit(stmt));

        code.append(ENDIF);
        code.append(temp);
        code.append(":").append("\n");

        return code.toString();
    }

    private String visitWhileStmt(JmmNode whileStmt, Void unused) {
        StringBuilder code = new StringBuilder();
        var temp = OptUtils.getTemp() + ".bool";
        var tempNum = OptUtils.getCurrentTempNum() + 1;

        var whileCond = WHILE_COND + tempNum;
        var whileLoop = WHILE_LOOP + tempNum;
        var whileEnd = WHILE_END + tempNum;

        code.append(whileCond).append(":").append("\n");

        var expr = exprVisitor.visit(whileStmt.getJmmChild(0));
        code.append(expr.getComputation());
        code.append(temp).append(SPACE);
        code.append(ASSIGN).append(".bool").append(SPACE);
        code.append(expr.getCode()).append(END_STMT);

        code.append(IF).append(SPACE).append("(").append(temp).append(")").append(SPACE);
        code.append(GOTO).append(SPACE).append(whileLoop).append(END_STMT);
        code.append(GOTO).append(SPACE).append(whileEnd).append(END_STMT);

        code.append(whileLoop).append(":").append("\n");
        var stmt = whileStmt.getJmmChild(1);
        code.append(visit(stmt));
        code.append(GOTO).append(SPACE).append(whileCond).append(END_STMT);
        code.append(whileEnd).append(":").append("\n");

        return code.toString();
    }

    private String visitArrayAssignStmt(JmmNode arrayAssignStmtNode, Void unused) {
        StringBuilder code = new StringBuilder();

        var lhs = arrayAssignStmtNode.get("name");
        Type thisType = TypeUtils.getExprType(arrayAssignStmtNode, table);

        var expr = exprVisitor.visit(arrayAssignStmtNode.getJmmChild(0));

        var indexIsMethodCall = isNodeType(METHOD_CALL.toString(), arrayAssignStmtNode.getJmmChild(0));
        var indexIsArrayAccess = isNodeType(ARRAY_ACCESS_OP.toString(), arrayAssignStmtNode.getJmmChild(0));
        var indexIsIdentifier = isNodeType(IDENTIFIER.toString(), arrayAssignStmtNode.getJmmChild(0));

        code.append(expr.getComputation());

        var fields = table.getFields();
        var methodName = (arrayAssignStmtNode.getAncestor(METHOD_DECLARATION).isPresent()) ?
                arrayAssignStmtNode.getAncestor(METHOD_DECLARATION).get().get("name") :
                arrayAssignStmtNode.getAncestor(MAIN_METHOD_DECLARATION).get().get("name");

        var locals = table.getLocalVariables(methodName);
        var params = table.getParameters(methodName);

        var isNotLocal = locals.stream().noneMatch(f -> f.getName().equals(lhs));
        var isNotParam = params.stream().noneMatch(p -> p.getName().equals(lhs));
        var isField = fields.stream().anyMatch(f -> f.getName().equals(lhs));

        if (isField && isNotLocal && isNotParam) {
            var temp = OptUtils.getTemp();
            var tempType = OptUtils.toOllirType(thisType);

            code.append(temp);
            code.append(tempType);
            code.append(SPACE);
            code.append(ASSIGN);
            code.append(tempType);
            code.append(SPACE);
            code.append("getfield(this,");
            code.append(SPACE);
            code.append(lhs);
            code.append(OptUtils.toOllirType(thisType));
            code.append(")");
            code.append(OptUtils.toOllirType(thisType));
            code.append(END_STMT);

            var rhs = exprVisitor.visit(arrayAssignStmtNode.getJmmChild(1));
            code.append(rhs.getComputation());

            if (indexIsArrayAccess || indexIsMethodCall || indexIsIdentifier) {
                if (indexIsIdentifier) {
                    var indexIsNotLocal = locals.stream().noneMatch(f -> f.getName().equals(arrayAssignStmtNode.getJmmChild(0).get("value")));
                    var indexIsNotParam = params.stream().noneMatch(p -> p.getName().equals(arrayAssignStmtNode.getJmmChild(0).get("value")));
                    var indexIsField = fields.stream().anyMatch(f -> f.getName().equals(arrayAssignStmtNode.getJmmChild(0).get("value")));

                    if (indexIsField && indexIsNotLocal && indexIsNotParam) {
                        var tempIndex = OptUtils.getTemp();
                        var tempIndexType = OptUtils.toOllirType(TypeUtils.getExprType(arrayAssignStmtNode.getJmmChild(0), table));

                        code.append(tempIndex);
                        code.append(tempIndexType);
                        code.append(SPACE);
                        code.append(ASSIGN);
                        code.append(tempIndexType);
                        code.append(SPACE);
                        code.append("getfield(this,");
                        code.append(SPACE);
                        code.append(arrayAssignStmtNode.getJmmChild(0).get("value"));
                        code.append(OptUtils.toOllirType(TypeUtils.getExprType(arrayAssignStmtNode.getJmmChild(0), table)));
                        code.append(")");
                        code.append(OptUtils.toOllirType(TypeUtils.getExprType(arrayAssignStmtNode.getJmmChild(0), table)));
                        code.append(END_STMT);

                        code.append(temp);
                        code.append('[');
                        code.append(tempIndex);
                        code.append(tempIndexType);
                        code.append(']');
                        code.append(OptUtils.toOllirType(new Type(thisType.getName(), false)));
                        code.append(SPACE);
                        code.append(ASSIGN);
                        code.append(OptUtils.toOllirType(new Type(thisType.getName(), false)));
                        code.append(SPACE);
                        code.append(rhs.getCode());
                        if (!code.toString().endsWith(END_STMT))
                            code.append(END_STMT);

                        return code.toString();
                    } else {
                        code.append(temp);
                        code.append('[');
                        code.append(expr.getCode());
                        code.append(']');
                        code.append(OptUtils.toOllirType(new Type(thisType.getName(), false)));
                        expr = exprVisitor.visit(arrayAssignStmtNode.getJmmChild(1));

                        code.append(SPACE);
                        code.append(ASSIGN);
                        code.append(OptUtils.toOllirType(new Type(thisType.getName(), false)));
                        code.append(SPACE);
                        code.append(expr.getCode());
                        code.append(END_STMT);

                        return code.toString();
                    }
                } else {
                    var tempIndex = OptUtils.getTemp();
                    var tempIndexType = OptUtils.toOllirType(TypeUtils.getExprType(arrayAssignStmtNode.getJmmChild(0), table));

                    code.append(tempIndex);
                    code.append(tempIndexType);
                    code.append(SPACE);
                    code.append(ASSIGN);
                    code.append(tempIndexType);
                    code.append(SPACE);
                    code.append(expr.getCode());
                    if (!code.toString().endsWith(END_STMT))
                        code.append(END_STMT);

                    code.append(temp);
                    code.append('[');
                    code.append(tempIndex);
                    code.append(tempIndexType);
                    code.append(']');
                    code.append(OptUtils.toOllirType(new Type(thisType.getName(), false)));
                    code.append(SPACE);
                    code.append(ASSIGN);
                    code.append(OptUtils.toOllirType(new Type(thisType.getName(), false)));
                    code.append(SPACE);
                    code.append(rhs.getCode());
                    if (!code.toString().endsWith(END_STMT))
                        code.append(END_STMT);

                    return code.toString();
                }

            }


            code.append(temp);
            code.append('[');
            code.append(expr.getCode());
            code.append(']');
            code.append(OptUtils.toOllirType(new Type(thisType.getName(), false)));
            code.append(SPACE);
            code.append(ASSIGN);
            code.append(OptUtils.toOllirType(new Type(thisType.getName(), false)));
            code.append(SPACE);
            code.append(rhs.getCode());
            if (!code.toString().endsWith(END_STMT))
                code.append(END_STMT);

            return code.toString();
        }

        code.append(lhs);
        code.append('[');
        code.append(expr.getCode());
        code.append(']');
        code.append(OptUtils.toOllirType(new Type(thisType.getName(), false)));
        expr = exprVisitor.visit(arrayAssignStmtNode.getJmmChild(1));

        code.append(SPACE);
        code.append(ASSIGN);
        code.append(OptUtils.toOllirType(new Type(thisType.getName(), false)));
        code.append(SPACE);
        code.append(expr.getCode());
        code.append(END_STMT);

        return code.toString();
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

            var isMethodCall = isNodeType(METHOD_CALL.toString(), child);
            var isArrayDecl = isNodeType(ARRAY_DECLARATION.toString(), child);
            var isIdentifier = isNodeType(IDENTIFIER.toString(), child);
            var isNotLocalId = true;
            var isNotParamId = true;

            if (isIdentifier) {
                isNotLocalId = locals.stream().noneMatch(f -> f.getName().equals(child.get("value")));
                isNotParamId = params.stream().noneMatch(p -> p.getName().equals(child.get("value")));
            }

            if (isMethodCall || isArrayDecl || (isNotLocalId && isNotParamId && isIdentifier)) {
                var temp = OptUtils.getTemp();
                var tempType = OptUtils.toOllirType(thisType);
                code.append(rhs.getComputation());

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

            var isArrayExpr = child.getKind().equals(ARRAY_EXPRESSION.toString());
            if (isArrayExpr) {
                var temp = OptUtils.getTemp();
                var tempType = OptUtils.toOllirType(thisType);

                code.append(temp);
                code.append(tempType);
                code.append(SPACE);
                code.append(ASSIGN);
                code.append(tempType);
                code.append(SPACE);
                code.append(rhs.getComputation());
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
            var isLocal = locals.stream().anyMatch(f -> f.getName().equals(child.get("value")));
            var isParam = params.stream().anyMatch(p -> p.getName().equals(child.get("value")));

            if (isLocal || isParam) {
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

            if (fields.stream().anyMatch(f -> f.getName().equals(child.get("value")))) {
                var temp = OptUtils.getTemp();
                var tempType = OptUtils.toOllirType(thisType);

                code.append(rhs.getComputation());
                code.append(temp);
                code.append(tempType);
                code.append(SPACE);
                code.append(ASSIGN);
                code.append(tempType);
                code.append(SPACE);
                code.append(rhs.getCode());

                if (!code.toString().endsWith(END_STMT))
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

        var isArrayExpr = child.getKind().equals(ARRAY_EXPRESSION.toString());
        if (isArrayExpr) {

            code.append(lhs);
            code.append(typeString);
            code.append(SPACE);
            code.append(ASSIGN);
            code.append(typeString);
            code.append(SPACE);
            code.append(rhs.getComputation());
            if (!code.toString().endsWith(END_STMT))
                code.append(END_STMT);

            code.append(rhs.getCode());

            if (!code.toString().endsWith(END_STMT))
                code.append(END_STMT);

            return code.toString();
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
            if (fromString(child.getKind()).isExpr() && node.getParent().getKind().equals(SCOPE_STMT.toString())) {
                code.append(exprVisitor.visit(child).getCode());
            } else code.append(visit(child));
        }

        return code.toString();
    }

    private String buildConstructor() {
        return ".construct " + table.getClassName() + "().V {\n" +
                "invokespecial(this, \"<init>\").V;\n" +
                "}\n";
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
