package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;

import static pt.up.fe.comp2024.ast.Kind.PRIMITIVE_TYPE;

public class OptUtils {
    private static int tempNumber = -1;

    public static String getTemp() {
        return getTemp("tmp");
    }

    public static String getTemp(String prefix) {
        return prefix + getNextTempNum();
    }

    public static int getNextTempNum() {
        tempNumber += 1;
        return tempNumber;
    }

    public static int getCurrentTempNum() {
        return tempNumber;
    }

    public static String toOllirType(JmmNode typeNode) {
        PRIMITIVE_TYPE.checkOrThrow(typeNode);

        String typeName = typeNode.getChildren().get(0).get("name");

        return toOllirType(typeName);
    }

    public static String toOllirType(Type type) {
        String ollirType = "";
        if (type.isArray()) ollirType += ".array";
        return ollirType + toOllirType(type.getName());
    }

    private static String toOllirType(String typeName) {
        String type = "." + switch (typeName) {
            case "int" -> "i32";
            case "boolean" -> "bool";
            case "void" -> "V";
            default -> typeName;
        };

        return type;
    }
}
