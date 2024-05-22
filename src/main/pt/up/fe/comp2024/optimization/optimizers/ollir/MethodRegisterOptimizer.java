package pt.up.fe.comp2024.optimization.optimizers.ollir;

import org.antlr.v4.runtime.misc.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MethodRegisterOptimizer {

    private final String method;

    private final HashMap<String, Pair<Integer, Integer>> liveRanges = new HashMap<>();

    public MethodRegisterOptimizer(String method) {
        this.method = method;
    }

    public String optimize(int numRegisters) {

        buildLiveRanges();
        ColorGraph colorGraph = new ColorGraph(liveRanges);
        if (numRegisters == 0) {
            while (true) {
                numRegisters++;
                HashMap<Integer, HashSet<String>> allocation = colorGraph.paintWithColors(numRegisters);
                if (allocation != null) {
                    String optimizedMethod = method;
                    for (Integer optRegIndex : allocation.keySet()) {
                        for (String reg : allocation.get(optRegIndex)) {
                            optimizedMethod = optimizedMethod.replace(reg, "tmp" + optRegIndex);
                        }
                    }
                    return optimizedMethod;
                }
            }
        } else {
            HashMap<Integer, HashSet<String>> allocation = colorGraph.paintWithColors(numRegisters);
            if (allocation == null) {
                return null;
            }
            String optimizedMethod = method;
            for (Integer optRegIndex : allocation.keySet()) {
                for (String reg : allocation.get(optRegIndex)) {
                    optimizedMethod = optimizedMethod.replace(reg, "tmp" + optRegIndex);
                }
            }
            return optimizedMethod;
        }
    }

    private void buildLiveRanges() {

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

//        while (true) {
//            int registerBegin = statement.indexOf("tmp");
//            if (registerBegin == -1) {
//                break;
//            }
//            int registerEnd = statement.substring(registerBegin).indexOf(".");
//
//            registers.add(statement.substring(registerBegin, registerBegin + registerEnd));
//            statement = statement.substring(registerBegin + registerEnd);
//        }

        String regex = "\\b([a-zA-Z][a-zA-Z0-9]*)\\.[a-zA-Z0-9_]+\\b";
        Pattern pattern = Pattern.compile(regex);

        Matcher matcher = pattern.matcher(statement);
        ArrayList<String> registerNames = new ArrayList<>();

        while (matcher.find()) {
            // Group 1 is the register name
            String registerName = matcher.group(1);
            if (!registerName.startsWith("new") && !registerName.startsWith("ret"))
                registerNames.add(registerName);
        }

        // Print the extracted register names for the current statement
        System.out.println("Statement: " + statement);
        System.out.println("Register Names: " + registerNames);

        return registerNames;
    }

    private void updateLiveRanges(String register, Integer line) {

        if (liveRanges.get(register) == null) {
            liveRanges.put(register, new Pair<>(line + 1, line + 1));
        } else {
            liveRanges.put(register, new Pair<>(liveRanges.get(register).a, line));
        }
    }
}
