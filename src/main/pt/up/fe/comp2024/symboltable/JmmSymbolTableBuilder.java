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

        for (JmmNode child : classDecl.getChildren()) {
            if (!child.getKind().equals("VarDeclaration")) continue;

            String name = child.getChild(0).getChild(0).get("name");
            Boolean isArray = child.getChild(0).getKind() == "ArrayType";

            Type type = new Type(name, isArray);
            Symbol field = new Symbol(type, child.get("name"));
            fields.add(field);
        }

        return fields;
    }

    private static Map<String, MethodSymbol> buildMethods(JmmNode classDecl) {


        //return classDecl.getChildren(METHOD_DECL).stream()
        //        .map(method -> method.get("name"))
        //       .toList();
        return new HashMap<>();
    }

    private static Map<String, Type> buildReturnTypes(JmmNode classDecl) {
        // TODO: Simple implementation that needs to be expanded

        Map<String, Type> map = new HashMap<>();

        classDecl.getChildren(METHOD_DECL).stream()
                .forEach(method -> map.put(method.get("name"), new Type(TypeUtils.getIntTypeName(), false)));

        return map;
    }

    private static Map<String, List<Symbol>> buildParams(JmmNode classDecl) {
        // TODO: Simple implementation that needs to be expanded

        Map<String, List<Symbol>> map = new HashMap<>();

        var intType = new Type(TypeUtils.getIntTypeName(), false);

        classDecl.getChildren(METHOD_DECL).stream()
                .forEach(method -> map.put(method.get("name"), Arrays.asList(new Symbol(intType, method.getJmmChild(1).get("name")))));

        return map;
    }

    private static Map<String, List<Symbol>> buildLocals(JmmNode classDecl) {
        // TODO: Simple implementation that needs to be expanded

        Map<String, List<Symbol>> map = new HashMap<>();


        classDecl.getChildren(METHOD_DECL).stream()
                .forEach(method -> map.put(method.get("name"), getLocalsList(method)));

        return map;
    }

    private static List<Symbol> getLocalsList(JmmNode methodDecl) {
        // TODO: Simple implementation that needs to be expanded

        var intType = new Type(TypeUtils.getIntTypeName(), false);

        return methodDecl.getChildren(VAR_DECL).stream()
                .map(varDecl -> new Symbol(intType, varDecl.get("name")))
                .toList();
    }

}
