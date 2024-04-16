package pt.up.fe.comp.ramos_tests;

import org.junit.Test;
import pt.up.fe.comp.TestUtils;
import pt.up.fe.specs.util.SpecsIo;

public class SemanticAnalysisTest {
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
    public void ImportedClassAttributeAccess() {
        var result = TestUtils.analyse(SpecsIo.getResource(
                "pt/up/fe/comp/ramos_tests/ArrayAccessOnIntVar.jmm"
        ));
        System.out.println(result.getReports());
        TestUtils.mustFail(result);
    }
}
