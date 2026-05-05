package analyzer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Modelo de datos: representa un token léxico.
 * Contiene el lexema, su clasificación (Tipo), línea y columna de origen.
 */

public class Token {
    public static final Map<String, Integer> map = new HashMap<>();
    static {
        map.put("null", -68);
        map.put("true", -69);
        map.put("false", -70);
        map.put("any", -71);
        map.put("break", -72);
        map.put("charAt", -73);
        map.put("class", -74);
        map.put("concat", -75);
        map.put("console.log", -76);
        map.put("const", -77);
        map.put("continue", -78);
        map.put("do", -79);
        map.put("else", -80);
        map.put("endsWith", -81);
        map.put("filter", -82);
        map.put("find", -83);
        map.put("findIndex", -84);
        map.put("for", -85);
        map.put("forEach", -86);
        map.put("get", -87);
        map.put("if", -88);
        map.put("in", -89);
        map.put("Includes", -90);
        map.put("indexOf", -91);
        map.put("interface", -92);
        map.put("length", -93);
        map.put("let", -94);
        map.put("map", -95);
        map.put("of", -96);
        map.put("push", -97);
        map.put("replace", -98);
        map.put("reverse", -99);
        map.put("set", -100);
        map.put("shift", -101);
        map.put("slice", -102);
        map.put("sort", -103);
        map.put("splice", -104);
        map.put("split", -105);
        map.put("startsWith", -106);
        map.put("switch", -107);
        map.put("toLowerCase", -108);
        map.put("toUpperCase", -109);
        map.put("trim", -110);
        map.put("typeof", -111);
        map.put("undefined", -112);
        map.put("while", -113);
    }
    public enum Tipo {
        KEYWORD, ID, NUMBER, STRING, COMMENT,
        OPERATOR, ASSIGN,
        OP_PAR, CL_PAR, OP_BRAC, CL_BRAC, OP_CURL, CL_CURL,
        COLON, COMMA, DOT, UNKNOWN
    }

    private final String lexema;
    private final Tipo   tipo;
    private final int    linea;
    private final int    columna;
    private final int    tokenClass; // Nueva propiedad para almacenar la clase del token

    public Token(String lexema, Tipo tipo, int linea, int columna, int tokenClass) {
        this.lexema  = lexema;
        this.tipo    = tipo;
        this.linea   = linea;
        this.columna = columna;
        this.tokenClass = tokenClass;
    }

    public String getLexema()  { return lexema;  }
    public Tipo   getTipo()    { return tipo;     }
    public int    getLinea()   { return linea;    }
    public int    getColumna() { return columna;  }
    public int    getTokenClass() { return tokenClass; }

    @Override
    public String toString() {
        return String.format("Token{lexema='%s', tipo=%s, ln=%d, col=%d}",
                lexema, tipo, linea, columna);
    }

    public Object getEstado() {
        return tokenClass;
    }

    public static int clasificar(String c) {
    if (c == null) return 511;

    c = c.trim();

    if (!map.containsKey(c)) {
        return 511; 
    }
    return map.get(c);
}

    public static void eliminarComentarios(List<Token> tokens) {
        tokens.removeIf(token -> token.getTokenClass() == -51);
        tokens.removeIf(token -> token.getTokenClass() == -52);
    }
}
