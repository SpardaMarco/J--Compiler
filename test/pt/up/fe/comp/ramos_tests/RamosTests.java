package pt.up.fe.comp.ramos_tests;

import org.junit.Test;
import org.specs.comp.ollir.*;
import pt.up.fe.comp.TestUtils;
import pt.up.fe.specs.util.SpecsIo;

import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;
import static pt.up.fe.comp.cp2.OllirTest.testJmmCompilation;

public class RamosTests {
    @Test
    public void StaticalMethodAttributeAccess() {
        var result = TestUtils.analyse(SpecsIo.getResource(
                "pt/up/fe/comp/ramos_tests/StaticalMethodAttributeAccess.jmm"
        ));
        System.out.println(result.getReports());
        TestUtils.mustFail(result);
    }

    @Test
    public void StaticalClassAttributeAccess() {
        var result = TestUtils.analyse(SpecsIo.getResource(
                "pt/up/fe/comp/ramos_tests/StaticalClassAttributeAccess.jmm"
        ));
        System.out.println(result.getReports());
        TestUtils.mustFail(result);
    }

    @Test
    public void compileArithmetic() {
        testJmmCompilation("pt/up/fe/comp/ramos_tests/ReturnBinaryOpWithField.jmm", this::compileArithmetic);
    }

    public void compileArithmetic(ClassUnit classUnit) {
        // Test name of the class
        assertEquals("Class name not what was expected", "CompileArithmetic", classUnit.getClassName());

        // Test foo
        var methodName = "foo";
        Method methodFoo = classUnit.getMethods().stream()
                .filter(method -> method.getMethodName().equals(methodName))
                .findFirst()
                .orElse(null);

        assertNotNull("Could not find method " + methodName, methodFoo);

        var binOpInst = methodFoo.getInstructions().stream()
                .filter(inst -> inst instanceof AssignInstruction)
                .map(instr -> (AssignInstruction) instr)
                .filter(assign -> assign.getRhs() instanceof BinaryOpInstruction)
                .findFirst();

        assertTrue("Could not find a binary op instruction in method " + methodName, binOpInst.isPresent());

        var retInst = methodFoo.getInstructions().stream()
                .filter(inst -> inst instanceof ReturnInstruction)
                .findFirst();
        assertTrue("Could not find a return instruction in method " + methodName, retInst.isPresent());

    }
}
