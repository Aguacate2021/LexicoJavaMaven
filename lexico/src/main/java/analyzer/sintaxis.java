package analyzer;

import java.util.List;
import java.util.Stack;

public class sintaxis {

    private final static Lexer lexer = new Lexer();

    public void parsear(List<Token> tokens) {

        Stack<Integer> pilaProducciones = new Stack<>();

        // Símbolo inicial
        pilaProducciones.push(0);

        while (!tokens.isEmpty() && !pilaProducciones.isEmpty()) {

            Token tokenActual = tokens.get(0);

            int cima = pilaProducciones.peek();

            System.out.println("\n=================================");
            System.out.println("Cima pila: " + cima);
            System.out.println("Token actual: " + tokenActual);

            // =====================================================
            // NO TERMINAL
            // =====================================================
            if (cima >= 0) {

                int fila = cima;

                int columna = LeerCSV2.clasificarTransicion(
                        tokenActual.getTokenClass()
                );

                int estadoTabla = LeerCSV2.getValor(
                        fila,
                        columna
                );

                System.out.println(
                        "Fila: " + fila +
                        " Columna: " + columna +
                        " -> Estado: " + estadoTabla
                );

                // =================================================
                // ERROR SINTÁCTICO
                // =================================================
                if (estadoTabla >= 512) {

                    System.out.println(
                            "Error sintáctico en token: "
                            + tokenActual.getLexema()
                            + " línea: "
                            + tokenActual.getLinea()
                            + " columna: "
                            + tokenActual.getColumna()
                    );

                    break;
                }

                // =================================================
                // EPSILON
                // =================================================
                else if (estadoTabla == 147) {

                    pilaProducciones.pop();

                    System.out.println(
                            "Producción epsilon aplicada"
                    );
                }

                // =================================================
                // PRODUCCIÓN NORMAL
                // =================================================
                else {

                    System.out.println(
                            "Aplicando producción: "
                            + estadoTabla
                    );

                    pilaProducciones =
                            Producciones.aplicarProduccion(
                                    pilaProducciones,
                                    estadoTabla
                            );
                }
            }

            // =====================================================
            // TERMINAL
            // =====================================================
            else {

                // Coincide terminal
                if (cima == tokenActual.getTokenClass()) {

                    System.out.println(
                            "Match: "
                            + tokenActual.getLexema()
                    );

                    pilaProducciones.pop();

                    tokens.remove(0);
                }

                // Error terminal
                else {

                    System.out.println(
                            "Error sintáctico se esperaba: "
                            + cima
                            + " pero llegó: "
                            + tokenActual.getTokenClass()
                    );

                    System.out.println(
                            "Token: "
                            + tokenActual.getLexema()
                            + " línea: "
                            + tokenActual.getLinea()
                            + " columna: "
                            + tokenActual.getColumna()
                    );

                    break;
                }
            }

            System.out.println(
                    "Pila actual: "
                    + pilaProducciones
            );
        }

        // =========================================================
        // RESULTADO FINAL
        // =========================================================
        if (tokens.isEmpty() && pilaProducciones.isEmpty()) {

            System.out.println(
                    "\nAnálisis sintáctico correcto"
            );

        } else {

            System.out.println(
                    "\nAnálisis sintáctico finalizado con errores"
            );
        }
    }

    // =============================================================
    // MAIN DE PRUEBA
    // =============================================================
    public static void main(String[] args) {

        // IMPORTANTE:
        // Debes cargar la tabla antes de parsear

        LeerCSV2.LeerCSV();

        sintaxis parser = new sintaxis();

        List<Token> tokens =
                lexer.tokenizar("main(){}");

        parser.parsear(tokens);
    }
}