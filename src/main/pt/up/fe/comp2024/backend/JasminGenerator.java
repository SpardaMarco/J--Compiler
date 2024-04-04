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

    private final FunctionClassMap<TreeNode, String> generators;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;

        reports = new ArrayList<>();
        code = null;
        currentMethod = null;

        this.generators = new FunctionClassMap<>();
        generators.put(ClassUnit.class, this::generateClassUnit);
        generators.put(Method.class, this::generateMethod);
        generators.put(AssignInstruction.class, this::generateAssign);
        generators.put(SingleOpInstruction.class, this::generateSingleOp);
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(Operand.class, this::generateOperand);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(ReturnInstruction.class, this::generateReturn);
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
        var className = ollirResult.getOllirClass().getClassName();
        code.append(".class ").append(className).append(NL).append(NL);

        if (ollirResult.getOllirClass().getSuperClass() != null) {
            code.append(".super ").append(ollirResult.getOllirClass().getSuperClass()).append(NL);
        }
        else {
            code.append(".super java/lang/Object").append(NL);
        }

        // generate fields
        for (var field : ollirResult.getOllirClass().getFields()) {
            code.append(generators.apply(field));
        }

        // generate a single constructor method
        var defaultConstructor = """
                ;default constructor
                .method public <init>()V
                    aload_0
                    invokespecial java/lang/Object/<init>()V
                    return
                .end method
                """;
        code.append(defaultConstructor);

        // generate code for all other methods
        for (var method : ollirResult.getOllirClass().getMethods()) {

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
            if (params.isEmpty()) params.append(getTypeDescriptor(param.getType()));
            else params.append(" ").append(getTypeDescriptor(param.getType()));
        }

        code.append("\n.method ").append(modifier).append(isStatic).append(methodName).append("(").append(params).append(")").append(descriptor).append(NL);

        // Add limits
        code.append(TAB).append(".limit stack 99").append(NL);
        code.append(TAB).append(".limit locals 99").append(NL);

        for (var inst : method.getInstructions()) {
            var instCode = StringLines.getLines(generators.apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, NL+TAB, NL));

            code.append(instCode);
        }

        code.append(".end method\n");

        // unset method
        currentMethod = null;

        return code.toString();
    }

    private String generateAssign(AssignInstruction assign) {
        var code = new StringBuilder();

        // generate code for loading what's on the right
        code.append(generators.apply(assign.getRhs()));

        // store value in the stack in destination
        var lhs = assign.getDest();

        if (!(lhs instanceof Operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        var operand = (Operand) lhs;

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
                code.append("astore ").append(reg < 4 ? "_" : " ").append(reg).append(NL);
            }
            default -> throw new NotImplementedException(operand.getType().getTypeOfElement());
        }
        return code.toString();
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return generators.apply(singleOp.getSingleOperand());
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

    private String generateOperand(Operand operand) {
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
        switch (operand.getType().getTypeOfElement()) {
            case INT32, BOOLEAN -> {
                return "iload " + reg + NL;
            }
            case ARRAYREF, OBJECTREF -> {
                return "aload " + reg + NL;
            }
            default -> throw new NotImplementedException(operand.getType().getTypeOfElement());
        }
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
            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        };

        code.append(op).append(NL);

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

    private String generateField(Field field) {
        var code = new StringBuilder();

        String access = getAccessModifier(field.getFieldAccessModifier());
        String name = field.getFieldName();
        String descriptor = getTypeDescriptor(field.getFieldType());

        code.append(".field ").append(access).append(" ").append(name).append(" ").append(descriptor).append(NL);

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
            /*case OBJECTREF:
                return "L" + type.getClassName() + ";";*/
        }
        return switch (type.toString()) {

            case "INT32" -> "I";
            case "BOOLEAN" -> "Z";
            case "STRING" -> "Ljava/lang/String;";
            case "VOID" -> "V";
            default -> throw new NotImplementedException(type);
        };
    }

}
