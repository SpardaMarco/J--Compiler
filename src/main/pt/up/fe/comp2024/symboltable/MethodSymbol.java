package pt.up.fe.comp2024.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;

import java.util.List;

public class MethodSymbol {
    private final String name;

    private final Type type;

    private final Boolean isStatic;

    private final Boolean isPublic;

    private final List<ParamSymbol> params;

    private final List<Symbol> locals;

    public MethodSymbol(String name, Type type, Boolean isStatic, Boolean isPublic, List<ParamSymbol> params, List<Symbol> locals) {
        this.name = name;
        this.type = type;
        this.isStatic = isStatic;
        this.isPublic = isPublic;
        this.params = params;
        this.locals = locals;
    }

    public String getName() {
        return name;
    }

    public Type getType() {
        return type;
    }

    public Boolean isStatic() { return isStatic; }

    public Boolean isPublic() { return isPublic; }

    public List<ParamSymbol> getParams() {
        return params;
    }

    public List<Symbol> getLocals() {
        return locals;
    }
}
