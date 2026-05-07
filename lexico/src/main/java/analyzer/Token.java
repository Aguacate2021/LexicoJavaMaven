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
        map.put("main", -68);
        map.put("reg", -69);
        map.put("var", -70);
        map.put("def", -71);
        map.put("true", -72);
        map.put("false", -73);
        map.put("null", -74);
        map.put("CLEAR", -75);
        map.put("SQRT", -76);
        map.put("POW", -77);
        map.put("SQRTV", -78);
        map.put("STRLEN", -79);
        map.put("concat", -80);
        map.put("copy", -81);
        map.put("val", -82);
        map.put("str", -83);
        map.put("sin", -84);
        map.put("cos", -85);
        map.put("tan", -86);
        map.put("chr", -87);
        map.put("pred", -88);
        map.put("succ", -89);
        map.put("inc", -90);
        map.put("dec", -91);
        map.put("sqr", -92);
        map.put("Console.read", -93);
        map.put("Console.log", -94);
        map.put("while", -95);
        map.put("do", -96);
        map.put("return", -97);
        map.put("for", -98);
        map.put("switch", -99);
        map.put("elseif", -100);
        map.put("else", -101);
        map.put("case", -102);
        map.put("default", -103);
        map.put("break", -104);
        map.put("if", -105);
        //NO PONER EL -106 CONTINUA CON EL -107
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
