package pt.up.fe.comp2024.optimization.optimizers.ollir;

import org.antlr.v4.runtime.misc.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MethodRegisterOptimizer {

    private final String method;

    private final HashMap<String, Pair<Integer, Integer>> liveRanges = new HashMap<>();

    public MethodRegisterOptimizer(String method) {
        this.method = method;
    }

    public String optimize() {

        buildBasicBlocks();
        return method;
    }

    private void buildBasicBlocks() {

        OllirMethodStream stream = new OllirMethodStream(method);

        int line = 0;
        String statement;
        while ((statement = stream.nextStatement()) != null) {
            int registerLine = line++;
            for (String register : getRegisters(statement)) {
                updateLiveRanges(register, registerLine);
            }
        }
    }

    private List<String> getRegisters(String statement) {
        List<String> registers = new ArrayList<>();

        while (true) {
            int registerBegin = statement.indexOf("tmp");
            if (registerBegin == -1) {
                break;
            }
            int registerEnd = statement.substring(registerBegin).indexOf(".");

            registers.add(statement.substring(registerBegin, registerBegin + registerEnd));
            statement = statement.substring(registerBegin + registerEnd);
        }

        return registers;
    }

    private void updateLiveRanges(String register, Integer line) {

        if (liveRanges.get(register) == null) {
            liveRanges.put(register, new Pair<>(line, line));
        } else {
            liveRanges.put(register, new Pair<>(liveRanges.get(register).a, line));
        }
    }
}
