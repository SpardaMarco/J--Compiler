package pt.up.fe.comp2024.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.tree.TreeNode;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.ArrayList;
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
        var className = classUnit.getClassName();
        code.append(".class ").append(className).append(NL);

        if (classUnit.getSuperClass() != null) {
            code.append(".super ").append(getFullClassName(classUnit.getSuperClass())).append(NL).append(NL);
        }
        else {
            code.append(".super java/lang/Object").append(NL).append(NL);
        }

        // generate fields
        for (var field : classUnit.getFields()) {
            code.append(generators.apply(field));
        }

        // generate a single constructor method
        String defaultConstructor = this.buildDefaultConstructor();

        code.append(defaultConstructor);

        // generate code for all other methods
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

        // generate code for loading what's on the right
        code.append(generators.apply(assign.getRhs()));

        // store value in the stack in destination
        var lhs = assign.getDest();

        if (!(lhs instanceof Operand operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        // get register
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
        switch (operand.getType().getTypeOfElement()) {
            case INT32, BOOLEAN -> {
                // TODO: check if this is correct
                if (operand instanceof ArrayOperand) {
                    code.append("iastore").append(NL);
                }
                else {
                    code.append("istore").append(reg < 4 ? "_" : " ").append(reg).append(NL);
                }
                break;
            }
            case ARRAYREF, OBJECTREF -> {
                code.append("astore").append(reg < 4 ? "_" : " ").append(reg).append(NL);
            }
            default -> throw new NotImplementedException(operand.getType().getTypeOfElement());
        }
        return code.toString();
    }

    private String generateUnaryOp(UnaryOpInstruction unaryOp) {
        var code = new StringBuilder();

        // load value
        code.append(generators.apply(unaryOp.getOperand()));

        // apply operation
        var op = getOperation(unaryOp.getOperation());

        // if it's a boolean operation, we expect the caller to add the label
        if (unaryOp.getOperation().getOpType() == OperationType.NOTB) {
            code.append(op).append(" ");
        }
        else {
            code.append(op).append(NL);
        }
        return code.toString();
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        // load values on the left and on the right
        code.append(generators.apply(binaryOp.getLeftOperand()));
        code.append(generators.apply(binaryOp.getRightOperand()));

        // apply operation
        var op = switch (binaryOp.getOperation().getOpType()) {
            case ADD -> "iadd";
            case MUL -> "imul";
            case SUB -> "isub";
            case DIV -> "idiv";
            case LTH -> "if_icmplt";
            case NOTB -> "ifeq";
            case ANDB -> "iand";

            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        };

        code.append(op).append(NL);

        return code.toString();
    }

    private String generateCall(CallInstruction call) {
        var code = new StringBuilder();

        switch (call.getInvocationType()) {
            case invokevirtual, invokespecial, invokestatic -> code.append(getNormalCall(call));
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
        }
        else {
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

        return code.toString();
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return generators.apply(singleOp.getSingleOperand());
    }

    private String generateOpCond(OpCondInstruction opCond) {
        // TODO: NOT ARITHMETIC
        throw new NotImplementedException(opCond);

//        var code = new StringBuilder();
//
//        Instruction inst = opCond.getCondition();
//
//        // assumes that code ended with conditional operation + " "
//        code.append(generators.apply(inst));
//        // add label
//        code.append(opCond.getLabel()).append(NL);
//
//        return code.toString();
    }

    private String generateSingleOpCond(SingleOpCondInstruction singleOpCond) {
        // TODO: NOT ARITHMETIC
        throw new NotImplementedException(singleOpCond);

//        var code = new StringBuilder();
//
//        Instruction inst = singleOpCond.getCondition();
//
//        // assumes that code ended with conditional operation + " "
//        code.append(generators.apply(inst));
//        // add label
//        code.append(singleOpCond.getLabel()).append(NL);
//
//        return code.toString();
    }

    private String generateLiteral(LiteralElement literal) {
        ElementType type = literal.getType().getTypeOfElement();
        if (type == ElementType.INT32 || type == ElementType.BOOLEAN) {
            int value = Integer.parseInt(literal.getLiteral());
            if (value >= 0 && value <= 5) {
                return "iconst_" + value + NL;
            }
            else if (value == -1) {
                return "iconst_m1" + NL;
            }
            else if (value >= -128 && value <= 127) {
                return "bipush " + value + NL;
            }
            else if (value >= -32768 && value <= 32767) {
                return "sipush " + value + NL;
            }
            else {
                return "ldc " + value + NL;
            }
        }
        else {
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

        var instCode = new StringBuilder();
        for (var inst : method.getInstructions()) {
            instCode.append(StringLines.getLines(generators.apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, NL+TAB, NL)));

            if (inst instanceof CallInstruction call) {
                if (call.getReturnType().getTypeOfElement() != ElementType.VOID) {
                    instCode.append(TAB).append("pop").append(NL);
                }
            }
        }

        // unset method
        currentMethod = null;
        // Add limits
        code.append(TAB).append(".limit stack ").append(stackSize).append(NL);
        // Add locals limit
//        var locals = method.getVarTable().size();
//        if (!method.isStaticMethod() && method.getVarTable().get("this") == null) {
//            locals++;
//        }
        var locals = 99;
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

    private String generateOperand(Operand operand) {
        var code = new StringBuilder();
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
        switch (operand.getType().getTypeOfElement()) {
            case INT32, BOOLEAN -> {
                if (operand instanceof ArrayOperand) {
                    code.append("iaload").append(NL);
                }
                else {
                    code.append("iload").append(reg < 4 ? "_" : " ").append(reg).append(NL);
                }
                break;
            }
            case ARRAYREF, OBJECTREF -> {
                code.append("aload").append(reg < 4 ? "_" : " ").append(reg).append(NL);
            }
            case THIS -> {
                code.append("aload_0").append(NL);
            }

            default -> throw new NotImplementedException(operand.getType().getTypeOfElement());
        }
        return code.toString();
    }

    private String getAccessModifier(AccessModifier accessModifier) {
        return accessModifier != AccessModifier.DEFAULT ?
                accessModifier.name().toLowerCase():
                "";
    }

    private String getTypeDescriptor(Type type) {
        switch (type.getTypeOfElement()) {
            case ARRAYREF:
                return "[" + getTypeDescriptor(((ArrayType) type).getElementType());
            case OBJECTREF:
                String className = getFullClassName(((ClassType) type).getName());
                return "L" + className + ";";
        }
        return switch (type.toString()) {

            case "INT32" -> "I";
            case "BOOLEAN" -> "Z";
            case "STRING" -> "Ljava/lang/String;";
            case "VOID" -> "V";
            default -> throw new NotImplementedException(type);
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
            case SUB -> "isub";
            case MUL -> "imul";
            case DIV -> "idiv";
            case LTH -> "if_icmplt";
            case NOTB -> "ifeq";
            case ANDB -> "iand";
            default -> throw new NotImplementedException(operation.getOpType());
        };
    }

    private String getNormalCall(CallInstruction call) {
        var code = new StringBuilder();

        var caller = (Operand) call.getCaller();
        var className = "";
        if (call.getInvocationType() == CallType.invokespecial) {
            var callerType = caller.getType();
            if (callerType.getTypeOfElement() == ElementType.THIS) {
                if (ollirResult.getOllirClass().getSuperClass() == null) {
                    className = "java/lang/Object";
                }
                else {
                    className = getFullClassName(ollirResult.getOllirClass().getSuperClass());
                }
            }
            else {
                className = getFullClassName(((ClassType) callerType).getName());
            }
        }
        else if (call.getInvocationType() == CallType.invokevirtual) {
            var callerType = caller.getType();
            className = getFullClassName(((ClassType) callerType).getName());
        }
        else {
            className = getFullClassName(caller.getName());
        }
        var methodName = ((LiteralElement) call.getMethodName()).getLiteral().replace("\"", "");

        // load caller
        if (call.getInvocationType() != CallType.invokestatic) {
            code.append(generators.apply(call.getCaller()));
        }
        // arguments
        for (var arg : call.getArguments()) {
            code.append(generators.apply(arg));
        }

        code.append(call.getInvocationType().toString().toLowerCase()).append(" ").append(className).append("/").append(methodName).append("(");

        for (var arg : call.getArguments()) {
            code.append(getTypeDescriptor(arg.getType()));
        }

        code.append(")").append(getTypeDescriptor(call.getReturnType())).append(NL);

        return code.toString();
    }

    private String getNewCall(CallInstruction call) {
        var code = new StringBuilder();

        var caller = (Operand) call.getCaller();

        for (var arg : call.getArguments()) {
            code.append(generators.apply(arg));
        }

        if (call.getReturnType() instanceof ArrayType arrayType) {
            code.append("newarray ").append(arrayType.getElementType().getTypeOfElement() == ElementType.INT32 ? "int" : "boolean").append(NL);
        }
        else {
            var className = getFullClassName(caller.getName());
            code.append("new ").append(className).append(NL);
        }

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
        }
        else {
            for (var importClass : ollirResult.getOllirClass().getImports()) {
                var tokens = importClass.split("\\.");
                if (tokens[tokens.length - 1].equals(className)) {
                    return importClass.replaceAll("\\.", "/");
                }
            }
        }
        return className;
    }
}
