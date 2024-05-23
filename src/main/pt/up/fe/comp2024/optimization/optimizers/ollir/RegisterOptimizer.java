package pt.up.fe.comp2024.optimization.optimizers.ollir;

import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp2024.symboltable.JmmSymbolTable;

public class RegisterOptimizer {

    OllirResult ollirResult;
    Integer numRegisters;

    public RegisterOptimizer(OllirResult ollirResult, Integer numRegisters) {
        this.ollirResult = ollirResult;
        this.numRegisters = numRegisters;
    }

    public OllirResult optimize() {

        String optimizedCode = optimizeCode(ollirResult.getOllirCode());

        if (optimizedCode == null) {
//            return new OllirResult(null, ollirResult.getOllirCode(), List.of(
//                    new Report(
//                            ReportType.ERROR,
//                            OPTIMIZATION,
//                            0,
//                            0,
//                            "Register allocation failed for " + numRegisters + " registers."
//                    )
//            ));
            throw new RuntimeException("Register allocation failed for " + numRegisters + " registers.");
        }
        return new OllirResult(optimizedCode, ollirResult.getConfig());
    }

    private String optimizeCode(String ollirCode) {

        StringBuilder stringBuilder = new StringBuilder();

        int classBegin = ollirCode.indexOf("{") + 1;
        stringBuilder.append(ollirCode, 0, classBegin);
        ollirCode = ollirCode.substring(classBegin);

        while (true) {

            String methodName = null;

            int begin = ollirCode.indexOf("{");
            if (begin == -1) break;
            int end = begin + ollirCode.substring(begin).indexOf("}");

            String prefix = ollirCode.substring(0, begin + 1);
            String method = ollirCode.substring(begin + 1, end);

            String optimizedMethod = new MethodRegisterOptimizer(
                    methodName,
                    method,
                    (JmmSymbolTable) ollirResult.getSymbolTable()
            ).optimize(numRegisters);

            if (optimizedMethod == null)
                return null;

            stringBuilder.append(prefix);
            stringBuilder.append(optimizedMethod);

            ollirCode = ollirCode.substring(end);
        }

        stringBuilder.append(ollirCode);

        return stringBuilder.toString();
    }
}
