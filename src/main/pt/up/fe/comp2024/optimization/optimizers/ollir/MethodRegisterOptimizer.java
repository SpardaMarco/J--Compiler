package pt.up.fe.comp2024.optimization.optimizers.ollir;

import org.antlr.v4.runtime.misc.Pair;
import pt.up.fe.comp2024.symboltable.JmmSymbolTable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MethodRegisterOptimizer {

    private final String methodName;
    private final String method;

    private final JmmSymbolTable table;

    private final HashMap<String, Pair<Integer, Integer>> liveRanges = new HashMap<>();

    public MethodRegisterOptimizer(String methodName, String method, JmmSymbolTable table) {
        this.methodName = methodName;
        this.method = method;
        this.table = table;
    }

    public String optimize(int numRegisters) {

        buildLiveRanges();
        ColorGraph colorGraph = new ColorGraph(liveRanges);
        if (numRegisters == 0) {
            while (true) {
                numRegisters++;
                HashMap<Integer, HashSet<String>> allocation = colorGraph.paintWithColors(numRegisters);
                if (allocation != null) {
                    return replaceRegisters(allocation);
                }
            }
        } else {
            HashMap<Integer, HashSet<String>> allocation = colorGraph.paintWithColors(numRegisters);
            if (allocation != null) {
                return replaceRegisters(allocation);
            }
        }
        return null;
    }

    private String replaceRegisters(HashMap<Integer, HashSet<String>> allocation) {
        String optimizedMethod = method;
        for (Integer optRegIndex : allocation.keySet()) {
            for (String reg : allocation.get(optRegIndex)) {
                optimizedMethod = optimizedMethod.replace(reg + ".", "tmp" + optRegIndex + ".");
            }
        }
        return optimizedMethod;
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
//
//            if (statement.contains(":=")) {
//                statement = statement.substring(statement.indexOf(":=") + 2);
//            }
//
//            int registerBegin = statement.indexOf("tmp");
//            if (registerBegin == -1) {
//                break;
//            }
//            int registerEnd = statement.substring(registerBegin).indexOf(".");
//
//            registers.add(statement.substring(registerBegin, registerBegin + registerEnd));
//            statement = statement.substring(registerBegin + registerEnd);
//        }

        int assignIndex = statement.indexOf(":=");
        if (assignIndex != -1)
            statement = statement.substring(assignIndex + 2);

        String regex = "\\b([a-zA-Z][a-zA-Z0-9]*)\\.[a-zA-Z0-9_]+\\b";
        Pattern pattern = Pattern.compile(regex);

        Matcher matcher = pattern.matcher(statement);
        ArrayList<String> registerNames = new ArrayList<>();

        while (matcher.find()) {

            String registerName = matcher.group(1);
            if (validRegister(registerName))
                registerNames.add(registerName);
        }

        return registerNames;
    }

    private boolean validRegister(String registerName) {
        if (registerName.startsWith("new"))
            return false;
        if (registerName.startsWith("ret"))
            return false;
        if (registerName.startsWith("array"))
            return false;
        // TODO
//        if (table.getMethodSymbol(methodName).getParams().stream().anyMatch(param -> param.getName().equals(registerName)))
//            return false;

        return true;
    }

    private void updateLiveRanges(String register, Integer line) {

        if (liveRanges.get(register) == null) {
            liveRanges.put(register, new Pair<>(line, line));
        } else {
            liveRanges.put(register, new Pair<>(liveRanges.get(register).a, line));
        }
    }
}
