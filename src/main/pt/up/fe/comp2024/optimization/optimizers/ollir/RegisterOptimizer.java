package pt.up.fe.comp2024.optimization.optimizers.ollir;

import org.antlr.v4.runtime.misc.Pair;
import org.specs.comp.ollir.ClassUnit;
import org.specs.comp.ollir.Descriptor;
import org.specs.comp.ollir.Instruction;
import org.specs.comp.ollir.Method;
import pt.up.fe.comp.jmm.ollir.OllirResult;

import java.util.HashMap;
import java.util.HashSet;

public class RegisterOptimizer {

    ClassUnit ollirClass;
    Integer numRegisters;

    public RegisterOptimizer(OllirResult ollirResult, Integer numRegisters) {
        this.ollirClass = ollirResult.getOllirClass();
        this.numRegisters = numRegisters;
    }

    public void optimize() {

        for (Method method : ollirClass.getMethods()) {
            HashSet<String> declaredRegisters = new HashSet<>();
            HashMap<String, Pair<Integer, Integer>> liveRanges = new HashMap<>();
            for (String register : method.getVarTable().keySet()) {
                String type = method.getVarTable().get(register).getVarType().toString();
                String registerName = register + "." + type;
                int line = 0;
                for (Instruction instruction : method.getInstructions()) {
                    String instructionString = instruction.toString();
                    if (instructionString.contains(registerName)) {
                        if (!declaredRegisters.contains(register)) {
                            declaredRegisters.add(register);
                        } else {
                            liveRanges.putIfAbsent(register, new Pair<>(line, line));
                            liveRanges.put(register, new Pair<>(liveRanges.get(register).a, line));
                        }
                    }
                    line++;
                }
            }
            ColorGraph colorGraph = new ColorGraph(liveRanges);
            HashMap<Integer, HashSet<String>> allocation = colorGraph.paintWithColors(numRegisters);
            if (allocation != null) {
                replaceRegisters(method, allocation);
            }
        }
    }

    private void replaceRegisters(Method method, HashMap<Integer, HashSet<String>> allocation) {
        for (Integer optRegIndex : allocation.keySet()) {
            for (String reg : allocation.get(optRegIndex)) {
                Descriptor descriptor = method.getVarTable().get(reg);
                descriptor.setVirtualReg(optRegIndex + 1);
            }
        }
    }
}
