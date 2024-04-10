package pt.up.fe.comp2024.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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


}
