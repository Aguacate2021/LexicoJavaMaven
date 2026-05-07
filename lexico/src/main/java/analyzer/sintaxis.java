package analyzer;

import java.util.List;
import java.util.Stack;

public class sintaxis {

    public void parsear(List<Token> tokens) {

        Stack<Integer> pilaProducciones = new Stack<>();
        LeerCSV2.LeerCSV();

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
            // TERMINALES
            // =====================================================
            else {

                // =================================================
                // CASO ESPECIAL PARA IDs (-1000)
                // =================================================
                if (cima == -1000) {

                    int token = tokenActual.getTokenClass();

                    // IDs entre -67 y -60
                    if (token >= -67 && token <= -60) {

                        System.out.println(
                                "Match ID: "
                                + tokenActual.getLexema()
                        );

                        pilaProducciones.pop();

                        tokens.remove(0);

                    } else {

                        System.out.println(
                                "Error sintáctico: se esperaba un identificador"
                        );

                        System.out.println(
                                "Token encontrado: "
                                + tokenActual.getLexema()
                                + " línea: "
                                + tokenActual.getLinea()
                                + " columna: "
                                + tokenActual.getColumna()
                        );

                        break;
                    }
                }

                // =================================================
                // TERMINAL NORMAL
                // =================================================
                else if (cima == tokenActual.getTokenClass()) {

                    System.out.println(
                            "Match: "
                            + tokenActual.getLexema()
                    );

                    pilaProducciones.pop();

                    tokens.remove(0);
                }

                // =================================================
                // ERROR TERMINAL
                // =================================================
                else {

                    System.out.println(
                            "Error sintáctico, se esperaba: "
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
/* 
    // =============================================================
    // MAIN DE PRUEBA
    // =============================================================
    public static void main(String[] args) {

        // Cargar tabla LL(1)
        LeerCSV2.LeerCSV();

        sintaxis parser = new sintaxis();
        String codigo = """
main ( ) { 
    while ( @sensor_activo == true ) { 
        if ( @temperatura > 90 ) { 
            @estado = 1 ; 
        } else { 
            @estado = 0 ; 
        } ; 

        Console.log ( @estado ) ; 
        @residuo = 10 # 3 ; 

        // 1. Igualdad (==) usando SIMPLE_EXP (Suma)
        if ( @temperatura + 10 == 100 ) { 
            Console.log ( 1 ) ; 
        } ; 

        // 3. Menor que (<) usando TERMINO_PASCAL (Multiplicación)
        if ( @temperatura < @base * 2 ) { 
            Console.log ( 3 ) ; 
        } ; 

        // 4. Mayor que (>) usando TERMINO_PASCAL (División)
        if ( @temperatura > @maximo / 2 ) { 
            Console.log ( 4 ) ; 
        } ; 

        // 5. Menor o igual (<=) usando TERMINO_PASCAL (Módulo #)
        if ( @temperatura <= @valor # 3 ) { 
            Console.log ( 5 ) ; 
        } ; 

        // 6. Mayor o igual (>=) usando ELEVACION (Potencia ^)
        if ( @temperatura >= @umbral ^ 2 ) { 
            Console.log ( 6 ) ; 
        } ; 

        // 7. Expresión compleja: Paréntesis (FACTOR) + Relacional
        if ( ( @temperatura + @residuo ) * 2 < @limite ) { 
            Console.log ( 7 ) ; 
        } ; 

        // FACTOR -> Funcion -> pow ( OR , OR )
        @c_cuadrado = POW ( @a , 2 ) + POW ( @b , 2 ) ; 

        // FACTOR -> Funcion -> sqrt ( OR )
        @c = SQRT ( @c_cuadrado ) ;
    } 
}
""";
;
        List<Token> tokens =
                lexer.tokenizar(codigo);

        parser.parsear(tokens);
    }*/
}