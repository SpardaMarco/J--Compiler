package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.symboltable.JmmSymbolTable;
import pt.up.fe.comp2024.symboltable.ParamSymbol;

import java.util.List;

public class IncompatibleArguments extends AnalysisVisitor {

    @Override
    protected void buildVisitor() {
        addVisit("MethodCall", this::visitMethodCall);
    }

    public Void visitMethodCall(JmmNode methodCall, JmmSymbolTable table) {

        List<JmmNode> arguments = methodCall.getChildren().subList(1, methodCall.getNumChildren());

        checkArguments(methodCall, table, arguments);

        return null;
    }

    private void checkArguments(JmmNode methodCall, JmmSymbolTable table, List<JmmNode> arguments) {
        String methodName = methodCall.get("name");

        if (table.getMethodSymbol(methodName) == null)
            return;

        List<ParamSymbol> parameters = table.getMethodSymbol(methodName).getParams();

        if (wrongVarargUse(parameters)) return;

        if (invalidArguments(arguments)) return;

        if (!hasVararg(parameters)) {

            if (parameters.size() != arguments.size()) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(methodCall),
                        NodeUtils.getColumn(methodCall),
                        String.format(
                                "Number of arguments provided does not match with method \"%s()\" declaration.",
                                methodName
                        ),
                        null)
                );
                return;
            }

            checkArgumentsIndividually(arguments, parameters, methodName);
        } else {
            if (arguments.size() < parameters.size() - 1){
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(methodCall),
                        NodeUtils.getColumn(methodCall),
                        String.format(
                                "Number of arguments provided does not match with method \"%s()\" declaration.",
                                methodName
                        ),
                        null)
                );
            } else {

                checkArgumentsIndividually(
                        arguments.subList(0, parameters.size() - 1),
                        parameters.subList(0, parameters.size() - 1),
                        methodName
                );

                ParamSymbol vararg = parameters.get(parameters.size() - 1);
                String varargType = vararg.getType().getName();

                if (hasArrayToVarargCorrespondence(arguments, parameters)){

                    JmmNode array = arguments.get(arguments.size() - 1);
                    String arrayType = array.get("type");

                    if (!arrayType.equals(varargType)) {
                        addReport(Report.newError(
                                Stage.SEMANTIC,
                                NodeUtils.getLine(array),
                                NodeUtils.getColumn(array),
                                String.format(
                                        "Array argument of type %s is incompatible with \"%s()\" method's  vararg parameter of type %s.",
                                        arrayType, methodName, varargType
                                ),
                                null)
                        );
                    }

                } else {

                    int leftoverArguments = arguments.size() - (parameters.size() - 1);

                    for (int j = 0; j < leftoverArguments; j++){

                        int argumentIndex = (parameters.size() - 1) + j;
                        JmmNode argument = arguments.get(argumentIndex);
                        Boolean argumentIsArray = argument.get("isArray").equals("true");
                        String argumentType = argument.get("type") + (argumentIsArray ? "[]" : "");
                        if (!argumentType.equals(varargType) || argumentIsArray) {
                            addReport(Report.newError(
                                    Stage.SEMANTIC,
                                    NodeUtils.getLine(argument),
                                    NodeUtils.getColumn(argument),
                                    String.format(
                                            "Argument of type %s incompatible with \"%s()\" method's vararg of type %s.",
                                            argumentType, methodName, varargType
                                    ),
                                    null)
                            );
                        }
                    }
                }
            }
        }
    }

    private boolean invalidArguments(List<JmmNode> arguments) {

        for (JmmNode argument: arguments) {
            if (argument.get("type").equals("invalid"))
                return true;
        }
        return false;
    }

    private boolean hasArrayToVarargCorrespondence(List<JmmNode> arguments, List<ParamSymbol> parameters) {
        JmmNode lastArgument = arguments.get(arguments.size() - 1);
        boolean isLastArgumentArray = lastArgument.get("isArray").equals("true");

        return parameters.size() == arguments.size() && isLastArgumentArray;
    }

    private void checkArgumentsIndividually(List<JmmNode> arguments, List<ParamSymbol> parameters, String methodName) {
        for (int i = 0; i < parameters.size(); i++) {

            ParamSymbol parameter = parameters.get(i);
            JmmNode argument = arguments.get(i);

            String parameterType =
                    parameter.getType().getName() + (parameter.getType().isArray() ? "[]" : "");
            String argumentType =
                    argument.get("type") + (argument.get("isArray").equals("true") ? "[]" : "");

            if (!parameterType.equals(argumentType)){
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(argument),
                        NodeUtils.getColumn(argument),
                        String.format(
                                "Argument of type %s incompatible with \"%s()\" method's parameter of type %s.",
                                argumentType, methodName, parameterType
                        ),
                        null)
                );
            }
        }
    }

    private boolean hasVararg(List<ParamSymbol> parameters) {
        if (parameters.size() == 0) return false;
        return parameters.get(parameters.size() - 1).isVararg();
    }

    private boolean wrongVarargUse(List<ParamSymbol> parameters) {
        for (int i = 0; i < parameters.size() - 1; i++){
            if (parameters.get(i).isVararg()) {
                return true;
            }
        }
        return false;
    }
}

