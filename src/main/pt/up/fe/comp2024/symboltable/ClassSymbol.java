package pt.up.fe.comp2024.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ClassSymbol {
    private final String className;
    private final String superclass;
    private final List<Symbol> fields;
    private final Map<String,MethodSymbol> methods;

    public ClassSymbol(String className, String superclass, List<Symbol> fields, Map<String,MethodSymbol> methods) {
        this.className = className;
        this.superclass = superclass;
        this.fields = fields;
        this.methods = methods;
    }

    public ClassSymbol(String className, List<Symbol> fields, Map<String,MethodSymbol> methods) {
        this.className = className;
        this.superclass = null;
        this.fields = fields;
        this.methods = methods;
    }

    public String getClassName() {
        return className;
    }

    public String getSuperclass() {
        return superclass;
    }

    public List<Symbol> getFields() {
        return fields;
    }

    public List<String> getMethods() {
        return new ArrayList<>(this.methods.keySet());
    }

    public MethodSymbol getMethodSymbol(String methodSignature) {
        return methods.get(methodSignature);
    }
    public Type getMethodType(String methodSignature) {
        return methods.get(methodSignature).getType();
    }

    public List<ParamSymbol> getParams(String methodSignature) {
        return methods.get(methodSignature).getParams();
    }

    public List<Symbol> getLocals(String methodSignature) {
        return methods.get(methodSignature).getLocals();
    }
}
