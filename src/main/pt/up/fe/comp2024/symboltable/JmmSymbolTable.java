package pt.up.fe.comp2024.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;

import java.util.*;

public class JmmSymbolTable implements SymbolTable {
    private final List<String> imports;

    private final ClassSymbol declaredClass;

    public JmmSymbolTable(List<String> imports,
                          ClassSymbol declaredClass) {
        this.imports = imports;
        this.declaredClass = declaredClass;
    }

    @Override
    public List<String> getImports() {
        return imports;
    }

    public List<String> getImportsList() {
        List<String> importsList = new ArrayList<>();

        for (String importStmt: this.getImports()) {
            String[] words = importStmt.replaceAll("[\\[\\]]", "").split(", ");
            String imported = words[words.length - 1];
            importsList.add(imported);
        }

        return importsList;
    }

    @Override
    public String getClassName() {
        return declaredClass.getClassName();
    }

    @Override
    public String getSuper() { return declaredClass.getSuperclass(); }

    @Override
    public List<Symbol> getFields() {
        return declaredClass.getFields();
    }

    @Override
    public List<String> getMethods() {
        return declaredClass.getMethods();
    }

    @Override
    public Type getReturnType(String methodSignature) {
        return declaredClass.getMethodType(methodSignature);
    }

    @Override
    public List<Symbol> getParameters(String methodSignature) {
        return Collections.unmodifiableList(declaredClass.getParams(methodSignature));
    }

    @Override
    public List<Symbol> getLocalVariables(String methodSignature) {
        return Collections.unmodifiableList(declaredClass.getLocals(methodSignature));
    }

    public MethodSymbol getMethodSymbol(String method){
        return declaredClass.getMethodSymbol(method);
    }

    public Symbol getVarDeclaration(String varName, String method) {

        for (Symbol varDecl : getParameters(method)) {
            if (varDecl.getName().equals(varName)) {
                return varDecl;
            }
        }
        for (Symbol varDecl : getLocalVariables(method)) {
            if (varDecl.getName().equals(varName)) {
                return varDecl;
            }
        }
        for (Symbol varDecl : getFields()) {
            if (varDecl.getName().equals(varName)) {
                return varDecl;
            }
        }
        return null;
    }

    public boolean classExtends() {
        return getSuper() != null;
    }
}
