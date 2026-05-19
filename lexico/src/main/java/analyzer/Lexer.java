package analyzer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Analizador léxico para código ManuelCode2026.
 *
 * Responsabilidades:
 *   - tokenizar(String) → Lista de Token
 *   - analizar(String)  → Lista de ErrorEntry
 *
 * 
 */
public class Lexer {
    List<ErrorEntry> errores = new ArrayList<>();
    // ── Palabras clave ───────────────────────────────────────────
    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
       "reg", 
        "main", 
        "var", 
        "def", 
        "true", 
        "false",
        "null", 
        "CLEAR", 
        "SQRT", 
        "POW", 
        "SQRTV", 
        "STRLEN", 
        "concat", 
        "copy", 
        "val", 
        "str", 
        "sin", 
        "cos", 
        "tan", 
        "chr", 
        "pred", 
        "succ", 
        "inc", 
        "dec", 
        "sqr", 
        "Console.read", 
        "Console.log", 
        "while", 
        "do", 
        "return", 
        "for", 
        "switch", 
        "elseif", 
        "else", 
        "case", 
        "default", 
        "break", 
        "if"
    ));

    // ════════════════════════════════════════════════════════════════════════
    // TOKENIZACIÓN
    // ════════════════════════════════════════════════════════════════════════

        public List<Token> tokenizar(String codigo) {
        List<Token> tokens = new ArrayList<>();
        errores.clear();
        if (codigo == null || codigo.isBlank()) return tokens;
        LeerCSV.LeerCSV();
        String[] lineas = codigo.split("\n", -1);
        int estado = 0;
        boolean enComentarioMultilinea = false;
        StringBuilder comentario = new StringBuilder();
        for (int numLinea = 0; numLinea < lineas.length; numLinea++) {
            String linea = lineas[numLinea];
            int col = 0;
            while (col < linea.length()) {
                char ch = linea.charAt(col);
                // ===== COMENTARIO MULTILÍNEA =====
                if (enComentarioMultilinea) {
                    comentario.append(ch);
                    if (terminaComentario(comentario)) {
                        tokens.add(crearToken(comentario.toString(), numLinea, col, -51));
                        comentario.setLength(0);
                        enComentarioMultilinea = false;
                    }
                    col++;
                    continue;
                }
                // ===== INICIO COMENTARIO =====
                if (esInicioComentario(linea, col)) {
                    enComentarioMultilinea = true;
                    comentario.append("/*");
                    col += 2;
                    continue;
                }
                StringBuilder lexema = new StringBuilder();
                while (col < linea.length()) {
                    ch = linea.charAt(col);
                    int clase = LeerCSV.clasificar(ch);

                    if (clase < 0) {
                        col++;
                        break;
                    }
                    int nuevoEstado = LeerCSV.getValor(estado, clase);
                    if (esTransicion(nuevoEstado)) {
                        estado = nuevoEstado;
                        lexema.append(ch);
                        col++;
                    } 
                    else {
                        procesarEstado(nuevoEstado, lexema, ch, numLinea, col, tokens);
                        estado = 0;
                        break;
                    }
                }
                // ===== EOF =====
                if (lexema.length() > 0 && col >= linea.length()) {
                    procesarEOF(lexema, estado, numLinea, col, tokens);
                    estado = 0;
                }
                if (lexema.length() == 0) col++;
            }
        }
        // ===== ERROR COMENTARIO SIN CERRAR =====
        if (enComentarioMultilinea) {
            analizar(comentario.toString(), 512, lineas.length, lineas[lineas.length - 1].length() + 1);
        }
        return tokens;
    }
    // ═══════════════════════════METODOS AUXILIARES═════════════════════════════════════════════
    // ANÁLISIS DE ERRORES
    // ════════════════════════════════════════════════════════════════════════
private boolean terminaComentario(StringBuilder comentario) {
    int len = comentario.length();
    return len >= 2 &&
           comentario.charAt(len - 2) == '*' &&
           comentario.charAt(len - 1) == '/';
}

