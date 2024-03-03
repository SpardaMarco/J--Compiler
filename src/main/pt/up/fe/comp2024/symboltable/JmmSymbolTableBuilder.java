package pt.up.fe.comp2024.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import java.lang.reflect.Method;
import java.util.*;

import static pt.up.fe.comp2024.ast.Kind.METHOD_DECL;
import static pt.up.fe.comp2024.ast.Kind.VAR_DECL;

public class JmmSymbolTableBuilder {
    public static JmmSymbolTable build(JmmNode root) {
        var imports = buildImports(root);
        var declaredClass = buildClass(root);

        return new JmmSymbolTable(imports, declaredClass);
    }

    private static List<String> buildImports(JmmNode root) {
        int numImports = root.getNumChildren()-1;
        List<String> imports = new ArrayList<>();

        for (int i = 0; i < numImports; i++) {
            imports.add(root.getJmmChild(i).get("name"));
        }

        return imports;
    }

    private static ClassSymbol buildClass(JmmNode root) {
        int classDeclIndex = root.getNumChildren()-1;
        var classDecl = root.getJmmChild(classDeclIndex);
        //SpecsCheck.checkArgument(Kind.CLASS_DECL.check(classDecl), () -> "Expected a class declaration: " + classDecl);
        String className = classDecl.get("name");

        var superclass = buildSuper(classDecl);
        var fields = buildFields(classDecl);
        var methods = buildMethods(classDecl);

        return new ClassSymbol(className, superclass, fields, methods);
    }

    private static String buildSuper(JmmNode classDecl) {
        return classDecl.hasAttribute("parent") ? classDecl.get("parent") : null;
    }

    private static List<Symbol> buildFields(JmmNode classDecl) {
        List<Symbol> fields = new ArrayList<>();

        for (JmmNode child: classDecl.getChildren()) {
            if (!child.getKind().equals("VarDeclaration")) continue;

            Type type = buildType(child.getChild(0));

            Symbol field = new Symbol(type, child.get("name"));
            fields.add(field);
        }

        return fields;
    }

    private static Type buildType(JmmNode typeNode) {
        String typeName = typeNode.getChild(0).get("name");
        boolean isArray = typeNode.getKind().equals("ArrayType");
        return new Type(typeName, isArray);
    }

    private static Map<String, MethodSymbol> buildMethods(JmmNode classDecl) {
        Map<String, MethodSymbol> methodSymbols = new HashMap<>();

        // Main method
        if (!classDecl.getChildren("MainMethodDeclaration").isEmpty()) {
            JmmNode mainMethod = classDecl.getChildren("MainMethodDeclaration").get(0);

            String name = mainMethod.get("name");
            Type returnType = new Type(TypeUtils.getVoidTypeName(), false);

            List<Symbol> params = new ArrayList<>();
            Symbol param = new Symbol(new Type("String", true), "args");
            params.add(param);

            List<Symbol> locals = buildFields(mainMethod);

            MethodSymbol methodSymbol = new MethodSymbol(
                    name,
                    returnType,
                    mainMethod.get("isStatic").equals("true"),
                    mainMethod.get("isPublic").equals("true"),
                    params,
                    locals
            );

            methodSymbols.put(name, methodSymbol);
        }

        for (JmmNode method: classDecl.getChildren("MethodDeclaration")) {

            String name = method.get("name");
            Type returnType = buildType(method.getChild(0));

            List<Symbol> params = buildParams(method);
            List<Symbol> locals = buildFields(method);

            MethodSymbol methodSymbol = new MethodSymbol(
                    name,
                    returnType,
                    method.get("isStatic").equals("true"),
                    method.get("isPublic").equals("true"),
                    params,
                    locals
            );

            methodSymbols.put(name, methodSymbol);
        }

        return methodSymbols;
    }

    private static List<Symbol> buildParams(JmmNode methodDecl) {
        List<Symbol> params = new ArrayList<>();

        for (JmmNode param: methodDecl.getChildren()) {
            if (!param.getKind().equals("Params")) continue;
            Symbol symbol = new Symbol(buildType(param.getChild(0)), param.get("name"));
            params.add(symbol);
            while (!param.getChildren("Params").isEmpty()) {
                param = param.getChildren("Params").get(0);
                if (param.get("isVarArg").equals("true")) {
                    symbol = new Symbol(new Type(TypeUtils.getIntTypeName(), true), param.get("name"));
                    params.add(symbol);
                    break;
                }
                symbol = new Symbol(buildType(param.getChild(0)), param.get("name"));
                params.add(symbol);
            }
        }
        return params;
    }
}
