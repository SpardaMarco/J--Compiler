package pt.up.fe.comp2024.optimization.optimizers.ollir;

public class OllirMethodStream {

    private final String method;

    private String stream;

    public OllirMethodStream(String method) {
        this.method = method;
        resetStream();
    }

    public void resetStream() {
        stream = method;
    }

    public String nextStatement() {

        int stmtBegin = 0;
        int stmtEnd = stream.indexOf(";");

        if (stmtEnd == -1) {
            return null;
        }


        String statement = stream.substring(stmtBegin, stmtEnd + 1);
        stream = stream.substring(stmtEnd + 1);

        return statement;
    }

    public boolean isEmpty() {
        return stream.isEmpty();
    }
}
