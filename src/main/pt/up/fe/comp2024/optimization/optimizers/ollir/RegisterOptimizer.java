package pt.up.fe.comp2024.optimization.optimizers.ollir;

import org.antlr.v4.runtime.misc.Pair;
import org.specs.comp.ollir.*;
import org.specs.comp.ollir.tree.TreeNode;
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

    public boolean optimize() {

        if (numRegisters == 0) {
            while (!optimize(++numRegisters)) ;
            return true;
        } else {
            return optimize(numRegisters);
        }
    }

    public boolean optimize(int numRegisters) {

        for (Method method : ollirClass.getMethods()) {
            HashSet<String> declaredRegisters = new HashSet<>();
            HashMap<String, Pair<Integer, Integer>> liveRanges = new HashMap<>();

            HashMap<Integer, HashSet<String>> liveIns = new HashMap<>();
            HashMap<Integer, HashSet<String>> liveOuts = new HashMap<>();
            HashMap<Integer, HashSet<String>> defs = new HashMap<>();
            HashMap<Integer, HashSet<String>> uses = new HashMap<>();

            for (int i = 0; i < method.getInstructions().size(); i++) {
                liveIns.put(i, new HashSet<>());
                liveOuts.put(i, new HashSet<>());
                defs.put(i, new HashSet<>());
                uses.put(i, new HashSet<>());
            }

            for (int line = 0; line < method.getInstructions().size(); line++) {
                Instruction instruction = method.getInstructions().get(line);
                if (instruction instanceof AssignInstruction assignment) {
                    if (assignment.getDest() instanceof Operand assigned) {
                        for (String register : method.getVarTable().keySet()) {
                            if (assigned.getName().equals(register)) {
                                defs.get(line).add(register);
                            }
                        }
                    }
                    for (TreeNode node : assignment.getRhs().getDescendants()) {
                        if (node instanceof Operand operand) {
                            for (String register : method.getVarTable().keySet()) {
                                if (operand.getName().equals(register)) {
                                    uses.get(line).add(register);
                                }
                            }
                        }
                    }
                } else {
                    for (TreeNode node : instruction.getDescendants()) {
                        if (node instanceof Operand operand) {
                            for (String register : method.getVarTable().keySet()) {
                                if (operand.getName().equals(register)) {
                                    uses.get(line).add(register);
                                }
                            }
                        }
                    }
                }
            }
            boolean changed = true;
            while (changed) {

                changed = false;

                for (int line = 0; line < method.getInstructions().size(); line++) {

                    Instruction instruction = method.getInstructions().get(line);
                    HashSet<String> oldLiveIn = new HashSet<>(liveIns.get(line));
                    HashSet<String> oldLiveOut = new HashSet<>(liveOuts.get(line));

                    HashSet<String> newLiveIn = new HashSet<>(liveIns.get(line));
                    HashSet<String> newLiveOut = new HashSet<>(liveOuts.get(line));

                    HashSet<String> previousOut = new HashSet<>(liveOuts.get(line));
                    previousOut.removeAll(defs.get(line));
                    newLiveIn.addAll(previousOut);
                    newLiveIn.addAll(uses.get(line));
                    if (line + 1 < method.getInstructions().size()) {
                        newLiveOut.addAll(liveIns.get(line + 1));
                    }

                    if (!oldLiveIn.equals(newLiveIn) || !oldLiveOut.equals(newLiveOut)) {
                        changed = true;
                    }

                    liveIns.put(line, newLiveIn);
                    liveOuts.put(line, newLiveOut);
                }
            }

            HashSet<Pair<String, String>> interferences = new HashSet<>();

            for (int i = 0; i < method.getInstructions().size(); i++) {
                for (String register : liveIns.get(i)) {
                    for (String otherRegister : liveIns.get(i)) {
                        if (!register.equals(otherRegister)) {
                            interferences.add(new Pair<>(
                                    register,
                                    otherRegister
                            ));
                        }
                    }
                }
                for (String register : liveOuts.get(i)) {
                    for (String otherRegister : liveOuts.get(i)) {
                        if (!register.equals(otherRegister)) {
                            interferences.add(new Pair<>(
                                    register,
                                    otherRegister
                            ));
                        }
                    }
                }
            }

            HashSet<String> registers = new HashSet<>(method.getVarTable().keySet());

            ColorGraph colorGraph = new ColorGraph(registers, interferences);
            HashMap<Integer, HashSet<String>> allocation = colorGraph.paintWithColors(numRegisters);
            if (allocation == null) {
                return false;
            }
            replaceRegisters(method, allocation);
        }
        return true;
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
