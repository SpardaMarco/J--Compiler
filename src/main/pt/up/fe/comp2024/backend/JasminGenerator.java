package pt.up.fe.comp2024.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.tree.TreeNode;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates Jasmin code from an OllirResult.
 * <p>
 * One JasminGenerator instance per OllirResult.
 */
public class JasminGenerator {

    private static final String NL = "\n";
    private static final String TAB = "   ";
    private final OllirResult ollirResult;
    List<Report> reports;
    String code;
    Method currentMethod;
    int stackSize = 99;
    int currentStackSize = 0;
    int jumpIndex = 0;

    private final FunctionClassMap<TreeNode, String> generators;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;

        reports = new ArrayList<>();
        code = null;
        currentMethod = null;

        this.generators = new FunctionClassMap<>();
        generators.put(ClassUnit.class, this::generateClassUnit);

        generators.put(AssignInstruction.class, this::generateAssign);
        generators.put(UnaryOpInstruction.class, this::generateUnaryOp);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(CallInstruction.class, this::generateCall);
        generators.put(ReturnInstruction.class, this::generateReturn);
        generators.put(GotoInstruction.class, this::generateGoto);
        generators.put(GetFieldInstruction.class, this::generateGetField);
        generators.put(PutFieldInstruction.class, this::generatePutField);
        generators.put(SingleOpInstruction.class, this::generateSingleOp);
        generators.put(OpCondInstruction.class, this::generateOpCond);
        generators.put(SingleOpCondInstruction.class, this::generateSingleOpCond);

        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(Operand.class, this::generateOperand);
        generators.put(ArrayOperand.class, this::generateArrayOperand);
        generators.put(Method.class, this::generateMethod);
        generators.put(Field.class, this::generateField);
    }

    public List<Report> getReports() {
        return reports;
    }

    public String build() {

        // This way, build is idempotent
        if (code == null) {
            code = generators.apply(ollirResult.getOllirClass());
        }

        return code;
    }

    private String generateClassUnit(ClassUnit classUnit) {

        var code = new StringBuilder();

        // generate class name
        // .class <access-spec> <class-name>
        var className = classUnit.getClassName();
        code.append(".class ").append(className).append(NL);

        // generate super class
        // .super <super-class-name>
        if (classUnit.getSuperClass() != null) {
            code.append(".super ").append(getFullClassName(classUnit.getSuperClass())).append(NL).append(NL);
        } else {
            code.append(".super java/lang/Object").append(NL).append(NL);
        }

        // generate fields
        // .field <access-spec> <field-name> <descriptor>
        for (var field : classUnit.getFields()) {
            code.append(generators.apply(field));
        }

        // generate a single constructor method
        String defaultConstructor = this.buildDefaultConstructor();

        code.append(defaultConstructor);

        // generate code for all other methods
        // .method <access-spec> <method-spec>
        // .limit stack <max-stack>
        // .limit locals <max-locals>
        // <statements>
        // .end method
        for (var method : classUnit.getMethods()) {

            // Ignore constructor, since there is always one constructor
            // that receives no arguments, and has been already added
            // previously
            if (method.isConstructMethod() || method.getMethodName().equals("<init>")) {
                continue;
            }

            code.append(generators.apply(method));
        }

        return code.toString();
    }

    private String generateAssign(AssignInstruction assign) {
        var code = new StringBuilder();

        // store value in the stack in destination
        var lhs = (Operand) assign.getDest();
        var rhs = assign.getRhs();

        if (lhs instanceof ArrayOperand) {
            code.append(getArrayOperand((ArrayOperand) lhs));
        }
        if (rhs.getInstType() == InstructionType.BINARYOPER && isIincAssignment(assign)) {
            code.append(getIincAssignment((BinaryOpInstruction) rhs));
            return code.toString();
        }

        // generate code for loading what's on the right
        code.append(generators.apply(rhs));

        // get register
        var reg = currentMethod.getVarTable().get(lhs.getName()).getVirtualReg();
        if (lhs.getName().equals("this")) {
            reg = 0;
        }
        switch (lhs.getType().getTypeOfElement()) {
            case INT32, BOOLEAN -> {
                if (currentMethod.getVarTable().get(lhs.getName()).getVarType().getTypeOfElement() == ElementType.ARRAYREF) {
                    code.append("iastore").append(NL);
                    updateStackSize(-3);
                } else {
                    code.append("istore").append(reg < 4 ? "_" : " ").append(reg).append(NL);
                    updateStackSize(-1);
                }
                break;
            }
            case ARRAYREF, OBJECTREF, THIS, STRING -> {
                code.append("astore").append(reg < 4 ? "_" : " ").append(reg).append(NL);
                updateStackSize(-1);
            }
            default -> throw new NotImplementedException(lhs.getType().getTypeOfElement());
        }
        return code.toString();
    }

    private String generateUnaryOp(UnaryOpInstruction unaryOp) {
        var code = new StringBuilder();

        // load value
        code.append(generators.apply(unaryOp.getOperand()));

        // apply operation
        var op = getOperation(unaryOp.getOperation());

        if (unaryOp.getOperation().getOpType() == OperationType.NOTB) {
            updateStackSize(1);
            code.append("iconst_1").append(NL).append(op).append(NL);
            updateStackSize(-1);
        } else {
            code.append(op).append(NL);
        }
        return code.toString();
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        // load values on the left and on the right
        code.append(generators.apply(binaryOp.getLeftOperand()));
        code.append(generators.apply(binaryOp.getRightOperand()));
        String operation = null;
        if (binaryOp.getOperation().getOpType() == OperationType.LTH
                || binaryOp.getOperation().getOpType() == OperationType.LTE
                || binaryOp.getOperation().getOpType() == OperationType.GTH
                || binaryOp.getOperation().getOpType() == OperationType.GTE) {
            operation = getBooleanOperation(binaryOp.getOperation());
        }

        // apply operation
        if (operation == null) {
            code.append(getOperation(binaryOp.getOperation())).append(NL);
        } else {
            code.append(getOperation(new Operation(OperationType.SUB, new Type(ElementType.INT32)))).append(NL);
            code.append(buildJump(operation)).append(NL);
        }

        updateStackSize(-1);

        return code.toString();
    }

    private String generateCall(CallInstruction call) {
        var code = new StringBuilder();

        switch (call.getInvocationType()) {
            case invokespecial -> code.append(getSpecialCall(call));
            case invokestatic -> code.append(getStaticCall(call));
            case invokevirtual -> code.append(getVirtualCall(call));
            case NEW -> code.append(getNewCall(call));
            case arraylength -> code.append(getArrayLengthCall(call));
            case ldc -> {
                var literal = call.getCaller();
                code.append(generators.apply(literal));
            }
            default -> throw new NotImplementedException(call.getInvocationType());
        }

        return code.toString();
    }

    private String generateReturn(ReturnInstruction returnInst) {
        var code = new StringBuilder();

        if (returnInst.hasReturnValue()) {
            code.append(generators.apply(returnInst.getOperand())).append(NL);
            code.append(returnInst.getOperand().getType().getTypeOfElement() == ElementType.INT32
                    || returnInst.getOperand().getType().getTypeOfElement() == ElementType.BOOLEAN ? "ireturn" : "areturn").append(NL);
        } else {
            code.append("return").append(NL);
        }

        return code.toString();
    }

    private String generateGoto(GotoInstruction gotoInst) {
        return "goto " + gotoInst.getLabel() + NL;
    }

    private String generateGetField(GetFieldInstruction getField) {
        var code = new StringBuilder();

        // get object
        var object = getField.getObject();
        code.append(generators.apply(object));

        // get field
        var field = getField.getField();
        var className = getFullClassName(object.getName());

        code.append("getfield ").append(className).append("/").append(field.getName()).append(" ").append(getTypeDescriptor(field.getType())).append(NL);

        return code.toString();
    }

    private String generatePutField(PutFieldInstruction putField) {
        var code = new StringBuilder();

        // get object
        var object = putField.getObject();
        code.append(generators.apply(object));

        // get field
        var field = putField.getField();
        var className = getFullClassName(object.getName());

        code.append(generators.apply(putField.getValue()));

        code.append("putfield ").append(className).append("/").append(field.getName()).append(" ").append(getTypeDescriptor(field.getType())).append(NL);

        updateStackSize(-2);
        return code.toString();
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return generators.apply(singleOp.getSingleOperand());
    }

    private String generateOpCond(OpCondInstruction opCond) {

        var code = new StringBuilder();

        Instruction inst = opCond.getCondition();
        String operation = getConditionOperation(inst);

        switch (inst.getInstType()) {
            case BINARYOPER, UNARYOPER -> {
                code.append(buildConditionCode(inst, operation)).append(NL);
                break;
            }
            default -> throw new NotImplementedException(inst.getInstType());
        }

        code.append(operation).append(" ").append(opCond.getLabel()).append(NL);

        if (operation.equals("if_icmplt")) updateStackSize(-2);
        else updateStackSize(-1);

        return code.toString();
    }

    private String generateSingleOpCond(SingleOpCondInstruction singleOpCond) {
        var code = new StringBuilder();

        Instruction inst = singleOpCond.getCondition(); // UnaryOpInstruction or BinaryOpInstruction

        code.append(generators.apply(inst));

        // add label
        code.append("ifne ").append(singleOpCond.getLabel()).append(NL);

        return code.toString();
    }

    private String generateLiteral(LiteralElement literal) {
        ElementType type = literal.getType().getTypeOfElement();
        updateStackSize(1);
        if (type == ElementType.INT32 || type == ElementType.BOOLEAN) {
            int value = Integer.parseInt(literal.getLiteral());
            if (value >= 0 && value <= 5) {
                return "iconst_" + value + NL;
            } else if (value == -1) {
                return "iconst_m1" + NL;
            } else if (value >= -128 && value <= 127) {
                return "bipush " + value + NL;
            } else if (value >= -32768 && value <= 32767) {
                return "sipush " + value + NL;
            } else {
                return "ldc " + value + NL;
            }
        } else {
            return "ldc " + literal.getLiteral() + NL;
        }
    }

    private String generateMethod(Method method) {

        // set method
        currentMethod = method;

        var code = new StringBuilder();
        // calculate modifier
        String modifier = getAccessModifier(method.getMethodAccessModifier());
        String isStatic = method.isStaticMethod() ? " static " : " ";

        String methodName = method.getMethodName();

        String descriptor = getTypeDescriptor(method.getReturnType());
        StringBuilder params = new StringBuilder();

        // Params
        for (Element param : method.getParams()) {
            params.append(getTypeDescriptor(param.getType()));
        }

        code.append("\n.method ").append(modifier).append(isStatic).append(methodName).append("(").append(params).append(")").append(descriptor).append(NL);

        stackSize = 0;
        currentStackSize = 0;

        var instCode = new StringBuilder();
        for (var inst : method.getInstructions()) {

            var label = method.getLabels(inst);
            if (label != null) {
                for (var l : label) {
                    instCode.append(NL).append(l).append(":");
                }
            }
            instCode.append(StringLines.getLines(generators.apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, NL + TAB, NL)));

            if (inst instanceof CallInstruction call) {
                if (call.getReturnType().getTypeOfElement() != ElementType.VOID) {
                    instCode.append(TAB).append("pop").append(NL);
                    updateStackSize(-1);
                }
            }
        }

        // unset method
        currentMethod = null;
        // Add limits
        code.append(TAB).append(".limit stack ").append(stackSize).append(NL);
        // Add locals limit

        HashSet<Integer> virtualRegs = new HashSet<>();
        for (String reg : method.getVarTable().keySet()) {
            virtualRegs.add(method.getVarTable().get(reg).getVirtualReg());
        }
        var locals = virtualRegs.size();
        if (!method.isStaticMethod()) {
            locals++;
            for (var variable : method.getVarTable().values()) {
                if (variable.getVarType().getTypeOfElement() == ElementType.THIS) {
                    locals--;
                    break;
                }
            }
        }
        code.append(TAB).append(".limit locals ").append(locals).append(NL);
        // Add code
        code.append(instCode);
        code.append(".end method\n");

        return code.toString();
    }

    private String generateField(Field field) {
        var code = new StringBuilder();

        String access = getAccessModifier(field.getFieldAccessModifier());
        String name = field.getFieldName();
        String descriptor = getTypeDescriptor(field.getFieldType());

        code.append(".field ").append(access).append(" ").append(name).append(" ").append(descriptor).append(NL);

        return code.toString();
    }

    private String generateArrayOperand(ArrayOperand operand) {
        var code = new StringBuilder();
        var reg = getReg(operand);
        updateStackSize(1);
        code.append("aload").append(reg < 4 ? "_" : " ").append(reg).append(NL);
        code.append(generators.apply(operand.getIndexOperands().get(0))).append(NL);
        code.append("iaload").append(NL);
        updateStackSize(-1);

        return code.toString();
    }

    private String generateOperand(Operand operand) {
        var code = new StringBuilder();
        var reg = getReg(operand);

        updateStackSize(1);
        switch (operand.getType().getTypeOfElement()) {
            case INT32, BOOLEAN -> {
                code.append("iload").append(reg < 4 ? "_" : " ").append(reg).append(NL);
                break;
            }
            case STRING, ARRAYREF, OBJECTREF -> {
                code.append("aload").append(reg < 4 ? "_" : " ").append(reg).append(NL);
            }
            case THIS -> {
                code.append("aload_0").append(NL);
            }

            default -> throw new NotImplementedException(operand.getType().getTypeOfElement());
        }
        return code.toString();
    }

    private int getReg(Operand operand) {
        var reg = -1;
        if (currentMethod.getVarTable().get(operand.getName()) != null) {
            reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
        } else if (operand.getName().equals("this")) {
            reg = 0;
        }
        return reg;
    }

    private String getAccessModifier(AccessModifier accessModifier) {
        return switch (accessModifier) {
            case PUBLIC -> "public";
            case PRIVATE -> "private";
            case PROTECTED -> "protected";
            case DEFAULT -> "";
            default -> throw new NotImplementedException(accessModifier);
        };
    }

    private String getTypeDescriptor(Type type) {
        return switch (type.getTypeOfElement()) {
            case ARRAYREF -> "[" + getTypeDescriptor(((ArrayType) type).getElementType());
            case OBJECTREF -> {
                String className = getFullClassName(((ClassType) type).getName());
                yield "L" + className + ";";
            }
            default -> switch (type.toString()) {

                case "INT32" -> "I";
                case "BOOLEAN" -> "Z";
                case "STRING" -> "Ljava/lang/String;";
                case "VOID" -> "V";
                default -> throw new NotImplementedException(type);
            };
        };
    }

    private String buildDefaultConstructor() {
        String superClassName = ollirResult.getOllirClass().getSuperClass() == null ? "java/lang/Object" : getFullClassName(ollirResult.getOllirClass().getSuperClass());
        return ".method public <init>()V" + NL +
                TAB + "aload_0" + NL +
                TAB + "invokespecial " + superClassName + "/<init>()V" + NL +
                TAB + "return" + NL +
                ".end method" + NL;
    }

    private String getOperation(Operation operation) {
        return switch (operation.getOpType()) {
            case ADD -> "iadd";
            case SUB, LTH -> "isub";
            case MUL -> "imul";
            case DIV -> "idiv";
            case NOTB -> "ixor";
            case ANDB -> "iand";
            default -> throw new NotImplementedException(operation.getOpType());
        };
    }

    private String getBooleanOperation(Operation operation) {
        return switch (operation.getOpType()) {
            case LTH -> "iflt";
            case LTE -> "ifle";
            case GTH -> "ifgt";
            case GTE -> "ifge";
            case ANDB -> "ifne";
            default -> throw new NotImplementedException(operation.getOpType());
        };
    }

    private String getSpecialCall(CallInstruction call) {
        var code = new StringBuilder();

        var caller = (Operand) call.getCaller();
        var className = "";
        if (caller.getType().getTypeOfElement() == ElementType.THIS) {
            if (ollirResult.getOllirClass().getSuperClass() == null) {
                className = "java/lang/Object";
            } else {
                className = getFullClassName(ollirResult.getOllirClass().getSuperClass());
            }
        } else {
            className = getFullClassName(((ClassType) caller.getType()).getName());
        }
        var methodName = ((LiteralElement) call.getMethodName()).getLiteral().replace("\"", "");

        // load caller
        code.append(generators.apply(call.getCaller()));

        // arguments
        for (var arg : call.getArguments()) {
            code.append(generators.apply(arg));
        }

        code.append("invokespecial ").append(className).append("/").append(methodName).append("(");

        for (var arg : call.getArguments()) {
            code.append(getTypeDescriptor(arg.getType()));
        }

        code.append(")").append(getTypeDescriptor(call.getReturnType())).append(NL);
        updateStackSize(call.getReturnType().getTypeOfElement() == ElementType.VOID ? 0 : -1);

        return code.toString();
    }

    private String getStaticCall(CallInstruction call) {
        var code = new StringBuilder();

        var caller = (Operand) call.getCaller();
        var className = getFullClassName(caller.getName());
        var methodName = ((LiteralElement) call.getMethodName()).getLiteral().replace("\"", "");

        // arguments
        var arguments = 0;
        for (var arg : call.getArguments()) {
            arguments++;
            code.append(generators.apply(arg));
        }

        code.append("invokestatic ").append(className).append("/").append(methodName).append("(");

        for (var arg : call.getArguments()) {
            code.append(getTypeDescriptor(arg.getType()));
        }

        code.append(")").append(getTypeDescriptor(call.getReturnType())).append(NL);
        updateStackSize(call.getReturnType().getTypeOfElement() == ElementType.VOID ? -arguments : -(arguments - 1));

        return code.toString();
    }

    private String getVirtualCall(CallInstruction call) {
        var code = new StringBuilder();

        var caller = (Operand) call.getCaller();
        var className = getFullClassName(((ClassType) caller.getType()).getName());
        var methodName = ((LiteralElement) call.getMethodName()).getLiteral().replace("\"", "");

        // load caller
        code.append(generators.apply(call.getCaller()));

        // arguments
        var arguments = 1;
        for (var arg : call.getArguments()) {
            arguments++;
            code.append(generators.apply(arg));
        }

        code.append("invokevirtual ").append(className).append("/").append(methodName).append("(");

        for (var arg : call.getArguments()) {
            code.append(getTypeDescriptor(arg.getType()));
        }

        code.append(")").append(getTypeDescriptor(call.getReturnType())).append(NL);
        updateStackSize(call.getReturnType().getTypeOfElement() == ElementType.VOID ? -arguments : -(arguments - 1));

        return code.toString();
    }

    private String getNewCall(CallInstruction call) {
        var code = new StringBuilder();

        var caller = (Operand) call.getCaller();

        var arguments = -1;
        for (var arg : call.getArguments()) {
            arguments++;
            code.append(generators.apply(arg));
        }

        if (call.getReturnType() instanceof ArrayType arrayType) {
            code.append("newarray ").append(arrayType.getElementType().getTypeOfElement() == ElementType.INT32 ? "int" : "boolean").append(NL);
        } else {
            var className = getFullClassName(caller.getName());
            updateStackSize(1);
            code.append("new ").append(className).append(NL);
        }

        if (arguments > 0) {
            updateStackSize(-arguments);
        }
        return code.toString();
    }

    private String getArrayOperand(ArrayOperand array) {
        var code = new StringBuilder();
        var reg = getReg(array);
        updateStackSize(1);
        code.append("aload").append(reg < 4 ? "_" : " ").append(reg).append(NL);
        code.append(generators.apply(array.getIndexOperands().get(0))).append(NL);

        return code.toString();
    }

    private boolean isIincAssignment(AssignInstruction inst) {
        var rhs = (BinaryOpInstruction) inst.getRhs();
        var left = rhs.getLeftOperand();
        var right = rhs.getRightOperand();
        var operation = rhs.getOperation().getOpType();
        // Must be something like i = i + 1 or i = i - 1 (this checks for left and right types)
        if (operation != OperationType.ADD && operation != OperationType.SUB) {
            return false;
        }
        // Must be a literal op variable or variable op literal
        if ((left.isLiteral() && right.isLiteral()) || (!left.isLiteral() && !right.isLiteral())) {
            return false;
        }
        var literal = left.isLiteral() ? (LiteralElement) left : (LiteralElement) right;
        var variable = left.isLiteral() ? (Operand) right : (Operand) left;
        if (!variable.getName().equals(((Operand) inst.getDest()).getName())) {
            return false;
        }
        var value = operation == OperationType.ADD ? Integer.parseInt(literal.getLiteral()) : -Integer.parseInt(literal.getLiteral());
        return value >= -128 && value <= 127;
    }

    private String getIincAssignment(BinaryOpInstruction inst) {
        var code = new StringBuilder();
        var left = inst.getLeftOperand();
        var right = inst.getRightOperand();
        var operation = inst.getOperation().getOpType();
        var literal = left.isLiteral() ? (LiteralElement) left : (LiteralElement) right;
        var variable = left.isLiteral() ? (Operand) right : (Operand) left;
        var value = operation == OperationType.ADD ? Integer.parseInt(literal.getLiteral()) : -Integer.parseInt(literal.getLiteral());
        var reg = currentMethod.getVarTable().get(variable.getName()).getVirtualReg();
        code.append("iinc ").append(reg).append(" ").append(value).append(NL);
        return code.toString();
    }

    private String getArrayLengthCall(CallInstruction call) {
        var code = new StringBuilder();

        var caller = (Operand) call.getCaller();

        code.append(generators.apply(caller));

        code.append("arraylength").append(NL);

        return code.toString();
    }

    private String getFullClassName(String className) {
        if (className.equals("this")) {
            return ollirResult.getOllirClass().getClassName();
        } else {
            for (var importClass : ollirResult.getOllirClass().getImports()) {
                var tokens = importClass.split("\\.");
                if (tokens[tokens.length - 1].equals(className)) {
                    return importClass.replaceAll("\\.", "/");
                }
            }
        }
        return className;
    }

    private String getConditionOperation(Instruction inst) {
        String op = "";
        if (inst instanceof BinaryOpInstruction binaryOp) {
            switch (binaryOp.getOperation().getOpType()) {
                case LTH -> {
                    return "iflt";
                }
                case LTE -> {
                    return "ifle";
                }
                case GTH -> {
                    return "ifgt";
                }
                case GTE -> {
                    return "ifge";
                }
                case ANDB -> {
                    return "ifne";
                }
                default -> {
                    throw new NotImplementedException(binaryOp.getOperation().getOpType());
                }
            }
        } else if (inst instanceof UnaryOpInstruction unaryOp) {
            switch (unaryOp.getOperation().getOpType()) {
                case NOTB -> {
                    return "ifeq";
                }
                default -> {
                    throw new NotImplementedException(unaryOp.getOperation().getOpType());
                }
            }
        }
        return op;
    }

    private String buildConditionCode(Instruction inst, String operation) {
        var code = new StringBuilder();

        if (inst instanceof BinaryOpInstruction binaryOp) {
            switch (operation) {
                case "iflt", "ifle", "ifgt", "ifge" -> {
                    code.append(generators.apply(binaryOp.getLeftOperand()));
                    code.append(generators.apply(binaryOp.getRightOperand()));
                    code.append("isub").append(NL);
                }
                case "ifne" -> code.append(generators.apply(binaryOp));
                default -> {
                    code.append(generators.apply(binaryOp.getLeftOperand()));
                    code.append(generators.apply(binaryOp.getRightOperand()));
                }
            }
        } else if (inst instanceof UnaryOpInstruction unaryOp) {
            code.append(generators.apply(unaryOp.getOperand()));
        }

        return code.toString();
    }

    private void updateStackSize(int num) {
        currentStackSize += num;
        stackSize = Math.max(stackSize, currentStackSize);
    }

    private String buildJump(String operation) {
        return "\n" + operation + " jump_" + jumpIndex + NL +
                "iconst_0" + NL +
                "goto end_" + jumpIndex + NL +
                "\njump_" + jumpIndex + ":" + NL +
                "iconst_1" + NL +
                "\nend_" + jumpIndex++ + ":" + NL;
    }
}