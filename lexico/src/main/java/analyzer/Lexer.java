package analyzer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Analizador léxico para código Python.
 *
 * Responsabilidades:
 *   - tokenizar(String) → Lista de Token
 *   - analizar(String)  → Lista de ErrorEntry
 *
 * No tiene dependencias de UI.
 */
public class Lexer {
    List<ErrorEntry> errores = new ArrayList<>();
    // ── Palabras clave de Python 3 ───────────────────────────────────────────
    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
        "null", "true", "false", "any", "break",
        "charAt", "class", "concat", "console.log", "const", "continue",
        "do", "else", "endsWith", "filter", "find", "findIndex",
        "for", "forEach", "get", "if", "in", "Includes",
        "indexOf", "interface", "length", "let", "map", "of", "push",
        "replace", "reverse", "set", "shift", "slice", "sort",
        "splice", "split", "startsWith", "switch",
        "toLowerCase", "toUpperCase", "trim", "typeof",
        "undefined", "while"
    ));

    // ════════════════════════════════════════════════════════════════════════
    // TOKENIZACIÓN
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Recorre el código fuente carácter a carácter y produce tokens.
     */
    public List<Token> tokenizar(String codigo) {
        List<Token> tokens = new ArrayList<>();
        if (codigo == null || codigo.isBlank()) return tokens;

        LeerCSV.LeerCSV();
        String[] lineas = codigo.split("\n", -1);

        int estado = 0; // 🔥 estado global
        boolean enComentarioMultilinea = false;
        StringBuilder comentario = new StringBuilder();

        for (int numLinea = 0; numLinea < lineas.length; numLinea++) {
            String linea = lineas[numLinea];
            int col = 0;

            while (col < linea.length()) {

                char ch = linea.charAt(col);

                // 🔥 SI YA ESTÁS EN COMENTARIO MULTILÍNEA
                if (enComentarioMultilinea) {
                    comentario.append(ch);

                    // Detectar cierre */
                    if (comentario.length() >= 2 &&
                            comentario.charAt(comentario.length() - 2) == '*' &&
                            comentario.charAt(comentario.length() - 1) == '/') {

                        enComentarioMultilinea = false;

                        tokens.add(new Token(
                                comentario.toString(),
                                Token.Tipo.COMMENT,
                                numLinea + 1,
                                col - comentario.length() + 2,
                                -51
                        ));

                        comentario.setLength(0);
                    }

                    col++;
                    continue;
                }

                // 🔥 Detectar inicio de comentario multilínea
                if (ch == '/' && col + 1 < linea.length() && linea.charAt(col + 1) == '*') {
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
                        System.out.println("Caracter inválido: " + ch);
                        col++;
                        break;
                    }

                    int nuevoEstado = LeerCSV.getValor(estado, clase);

                    // 🔥 TRANSICIÓN
                    if (nuevoEstado >= 0 && nuevoEstado < 500) {
                        estado = nuevoEstado;
                        lexema.append(ch);
                        col++;
                    }

                    // 🔥 ACEPTACIÓN
                    else if (nuevoEstado < 0) {
                        if (nuevoEstado == -68) {

                            if ((Token.clasificar(lexema.toString()) != 510)){
                                tokens.add(new Token(
                                lexema.toString(),
                                Token.Tipo.UNKNOWN,
                                numLinea + 1,
                                col - lexema.length() + 1,
                                Token.clasificar(lexema.toString())
                            ));

                            estado = 0; //reiniciar estado
                            break;
                            }else{
                                if (lexema.length() > 0) {
                                lexema.append(ch); // incluir el carácter problemático en el lexema del error
                                analizar(
                                    lexema.toString(),
                                    nuevoEstado,
                                    numLinea + 1,
                                    col - lexema.length() + 1
                                );
                                } else {
                                    // 🔥 si el error ocurre desde el inicio (ej: @)
                                    analizar(
                                        String.valueOf(ch),
                                        nuevoEstado,
                                        numLinea + 1,
                                        col + 1
                                    );
                                }
                                estado = 0;
                                break;
                            }
                        } else {
                            tokens.add(new Token(
                                lexema.toString(),
                                Token.Tipo.UNKNOWN,
                                numLinea + 1,
                                col - lexema.length() + 1,
                                nuevoEstado
                            ));

                            estado = 0; // 🔥 reiniciar estado
                            break;
                        }
                    }

                    // 🔥 ERROR
                    else if (nuevoEstado >= 500) {
                        if (lexema.length() > 0) {
                            lexema.append(ch); // incluir el carácter problemático en el lexema del error
                            analizar(
                                lexema.toString(),
                                nuevoEstado,
                                numLinea + 1,
                                col - lexema.length() + 1
                            );
                        } else {
                            // 🔥 si el error ocurre desde el inicio (ej: @)
                            analizar(
                                String.valueOf(ch),
                                nuevoEstado,
                                numLinea + 1,
                                col + 1
                            );
                            
                        }
                        estado = 0;
                        break;
                    }
                }

                // 🔥 FORZAR EVALUACIÓN AL FINAL DE LÍNEA
                if (lexema.length() > 0 && col >= linea.length()) {

                    int claseEOF = 56;
                    int nuevoEstado = LeerCSV.getValor(estado, claseEOF);
                    if (nuevoEstado == -68) {

                            if ((Token.clasificar(lexema.toString()) != 510)){
                                tokens.add(new Token(
                                lexema.toString(),
                                Token.Tipo.UNKNOWN,
                                numLinea + 1,
                                col - lexema.length() + 1,
                                Token.clasificar(lexema.toString())
                            ));

                            estado = 0; //reiniciar estado
                            break;
                            }else{
                                if (lexema.length() > 0) {
                                analizar(
                                    lexema.toString(),
                                    nuevoEstado,
                                    numLinea + 1,
                                    col - lexema.length() + 1
                                );
                                } else {
                                    // 🔥 si el error ocurre desde el inicio (ej: @)
                                    analizar(
                                        String.valueOf(ch),
                                        nuevoEstado,
                                        numLinea + 1,
                                        col + 1
                                    );
                                }
                                estado = 0;
                                break;
                            }
                        }
                    if (nuevoEstado < 0) {
                        System.out.println("TOKEN EOF -> " + lexema);

                        tokens.add(new Token(
                                lexema.toString(),
                                Token.Tipo.UNKNOWN,
                                numLinea + 1,
                                col - lexema.length() + 1,
                                nuevoEstado
                        ));
                    } else if (nuevoEstado >= 500) {
                        analizar(
                                lexema.toString(),
                                nuevoEstado,
                                numLinea + 1,
                                col - lexema.length() + 1
                            );
                    }

                    estado = 0; // 🔥 reset
                }

                // 🔴 evitar loop infinito
                if (lexema.length() == 0) {
                    col++;
                }
            }
        }

        // 🔴 error si no se cerró comentario
        if (enComentarioMultilinea) {
            System.out.println("ERROR: comentario multilínea no cerrado");
        }

        return tokens;
    }
    // ════════════════════════════════════════════════════════════════════════
    // ANÁLISIS DE ERRORES
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Analiza el código fuente e identifica errores léxicos/sintácticos básicos.
     * Recibe los tokens ya generados para evitar tokenizar dos veces.
     */
    public void analizar(String Lexema, int numError,int linea, int columna) {
        String codigoError = String.format("ERR%03d", numError);
        String descripcion = "Error léxico desconocido";

        // Ejemplo de clasificación de errores basada en el número de error
        descripcion = ErrorEntry.definirDescripcion(numError);
        errores.add(new ErrorEntry(codigoError, descripcion, linea, "ManuelCode",Lexema));
    }

    /** Utilidad: ¿es esta palabra un keyword? */
    public static boolean esKeyword(String palabra) {
        return KEYWORDS.contains(palabra);
    }

    public List<ErrorEntry> getErrores() {
        return errores;
    }
}