private boolean esInicioComentario(String linea, int col) {
    return linea.charAt(col) == '/' &&
           col + 1 < linea.length() &&
           linea.charAt(col + 1) == '*';
}

private boolean esTransicion(int estado) {
    return estado >= 0 && estado < 500;
}

private Token crearToken(String lexema, int linea, int col, int estado) {
    return new Token(
        lexema,
        Token.Tipo.UNKNOWN,
        linea + 1,
        col - lexema.length() + 1,
        estado
    );
}
//MANEJO CENTRALIZADO DE ESTADOS
private void procesarEstado(int estado, StringBuilder lexema, char ch,
                            int numLinea, int col, List<Token> tokens) {

    int linea = numLinea + 1;
    int posicion = col - lexema.length() + 1;

    if (estado < 0) {
    if (estado >= 500 || estado == 511) {
        manejarErrorGeneral(lexema, ch, estado, linea, posicion);
        return;
    }

    if (estado == -68) {
        manejarError68(lexema, ch, linea, posicion, tokens);
    } else {
        tokens.add(new Token(
            lexema.toString(),
            Token.Tipo.UNKNOWN,
            linea,
            posicion,
            estado
        ));
    }
}
    else if (estado >= 500) {
        manejarErrorGeneral(lexema, ch, estado, linea, posicion);
    }
}
//Manejo limpio de errores
private void manejarError68(StringBuilder lexema, char ch, int linea, int pos, List<Token> tokens) {
    int clasificacion = Token.clasificar(lexema.toString());
    if (clasificacion != 511) {
        tokens.add(new Token(
            lexema.toString(),
            Token.Tipo.KEYWORD,
            linea,
            pos,
            clasificacion
        ));
    } 
    else {
        if (lexema.length() > 0) lexema.append(ch);

        analizar(
            lexema.length() > 0 ? lexema.toString() : String.valueOf(ch),
            511,
            linea,
            pos
        );
    }
}

private void manejarErrorGeneral(StringBuilder lexema, char ch,
                                 int estado, int linea, int pos) {

    if (estado == -68) estado = 510;

    if (lexema.length() > 0) {
        lexema.append(ch);
        analizar(lexema.toString(), estado, linea, pos);
    } else {
        analizar(String.valueOf(ch), estado, linea, pos);
    }
}
private void procesarEOF(StringBuilder lexema, int estado,
                         int numLinea, int col, List<Token> tokens) {
    int linea = numLinea + 1;
    int posicion = col - lexema.length() + 1;
    int claseEOF = 56;
    int nuevoEstado = LeerCSV.getValor(estado, claseEOF);
    if (nuevoEstado == -68) {
        int clasificacion = Token.clasificar(lexema.toString());
    if (clasificacion < 0) {
    if (nuevoEstado >= 500 || nuevoEstado == 511) {
        analizar(lexema.toString(), clasificacion, linea, posicion);
        return;
    }
    tokens.add(new Token(
        lexema.toString(),
        Token.Tipo.UNKNOWN,
        linea,
        posicion,
        clasificacion
    ));
}
        else {
            analizar(lexema.toString(), nuevoEstado, linea, posicion);
        }
        return;
    }
    if (nuevoEstado < 0) {
        tokens.add(new Token(
            lexema.toString(),
            Token.Tipo.UNKNOWN,
            linea,
            posicion,
            nuevoEstado
        ));
    } else if (nuevoEstado >= 500) {
        analizar(lexema.toString(), nuevoEstado, linea, posicion);
    }
}
    public void analizar(String Lexema, int numError,int linea, int columna) {
        if (numError==-68) { numError = 511; } 
        String codigoError = String.format("ERR%03d", numError);
        String descripcion = "Error léxico desconocido";

        descripcion = ErrorEntry.definirDescripcion(numError);
        errores.add(new ErrorEntry(codigoError, descripcion, linea, "ManuelCode",Lexema));
    }

    public static boolean esKeyword(String palabra) {
        return KEYWORDS.contains(palabra);
    }

    public List<ErrorEntry> getErrores() {
        return errores;
    }
}
