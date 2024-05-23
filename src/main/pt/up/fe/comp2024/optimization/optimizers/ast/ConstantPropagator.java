package pt.up.fe.comp2024.optimization.optimizers.ast;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.symboltable.JmmSymbolTable;

import java.util.HashMap;
import java.util.Stack;

public class ConstantPropagator extends ConstantOptimizer {

    private final HashMap<String, JmmNode> declarations = new HashMap<>();

    private final HashMap<String, Stack<JmmNode>> assignments = new HashMap<>();

    private final HashMap<String, Stack<Boolean>> assignmentsConditioned = new HashMap<>();

    public ConstantPropagator() {
        setDefaultValue(() -> null);
    }

    protected void cleanUp() {
        for (String id : assignments.keySet()) {
            cleanLiteralAssignments(id);
        }
    }

    private void cleanLiteralAssignments(String id) {
        boolean allRemoved = true;
        Stack<Boolean> conditions = assignmentsConditioned.get(id);
        Stack<JmmNode> assignments = this.assignments.get(id);
        while (!assignments.isEmpty()) {
            JmmNode assignment = assignments.pop();
            boolean isConditioned = conditions.pop();
            if (!isConditioned) {
                assignment.detach();
            } else {
                allRemoved = false;
            }
        }
        if (allRemoved) {
            JmmNode declaration = declarations.get(id);
            if (declaration != null)
                declaration.detach();
        }
    }

    @Override
    protected void buildVisitor() {
        addVisit("VarDeclaration", this::visitVarDeclaration);
        addVisit("AssignStmt", this::visitAssignStmt);
        addVisit("Identifier", this::visitIdentifier);
    }

    private Void visitVarDeclaration(JmmNode varDeclaration, JmmSymbolTable table) {

        declarations.put(varDeclaration.get("name"), varDeclaration);
        return null;
    }

    private Void visitAssignStmt(JmmNode assignment, JmmSymbolTable table) {

        JmmNode value = assignment.getChild(0);

        String name = assignment.get("name");

        assignments.putIfAbsent(name, new Stack<>());
        assignments.get(name).push(assignment);
        assignmentsConditioned.putIfAbsent(name, new Stack<>());


        if (isAssignmentConditioned(assignment) || isNotLiteralAssignment(value)) {
            assignmentsConditioned.get(name).push(true);
        } else {
            assignmentsConditioned.get(name).push(false);
        }
        return null;
    }

    private static boolean isNotLiteralAssignment(JmmNode value) {
        return !(value.getKind().equals("IntegerLiteral") || value.getKind().equals("BooleanLiteral"));
    }

    private Void visitIdentifier(JmmNode identifier, JmmSymbolTable table) {
        String id = identifier.get("value");
        if (isValidLiteral(identifier, id)) {
            Stack<JmmNode> assignments = this.assignments.get(id);
            JmmNode value = assignments.peek().getChild(0);
            identifier.replace(value);
            addOptimization();
        }
        return null;
    }

    private boolean isValidLiteral(JmmNode identifier, String id) {
        return assignments.containsKey(id) &&
                !assignmentsConditioned.get(id).peek() &&
                !isConditioned(identifier);
    }

    private boolean isConditioned(JmmNode identifier) {
        return !isPartOfIfCondition(identifier) && refersConditionedAssignment(identifier);
    }

    private boolean isPartOfIfCondition(JmmNode node) {

        if (node.getAncestor("IfStmt").isPresent()) {
            JmmNode ifStmt = node.getAncestor("IfStmt").get();
            return ifStmt.getChild(0).getDescendantsAndSelfStream().anyMatch(n -> n == node);
        }
        return false;
    }

    private boolean refersConditionedAssignment(JmmNode node) {
        if (node.getAncestor("IfStmt").isPresent()) {
            JmmNode ifStmt = node.getAncestor("IfStmt").get();
            if (ifStmt.getDescendants("AssignStmt").stream().anyMatch(
                    n -> n.get("name").equals(node.get("value"))
            )) {
                return true;
            }
        }
        if (node.getAncestor("WhileStmt").isPresent()) {
            JmmNode whileStmt = node.getAncestor("WhileStmt").get();
            return whileStmt.getDescendants("AssignStmt").stream().anyMatch(
                    n -> n.get("name").equals(node.get("value"))
            );
        }
        return false;
    }

    private boolean isAssignmentConditioned(JmmNode node) {

        return node.getAncestor("IfStmt").isPresent() || node.getAncestor("WhileStmt").isPresent();
    }
}
