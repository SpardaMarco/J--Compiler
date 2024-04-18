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
}
