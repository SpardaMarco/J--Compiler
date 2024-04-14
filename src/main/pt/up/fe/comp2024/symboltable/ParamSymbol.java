package pt.up.fe.comp2024.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;

public class ParamSymbol extends Symbol {

    private boolean isVararg = false;
    public ParamSymbol(Type type, String name) {
        super(type, name);
    }

    public ParamSymbol(Type type, String name, boolean isVararg) {
        super(type, name);
        this.isVararg = isVararg;
    }

    public boolean isVararg() {
        return isVararg;
    }
}
