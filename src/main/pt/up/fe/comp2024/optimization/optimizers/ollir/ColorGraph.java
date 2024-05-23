package pt.up.fe.comp2024.optimization.optimizers.ollir;

import org.antlr.v4.runtime.misc.Pair;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Stack;

public class ColorGraph {

    private final HashSet<String> registers = new HashSet<>();

    private final HashSet<Pair<String, String>> edges = new HashSet<>();

    ColorGraph(HashMap<String, Pair<Integer, Integer>> liveRanges) {
        registers.addAll(liveRanges.keySet());

        setInterferenceEdges(liveRanges);
    }

    private void setInterferenceEdges(HashMap<String, Pair<Integer, Integer>> liveRanges) {
        for (String register : registers) {
            for (String otherRegister : registers) {
                if (!register.equals(otherRegister)) {

                    Pair<Integer, Integer> liveRange1 = liveRanges.get(register);
                    Pair<Integer, Integer> liveRange2 = liveRanges.get(otherRegister);

                    if (interferenceBetween(liveRange1, liveRange2)) {
                        edges.add(new Pair<>(register, otherRegister));
                    }
                }
            }
        }
    }

    private boolean interferenceBetween(Pair<Integer, Integer> liveRange1, Pair<Integer, Integer> liveRange2) {

        return liveRange1.a <= liveRange2.b && liveRange1.b >= liveRange2.a;
    }

    public HashMap<Integer, HashSet<String>> paintWithColors(int numColors) {

        HashMap<String, Integer> colors = new HashMap<>();
        Stack<String> registersToPaint = new Stack<>();
        HashSet<String> spills = new HashSet<>();

        registers.forEach(registersToPaint::push);

        while (!registersToPaint.isEmpty()) {
            String register = registersToPaint.pop();

            if (!hasDegreeLessThan(numColors, register)) {
                spills.add(register);
                continue;
            }
            for (int color = 0; color < numColors; color++) {

                if (canPaintRegWith(register, color, colors, spills)) {
                    colors.put(register, color);
                    break;
                }
            }
            if (colors.get(register) == null) {
                return null;
            }
        }

        for (String spill : spills) {
            for (int color = 0; color < numColors; color++) {
                if (canPaintRegWith(spill, color, colors, new HashSet<>())) {
                    colors.put(spill, color);
                    break;
                }
            }
            if (colors.get(spill) == null) {
                return null;
            }
        }

        HashMap<Integer, HashSet<String>> coloredRegisters = new HashMap<>();

        colors.forEach((register, color) -> {
            coloredRegisters.putIfAbsent(color, new HashSet<>());
            coloredRegisters.get(color).add(register);
        });

        return coloredRegisters;
    }

    private boolean canPaintRegWith(
            String register, int color, HashMap<String, Integer> colors, HashSet<String> spills
    ) {
        return edges.stream().noneMatch(edge ->
                edge.a.equals(register) &&
                        colors.get(edge.b) != null &&
                        colors.get(edge.b) == color &&
                        !spills.contains(edge.b)
        );
    }

    private boolean hasDegreeLessThan(int degree, String register) {
        return edges.stream().filter(edge -> edge.a.equals(register)).count() < degree;
    }
}
