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
        addVisit(ARRAY_EXPRESSION, this::visitArrayExpression);

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
        var hasValue = firstChild.hasAttribute("value");
        var firstChildValue = (hasValue) ? firstChild.get("value") : firstChild.hasAttribute("name") ? firstChild.get("name") : "";
        var firstChildIsMethodCall = isNodeType(METHOD_CALL.toString(), firstChild);
        var firstChildIsArrayDecl = isNodeType(ARRAY_DECLARATION.toString(), firstChild);
        if (firstChildIsMethodCall || firstChildIsArrayDecl) {
            firstChildValue = "";
        }

        var secondChild = arrayAccessNode.getJmmChild(1);
        hasValue = secondChild.hasAttribute("value");
        var hasName = secondChild.hasAttribute("name");
        var isArrayAccessOp = isNodeType(ARRAY_ACCESS_OP.toString(), secondChild);

        var fields = table.getFields();
        var methodName = (arrayAccessNode.getAncestor(METHOD_DECLARATION).isPresent()) ?
                arrayAccessNode.getAncestor(METHOD_DECLARATION).get().get("name") :
                arrayAccessNode.getAncestor(MAIN_METHOD_DECLARATION).get().get("name");
        var locals = table.getLocalVariables(methodName);
        var params = table.getParameters(methodName);

        var firstChildName = firstChildValue;
        var isNotLocal = locals.stream().noneMatch(l -> l.getName().equals(firstChildName));
        var isNotParam = params.stream().noneMatch(p -> p.getName().equals(firstChildName));
        var isField = fields.stream().anyMatch(f -> f.getName().equals(firstChildName));

        if ((isNotLocal && isNotParam && isField) || firstChildValue.isEmpty()) {
            var result = visit(firstChild);

            var temp = OptUtils.getTemp();
            var tempType = OptUtils.toOllirType(TypeUtils.getExprType(firstChild, table));

            if (firstChildValue.isEmpty()) {
                if (firstChildIsMethodCall || firstChildIsArrayDecl) {
                    computation.append(result.getComputation());
                    computation.append(temp).append(tempType).append(SPACE).append(ASSIGN).append(tempType).append(SPACE);
                    computation.append(result.getCode());

                } else {
                    computation.append(temp).append(tempType).append(SPACE).append(ASSIGN).append(tempType).append(SPACE);
                    computation.append(result.getComputation());
                    if (!computation.toString().endsWith(END_STMT))
                        computation.append(END_STMT);
                    computation.append(result.getCode());
                }
            } else {
                computation.append(result.getComputation());
                computation.append(temp).append(tempType).append(SPACE)
                        .append(ASSIGN).append(tempType).append(SPACE);
                computation.append(result.getCode());
            }
            if (!computation.toString().endsWith(END_STMT))
                computation.append(END_STMT);

            firstChildValue = temp;
        }

        if ((!hasValue && !hasName) || isArrayAccessOp) {
            var result = visit(secondChild);
            var resultCode = result.getCode();
            var isMethodCall = isNodeType(METHOD_CALL.toString(), secondChild);

            if (isMethodCall) {
                var temp = OptUtils.getTemp();
                var tempType = OptUtils.toOllirType(TypeUtils.getExprType(secondChild, table));
                computation.append(result.getComputation());
                computation.append(temp).append(tempType).append(SPACE).append(ASSIGN).append(tempType).append(SPACE);
                computation.append(result.getCode());
                if (!computation.toString().endsWith(END_STMT))
                    computation.append(END_STMT);
                resultCode = temp + tempType;
            } //else if (!isArrayAccessOp) computation.append(result.getComputation());
            else {
                computation.append(result.getComputation());
                var temp = OptUtils.getTemp();
                var tempType = OptUtils.toOllirType(TypeUtils.getExprType(secondChild, table));
                computation.append(temp).append(tempType).append(SPACE).append(ASSIGN).append(tempType).append(SPACE);
                computation.append(result.getCode()).append(END_STMT);
                resultCode = temp + tempType;
            }
            code.append(firstChildValue).append("[").append(resultCode).append("]");
            var type = TypeUtils.getExprType(arrayAccessNode, table);
            code.append(OptUtils.toOllirType(type));
        } else {
            var isMethodCall = isNodeType(METHOD_CALL.toString(), secondChild);
            var isArrayAccess = isNodeType(ARRAY_ACCESS_OP.toString(), secondChild);
            var isIdentifier = isNodeType(IDENTIFIER.toString(), secondChild);

            if (isMethodCall || isArrayAccess || isIdentifier) {
                if (isIdentifier) {
                    var secondChildValue = (hasValue) ? secondChild.get("value") : secondChild.get("name");
                    var indexIsNotLocal = locals.stream().noneMatch(l -> l.getName().equals(secondChildValue));
                    var indexIsNotParam = params.stream().noneMatch(p -> p.getName().equals(secondChildValue));
                    var indexIsField = fields.stream().anyMatch(f -> f.getName().equals(secondChildValue));
                    if (indexIsNotLocal && indexIsNotParam && indexIsField) {
                        var result = visit(secondChild);
                        var temp = OptUtils.getTemp();
                        var tempType = OptUtils.toOllirType(TypeUtils.getExprType(secondChild, table));
                        computation.append(result.getComputation());
                        computation.append(temp).append(tempType).append(SPACE).append(ASSIGN).append(tempType).append(SPACE);
                        computation.append(result.getCode()).append(END_STMT);
                        code.append(firstChildValue).append("[").append(temp).append(tempType).append("]");
                        var type = TypeUtils.getExprType(arrayAccessNode, table);
                        code.append(OptUtils.toOllirType(type));
                    } else {
                        var secondChildType = TypeUtils.getExprType(secondChild, table);
                        code.append(firstChildValue).append("[").append(secondChildValue).append(OptUtils.toOllirType(secondChildType)).append("]");
                        var type = TypeUtils.getExprType(arrayAccessNode, table);
                        code.append(OptUtils.toOllirType(type));
                    }

                } else {
                    var temp = OptUtils.getTemp();
                    var tempType = OptUtils.toOllirType(TypeUtils.getExprType(secondChild, table));
                    var secondChildResult = visit(secondChild);
                    computation.append(secondChildResult.getComputation());
                    computation.append(temp).append(tempType).append(SPACE).append(ASSIGN).append(tempType).append(SPACE);
                    computation.append(secondChildResult.getCode());
                    if (!computation.toString().endsWith(END_STMT))
                        computation.append(END_STMT);
                    code.append(firstChildValue).append("[").append(temp).append(tempType).append("]");
                    var type = TypeUtils.getExprType(arrayAccessNode, table);
                    code.append(OptUtils.toOllirType(type));
                }
            } else {
                var secondChildValue = (hasValue) ? secondChild.get("value") : secondChild.get("name");
                var secondChildType = TypeUtils.getExprType(secondChild, table);
                code.append(firstChildValue).append("[").append(secondChildValue).append(OptUtils.toOllirType(secondChildType)).append("]");
                var type = TypeUtils.getExprType(arrayAccessNode, table);
                code.append(OptUtils.toOllirType(type));
            }
        }

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

        var hasValue = child.hasAttribute("value");
        var hasName = child.hasAttribute("name");
        var id = (hasValue) ? child.get("value") : ((hasName) ? child.get("name") : "");

        var isMethodCall = isNodeType(METHOD_CALL.toString(), child);

        if (id.isEmpty() || isMethodCall) {
            var result = visit(child);
            code.append(result.getComputation());

            if (isMethodCall) {
                code.append("tmp").append(OptUtils.getCurrentTempNum() + 1);
                var type = OptUtils.toOllirType(TypeUtils.getExprType(child, table));
                code.append(type).append(SPACE).append(ASSIGN).append(type).append(SPACE);
                //code.append(result.getCode());
            }

            code.append(result.getCode());


            var prevTemp = OptUtils.getTemp();
            var nextTempNum = OptUtils.getCurrentTempNum() + 1;
            var temp = "tmp" + nextTempNum;
            var tempType = OptUtils.toOllirType(TypeUtils.getExprType(child, table));

            code.append(temp).append(".i32").append(SPACE).append(ASSIGN).append(".i32").append(SPACE);
            code.append(ARRAY_LENGTH);
            code.append("(").append(prevTemp).append(tempType).append(")");

            var thisType = attributeNode.get("type");
            var type = OptUtils.toOllirType(new Type(thisType, false));
            code.append(type);

            return new OllirExprResult(code.toString(), "");
        }

        var idType = OptUtils.toOllirType(TypeUtils.getExprType(child, table));

        code.append(ARRAY_LENGTH);
        code.append("(").append(id).append(idType).append(")");

        var thisType = attributeNode.get("type");
        var type = OptUtils.toOllirType(new Type(thisType, false));
        code.append(type);

        return new OllirExprResult(code.toString(), "");
    }

    private OllirExprResult visitArrayExpression(JmmNode arrayExpression, Void unused) {
        var code = new StringBuilder();
        var computation = new StringBuilder();

        JmmNode parent = arrayExpression.getParent();
        while (true) {
            if (parent.getKind().equals(PAREN_EXPR.toString())) {
                parent = parent.getParent();
            } else {
                break;
            }
        }

        var parentType = parent.get("type");
        var parentName = "";
        if (parent.getKind().equals(METHOD_CALL.toString())
                || parent.getKind().equals(RETURN.toString())
                || parent.getKind().equals(ARRAY_ACCESS_OP.toString())
                || parent.getKind().equals(ATTRIBUTE.toString())) {
            parentName = "tmp" + (OptUtils.getCurrentTempNum() + 1);
        } else {
            parentName = parent.get("name");
        }

        var parentOllirType = OptUtils.toOllirType(new Type(parentType, true));

        var numChildren = arrayExpression.getNumChildren();

        if (arrayExpression.getParent().getKind().equals(RETURN.toString())) {
            computation.append(parentName).append(parentOllirType).append(SPACE).append(ASSIGN).append(parentOllirType).append(SPACE);
        }

        computation.append("new(array,").append(SPACE).append(numChildren).append(".i32").append(")");
        computation.append(parentOllirType);
        computation.append(END_STMT);

        if (numChildren > 0) {
            var childrenType = arrayExpression.getChild(0).get("type");
            var childrenOllirType = OptUtils.toOllirType(new Type(childrenType, false));

            for (var i = 0; i < numChildren; i++) {
                var child = arrayExpression.getChild(i);

                var isChildParenExpr = child.getKind().equals(PAREN_EXPR.toString());
                var isChildBinaryOp = child.getKind().equals(BINARY_OP.toString());
                var isChildArrayAccessOp = child.getKind().equals(ARRAY_ACCESS_OP.toString());
                var isChildUnaryOp = child.getKind().equals(UNARY_OP.toString());

                var result = visit(arrayExpression.getChild(i));

                if (isChildParenExpr) {
                    result = visit(child.getJmmChild(0));
                    isChildBinaryOp = child.getJmmChild(0).getKind().equals(BINARY_OP.toString());
                    isChildArrayAccessOp = child.getJmmChild(0).getKind().equals(ARRAY_ACCESS_OP.toString());
                }

                if (isChildBinaryOp) {
                    computation.append(result.getComputation());

                    parentName = "tmp" + (OptUtils.getCurrentTempNum() + 1);

                    code.append(parentName);
                    code.append("[").append(i).append(".i32").append("]").append(childrenOllirType);
                    code.append(SPACE).append(ASSIGN).append(childrenOllirType).append(SPACE);
                    code.append(result.getCode());
                    code.append(END_STMT);

                    continue;
                } else if (isChildUnaryOp) {
                    computation.append(result.getComputation());

                    parentName = "tmp" + (OptUtils.getCurrentTempNum() + 1);

                    code.append(parentName);
                    code.append("[").append(i).append(".i32").append("]").append(childrenOllirType);
                    code.append(SPACE).append(ASSIGN).append(childrenOllirType).append(SPACE);
                    code.append(result.getCode());
                    code.append(END_STMT);

                    continue;
                } else if (isChildArrayAccessOp) {
                    computation.append(result.getComputation());

                    var temp = OptUtils.getTemp();
                    var tempType = OptUtils.toOllirType(TypeUtils.getExprType(arrayExpression.getChild(i), table));

                    code.append(temp).append(tempType).append(SPACE).append(ASSIGN).append(tempType).append(SPACE);

                    code.append(result.getCode());
                    code.append(END_STMT);

                    parentName = "tmp" + (OptUtils.getCurrentTempNum() + 1);

                    code.append(parentName);
                    code.append("[").append(i).append(".i32").append("]").append(childrenOllirType);
                    code.append(SPACE).append(ASSIGN).append(childrenOllirType).append(SPACE);
                    code.append(temp).append(tempType);
                    code.append(END_STMT);

                    continue;
                }

                if (isChildParenExpr) continue;

                code.append(parentName);
                code.append("[").append(i).append(".i32").append("]").append(childrenOllirType);
                code.append(SPACE).append(ASSIGN).append(childrenOllirType).append(SPACE);
                var hasValue = child.hasAttribute("value");
                var childValue = (hasValue) ? arrayExpression.getChild(i).get("value") : arrayExpression.getChild(i).get("name");
                code.append(childValue).append(childrenOllirType);
                code.append(END_STMT);
            }
        }

        return new OllirExprResult(code.toString(), computation.toString());
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

            var isNotLocal = false;
            var isNotParam = false;
            var isField = false;

            if (exprHasValue) {
                isNotLocal = locals.stream().noneMatch(l -> l.getName().equals(methodCallNode.getJmmChild(0).get("value")));
                isNotParam = params.stream().noneMatch(p -> p.getName().equals(methodCallNode.getJmmChild(0).get("value")));
                isField = fields.stream().anyMatch(f -> f.getName().equals(methodCallNode.getJmmChild(0).get("value")));
            }

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
            if (methodSymbol == null && table.classExtends()) {
                code.append("invokevirtual(");
            } else if (methodSymbol.isStatic()) {
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
                var isArrayExpr = isNodeType(ARRAY_EXPRESSION.toString(), methodCallNode.getJmmChild(index));

                if (isArrayExpr) {
                    var temp = OptUtils.getTemp();
                    var tempType = OptUtils.toOllirType(TypeUtils.getExprType(methodCallNode.getJmmChild(index), table));
                    computation.append(temp).append(tempType).append(SPACE)
                            .append(ASSIGN).append(tempType).append(SPACE);
                    computation.append(result.getComputation());
                    computation.append(result.getCode());

                    code.append(temp).append(tempType);
                } else if (!(isLiteral || !isNotLocal || !isNotParam || isObjectDecl || isBinExpr)) {
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
            var method = table.getMethodSymbol(name);
            if (!isVararg && method != null && index < method.getParams().size() + 1 && method.getParams().get(index - 1).isVararg()) {
                var thisType = method.getParams().get(index - 1).getType();
                var typeVarArg = OptUtils.toOllirType(new Type(thisType.getName(), true));
                var temp = OptUtils.getTemp();
                computation.append(temp).append(typeVarArg).append(SPACE)
                        .append(ASSIGN).append(typeVarArg).append(SPACE);
                computation.append("new(array,").append(SPACE);
                computation.append(0).append(".i32").append(")").append(typeVarArg).append(END_STMT);
                code.append(", ").append(temp).append(typeVarArg);
            }
            if (isVararg) {
                var thisType = methodCallNode.getJmmChild(index).get("type");
                var isArray = methodCallNode.getJmmChild(index).hasAttribute("isArray") && methodCallNode.getJmmChild(index).get("isArray").equals("true");
                if (isArray) {
                    var child = methodCallNode.getJmmChild(index);
                    var result = visit(child);
                    var isIdentifier = isNodeType(IDENTIFIER.toString(), child);
                    if (isIdentifier) {
                        computation.append(result.getComputation());
                        var childValue = child.get("value");
                        var fields = table.getFields();
                        var isNotLocal = locals.stream().noneMatch(l -> l.getName().equals(childValue));
                        var isNotParam = params.stream().noneMatch(p -> p.getName().equals(childValue));
                        var isField = fields.stream().anyMatch(f -> f.getName().equals(childValue));
                        if (isNotLocal && isNotParam && isField) {
                            var temp = OptUtils.getTemp();
                            var tempType = OptUtils.toOllirType(TypeUtils.getExprType(child, table));
                            computation.append(temp).append(tempType).append(SPACE)
                                    .append(ASSIGN).append(tempType).append(SPACE).append(result.getCode());
                            if (!computation.toString().endsWith(END_STMT))
                                computation.append(END_STMT);
                            code.append(temp).append(tempType);
                        } else {
                            code.append(result.getCode());
                        }
                    } else {
                        var isMethodCall = isNodeType(METHOD_CALL.toString(), child);
                        var isArrayExpr = isNodeType(ARRAY_EXPRESSION.toString(), child);
                        var isArrayDecl = isNodeType(ARRAY_DECLARATION.toString(), child);

                        if (isArrayExpr || isArrayDecl) {
                            var temp = OptUtils.getTemp();
                            var tempType = OptUtils.toOllirType(TypeUtils.getExprType(child, table));
                            computation.append(temp).append(tempType).append(SPACE)
                                    .append(ASSIGN).append(tempType).append(SPACE).append(result.getComputation());
                            computation.append(result.getCode());
                            if (!computation.toString().endsWith(END_STMT))
                                computation.append(END_STMT);
                            code.append(temp).append(tempType);
                        } else if (isMethodCall) {
                            var temp = OptUtils.getTemp();
                            var tempType = OptUtils.toOllirType(TypeUtils.getExprType(child, table));
                            computation.append(result.getComputation());
                            computation.append(temp).append(tempType).append(SPACE)
                                    .append(ASSIGN).append(tempType).append(SPACE).append(result.getCode());
                            if (!computation.toString().endsWith(END_STMT))
                                computation.append(END_STMT);
                            code.append(temp).append(tempType);
                        } else {
                            code.append(result.getCode());

                        }
                    }

                } else {
                    var numOfArgs = methodCallNode.getNumChildren() - index;
                    var currArg = 0;
                    var typeVarArg = OptUtils.toOllirType(new Type(thisType, true));
                    var temp = OptUtils.getTemp();
                    computation.append(temp).append(typeVarArg).append(SPACE)
                            .append(ASSIGN).append(typeVarArg).append(SPACE);
                    computation.append("new(array,").append(SPACE);
                    computation.append(numOfArgs).append(".i32").append(")").append(typeVarArg).append(END_STMT);
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

                        computation.append(temp).append('[').append(currArg).append(".i32").append(']').append(tempType).append(SPACE)
                                .append(ASSIGN).append(typeVarArg).append(SPACE).append(newTemp).append(tempType);
                        if (!computation.toString().endsWith(END_STMT))
                            computation.append(END_STMT);

                        currArg++;
                    }
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
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();

        var child = unaryExprNode.getChild(0);
        var isChildMethodCall = isNodeType(METHOD_CALL.toString(), child);
        var isChildUnaryOp = isNodeType(UNARY_OP.toString(), child);

        var methodName = (unaryExprNode.getAncestor(METHOD_DECLARATION).isPresent()) ?
                unaryExprNode.getAncestor(METHOD_DECLARATION).get().get("name") :
                unaryExprNode.getAncestor(MAIN_METHOD_DECLARATION).get().get("name");

        var locals = table.getLocalVariables(methodName);
        var params = table.getParameters(methodName);

        var childIsField = false;

        var hasValue = child.hasAttribute("value");

        if (!child.getKind().equals(PAREN_EXPR.toString()) && hasValue) {
            var childIsNotLocal = locals.stream().noneMatch(l -> l.getName().equals(child.get("value")));
            var childIsNotParam = params.stream().noneMatch(p -> p.getName().equals(child.get("value")));

            var childCouldBeField = table.getFields().stream().anyMatch(f -> f.getName().equals(child.get("value")));

            childIsField = childIsNotLocal && childIsNotParam && childCouldBeField;
        }

        var result = visit(unaryExprNode.getJmmChild(0));

        if (isChildMethodCall || isChildUnaryOp || childIsField) {
            var temp = OptUtils.getTemp();
            var tempType = OptUtils.toOllirType(TypeUtils.getExprType(unaryExprNode.getJmmChild(0), table));
            computation.append(result.getComputation());
            computation.append(temp).append(tempType).append(SPACE)
                    .append(ASSIGN).append(tempType).append(SPACE).append(result.getCode());
            if (!computation.toString().endsWith(END_STMT))
                computation.append(END_STMT);
            code.append(NOT).append(".bool").append(SPACE).append(temp).append(tempType);
        } else {
            computation.append(result.getComputation());
            code.append(NOT).append(".bool").append(SPACE).append(result.getCode());
        }

        return new OllirExprResult(code.toString(), computation.toString());
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
        String conditionLabel = "condition" + (OptUtils.getCurrentTempNum() + 1) + "Label";
        String result = OptUtils.getTemp() + ".bool";
        var methodName = (binExprNode.getAncestor(METHOD_DECLARATION).isPresent()) ?
                binExprNode.getAncestor(METHOD_DECLARATION).get().get("name") :
                binExprNode.getAncestor(MAIN_METHOD_DECLARATION).get().get("name");
        var locals = table.getLocalVariables(methodName);
        var params = table.getParameters(methodName);
        var fields = table.getFields();

        for (var i = 0; i < children.size() - 1; i++) {
            var child = children.get(i);
            var childResult = visit(child);
            var childKind = child.getKind();
            var getFieldCond = false;
            if (childKind.equals(IDENTIFIER.toString()) || childKind.equals(PAREN_EXPR.toString())) {
                var childToAnalyze = child;
                while (true) {
                    if (childToAnalyze.getKind().equals(IDENTIFIER.toString())) {
                        break;
                    } else if (childToAnalyze.getKind().equals(PAREN_EXPR.toString())) {
                        childToAnalyze = childToAnalyze.getJmmChild(0);
                    } else {
                        childToAnalyze = null;
                        break;
                    }
                }
                if (childToAnalyze != null && childToAnalyze.getKind().equals(IDENTIFIER.toString())) {
                    var childIdentifier = childToAnalyze;
                    var isNotLocal = locals.stream().noneMatch(l -> l.getName().equals(childIdentifier.get("value")));
                    var isNotParam = params.stream().noneMatch(p -> p.getName().equals(childIdentifier.get("value")));
                    var isField = fields.stream().anyMatch(f -> f.getName().equals(childIdentifier.get("value")));
                    getFieldCond = isNotLocal && isNotParam && isField;
                }
            }
            var childResultToAppend = childResult.getCode();

            computation.append(childResult.getComputation());

            var isUnaryOp = childKind.equals(UNARY_OP.toString());

            if (childKind.equals(ARRAY_ACCESS_OP.toString()) || childKind.equals(METHOD_CALL.toString()) || getFieldCond || isUnaryOp) {
                var temp = OptUtils.getTemp();
                var tempType = OptUtils.toOllirType(TypeUtils.getExprType(child, table));
                computation.append(temp).append(tempType).append(SPACE)
                        .append(ASSIGN).append(tempType).append(SPACE).append(childResult.getCode());
                if (!computation.toString().endsWith(END_STMT))
                    computation.append(END_STMT);
                childResultToAppend = temp + tempType;
            }
            String nextJump = conditionLabel + i;
            computation.append("if (").append(childResultToAppend).append(") goto ").append(nextJump).append(END_STMT);
            computation.append(result).append(SPACE).append(ASSIGN).append(".bool").append(SPACE).append("0.bool").append(END_STMT);
            computation.append("goto ").append(endLabel).append(END_STMT);
            computation.append(nextJump).append(":").append('\n');
        }

        var lastChild = children.get(children.size() - 1);
        var lastChildResult = visit(lastChild);
        computation.append(lastChildResult.getComputation());

        computation.append(result).append(SPACE).append(ASSIGN).append(".bool").append(SPACE).append(lastChildResult.getCode());
        if (!computation.toString().endsWith(END_STMT))
            computation.append(END_STMT);
//        computation.append("goto ").append(endLabel).append(END_STMT);
//        computation.append(falseLabel).append(":").append('\n');
//        computation.append(result).append(SPACE).append(ASSIGN).append(".bool").append(SPACE).append("0.bool").append(END_STMT);
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
            } else if (!lhs.getComputation().isEmpty()) {
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
// isNotLocal && naofor param && nao for literal == field || methodcall
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
            } else if (!rhs.getComputation().isEmpty()) {
                computation.append(rhs.getComputation());
                var temp = OptUtils.getTemp();
                var tempType = OptUtils.toOllirType(TypeUtils.getExprType(binExprNode.getJmmChild(1), table));
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

        if (binExprNode.getParent().getKind().equals(ASSIGN_STMT.toString())) {
            StringBuilder code = new StringBuilder();
            code.append(lhsToAppend).append(SPACE).append(op).append(resOllirType).append(SPACE);
            code.append(rhsToAppend);
            if (!code.toString().endsWith(END_STMT))
                code.append(END_STMT);
            return new OllirExprResult(code.toString(), computation.toString());
        }

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
