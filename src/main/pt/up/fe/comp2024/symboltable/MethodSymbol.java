package pt.up.fe.comp2024.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;

import java.util.List;

public class MethodSymbol {

    private final String name;

    private final Type type;

    private final List<Symbol> params;

    private final List<Symbol> locals;


    public MethodSymbol(String name, Type type, List<Symbol> params, List<Symbol> locals) {
        this.name = name;
        this.type = type;
        this.params = params;
        this.locals = locals;
    }

    public String getName() {
        return name;
    }

    public Type getType() {
        return type;
    }

    public List<Symbol> getParams() {
        return params;
    }

    public List<Symbol> getLocals() {
        return locals;
    }
}
