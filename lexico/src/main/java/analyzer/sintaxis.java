package analyzer;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

public class sintaxis {
    //Variable trace para imprimir el seguimiento del análisis sintáctico
    private static final boolean TRACE = true;
    // Contador de errores sintácticos para apagar todo e irme a dormir si salen mas de 50 errores
    private static final int MAX_ERRORES = 50;

    private final List<ErrorEntry> erroresSintaxis = new ArrayList<>();
    private final List<ErrorEntry> erroresAmbito = new ArrayList<>();
    TablaSimbolosDAO tabla = new TablaSimbolosDAO();
    
    private StringBuilder logBuilder = new StringBuilder();
    
    //Variables de ambito para el análisis sintáctico
    private boolean AreaDeDeclaraciones = true;
    private boolean AreaDeEjecuciones = false;
    Stack<Integer> AmbitoActual = new Stack<>();
    int ContadorAmbito = 1;
    int produccionActual = 0;
    boolean encontrado = false;
        //Arreglo
        String TamañoArreglo="";
        String idArreglo="";
        int DimensionesArreglo=0;
        int ambitoArreglo;
        //Funciones
        String idFuncion="";
        int NumParametros=0;
        int ambitoFuncion;
    public List<ErrorEntry> getErroresSintaxis() {
        return erroresSintaxis;
    }
    public List<ErrorEntry> getErroresAmbito() {
        return erroresAmbito;
    }
    public String getLogAvance1() {
        return logBuilder.toString();
    }
    public void parsear(List<Token> tokens) {
        logBuilder.setLength(0);
        erroresSintaxis.clear();
        ContadorCiclos.resetearContadores();
        LeerCSV2.LeerCSV();
        LinkedList<Token> lt = new LinkedList<>(tokens);
        Stack<Integer> ps = new Stack<>();
        ContadorAmbito = 1;
        AmbitoActual.clear();
        AmbitoActual.push(0);
        ps.push(0);
        Token tokenActual = null;
        logBuilder.append("Línea: 1 ---> ÁMBITO 0 ---> ABIERTO\n");
        while (!lt.isEmpty() && !ps.isEmpty()) {

            if (ContadorCiclos.ERRORES >= MAX_ERRORES) {
                log("Límite de errores alcanzado, abortando análisis.");
                break;
            }

            tokenActual = lt.getFirst();
            int cima = ps.peek();

            log("Cima: " + cima + " | Token: " + tokenActual.getLexema()
                    + " (clase " + tokenActual.getTokenClass()
                    + ") ln:" + tokenActual.getLinea());

            if (cima >= 0) {
                // Manejo de transiciones especiales para cambiar entre áreas de declaraciones y ejecuciones
                if(cima==800){
                    AreaDeDeclaraciones=false;
                    //logBuilder.append("Linea: " + tokenActual.getLinea() + "    Area de declaracion    :   Cerrada\n");
                    AreaDeEjecuciones=true;
                    //logBuilder.append("Linea: " + tokenActual.getLinea() + "    Area de ejecucion    :   Abierta\n");
                    ps.pop();
                    continue;
                }
                if(cima==801){
                    AreaDeEjecuciones=false;
                    //logBuilder.append("Linea: " + tokenActual.getLinea() + "    Area de ejecucion  :   Cerrada\n");
                    AreaDeDeclaraciones=true;
                    //logBuilder.append("Linea: " + tokenActual.getLinea() + "    Area de declaracion  :   Abierta\n");
                   
                    ps.pop();
                    continue;
                }
                if(cima==802){
                    AbrirAmbito(tokenActual);
                    ps.pop();
                    continue;
                }
                if(cima==803){
                    CerrarAmbito(tokenActual);
                    ps.pop();
                    continue;
                }
                /////////////////////////////////////////////////////////////////
                // ── NO TERMINAL ──────────────────────────────────────────────
                int columna = LeerCSV2.clasificarTransicion(tokenActual.getTokenClass());
                if (columna < 0) {
                    // token no clasificable → error, consumir token
                    registrarError(tokenActual, "Token no reconocido por la tabla sintáctica", -2000);
                    lt.removeFirst();
                    continue;
                }

                int resultado = LeerCSV2.getValor(cima, columna);

                if (resultado >= 512) {
                    // ── ERROR DE NO TERMINAL ──────────────────────────────────
                    registrarError(tokenActual,
                            "Error sintáctico: no hay producción para NT=" + cima
                            + " con token=" + tokenActual.getLexema(),
                            resultado);
                    lt.removeFirst();
                    

                } else if (resultado == 147) {
                    // ── EPSILON ───────────────────────────────────────────────
                    ps.pop();
                    ContadorCiclos.aumentarContador(cima);
                    log("Epsilon: NT=" + cima + " derivó en ε");

                } else {
                    // ── PRODUCCIÓN ────────────────────────────────────────────
                    ps.pop();
                    ContadorCiclos.aumentarContador(cima);
                    produccionActual = resultado;
                    Producciones.aplicarProduccion(ps, resultado);
                    log("NT=" + cima + " → producción " + resultado);
                }

            } else {
                // ── TERMINAL ─────────────────────────────────────────────────
                if (cima == -1000) {
                    // caso especial: identificador genérico
                    int tc = tokenActual.getTokenClass();
                    if (tc >= -67 && tc <= -60 || tc== -107) {
                        if (produccionActual==12||produccionActual==17) {
                            idArreglo=tokenActual.getLexema();
                            TamañoArreglo="";
                            DimensionesArreglo=0;
                        }
                        if (produccionActual==7) {
                            idFuncion=tokenActual.getLexema();
                            NumParametros=0;
                        }
                        if(AreaDeDeclaraciones){
                            /* case 7: return "Funcion";
                                case 35:
                                return "Parametro";
                                case 8: return "Constante";
                                case 20: return "Constante"; */
                            encontrado = false;
                         for (Integer ambito : AmbitoActual) {
                                if (tabla.idExiste(tokenActual.getLexema(), ambito)) {
                                    encontrado = true;
                                    break;
                                    }
                                }
                            // Si se encontró en algún ámbito
                            if (encontrado) {
                                registrarError(
                                    tokenActual,
                                    "Identificador ya declarado en los ámbitos accesibles: "
                                    + tokenActual.getLexema(),
                                    -3000
                                );
                            if(produccionActual==7){
                            tabla.insertar(tokenActual.getLexema(),obtenerTipoDeToken(tc),obtenerClaseDeProduccion(produccionActual,ps),AmbitoActual.peek(),null,null,0,String.valueOf(AmbitoActual.peek()+1));
                            ambitoFuncion=AmbitoActual.peek();
                            }
                            if(produccionActual==12||produccionActual==17){
                            tabla.insertar(tokenActual.getLexema(),obtenerTipoDeToken(tc),obtenerClaseDeProduccion(produccionActual,ps),AmbitoActual.peek(),null,null,null,null);
                            ambitoArreglo=AmbitoActual.peek();
                            }
                            if(produccionActual==35){
                            tabla.insertar(tokenActual.getLexema(),obtenerTipoDeToken(tc),obtenerClaseDeProduccion(produccionActual,ps),AmbitoActual.peek(),null,null,null,idFuncion);
                            }
                            if(produccionActual==8||produccionActual==20){
                            tabla.insertar(tokenActual.getLexema(),obtenerTipoDeToken(tc),obtenerClaseDeProduccion(produccionActual,ps),AmbitoActual.peek(),null,null,null,null);
                            }
                        }
                        } // <-- FIX: cierra "if(AreaDeDeclaraciones)" aquí (antes faltaba esta llave,
                          //     lo que dejaba "if(AreaDeEjecuciones)" anidado dentro de este bloque).
                        if(AreaDeEjecuciones){
                        encontrado = false;
                         for (Integer ambito : AmbitoActual) {
                                if (tabla.idExiste(tokenActual.getLexema(), ambito)) {
                                    encontrado = true;
                                    break;
                                    }
                                }
                            // Si no se encontró en ningún ámbito
                            if (!encontrado) {
                                registrarError(
                                    tokenActual,
                                    "Identificador no declarado en los ámbitos accesibles: "
                                    + tokenActual.getLexema(),
                                    -3000
                                );
                        }
                        } // <-- FIX: cierra "if(AreaDeEjecuciones)" aquí (antes el match de abajo
                          //     quedaba atrapado dentro de esta condición y nunca se ejecutaba
                          //     mientras AreaDeDeclaraciones estuviera activa).

                        // FIX: este match ahora se ejecuta SIEMPRE que el token sea un
                        // identificador válido, sin importar el área (declaraciones o
                        // ejecuciones). Antes, mientras AreaDeDeclaraciones era true (el caso
                        // del primer "var" del programa), este bloque nunca corría porque
                        // estaba anidado dentro de "if(AreaDeEjecuciones)", así que el -1000
                        // jamás se sacaba de la pila y el parser quedaba atorado.
                        ps.pop();
                        lt.removeFirst();
                        log("Match ID: " + tokenActual.getLexema());
                    } else {
                        registrarError(tokenActual,
                                "Se esperaba un identificador, se encontró: "
                                + tokenActual.getLexema(), -2000);
                        lt.removeFirst();
                        // FIX: también se saca el -1000 de la pila en el camino de error,
                        // para no dejarlo atorado repitiendo el mismo error en cascada con
                        // cada token restante del archivo.
                        ps.pop();
                    }

                } else if (cima == tokenActual.getTokenClass()) {
                    // Ambito de arreglos: si la producción actual es 14 o 16 y el token actual es -55 (Numero Decimal), entonces se está aumentando el tamaño y el número de dimensiones y se debe contar la dimensión y el tamaño
                    if((produccionActual==14 && tokenActual.getTokenClass()==-55)||(produccionActual==16 && tokenActual.getTokenClass()==-55)){
                        DimensionesArreglo++;
                        if(TamañoArreglo.equals("")){
                            TamañoArreglo=tokenActual.getLexema();
                        }else{
                            TamañoArreglo=TamañoArreglo+","+tokenActual.getLexema();
                        }
                    }
                    // match normal
                    ps.pop();
                    lt.removeFirst();
                    log("Match: " + tokenActual.getLexema());
                } else {
                    // Error de fuerza bruta: no coincide el terminal esperado con el token actual
                    registrarError(tokenActual,
                            "Se esperaba terminal " + cima
                            + " pero se encontró " + tokenActual.getTokenClass()
                            + " ('" + tokenActual.getLexema() + "')", -2000);
                    break;
                }
            }
            log("Pila: " + ps);
        }

        if (lt.isEmpty() && !ps.isEmpty()) {
             // Contar como error si quedan no terminales sin resolver
             registrarError(tokenActual,
                                "Bloque incompleto: quedan no terminales sin resolver en la pila al finalizar los tokens. Cima residual: "
                                + tokenActual.getLexema(),-2000);
                                // Vaciar epsilones residuales: metodo que me ayudo a verificar las producciones, pero no es necesario para el análisis sintáctico final
                                //vaciarEpsilones(ps);
        }
        boolean exitoso = lt.isEmpty() && ps.isEmpty() && ContadorCiclos.ERRORES == 0;
        if (exitoso) {
            System.out.println("\nAnálisis sintáctico correcto.");
        } else {
            System.out.println("\nAnálisis sintáctico finalizado con "
                    + ContadorCiclos.ERRORES + " error(es) sintáctico(s).");
        }
        logBuilder.append("Línea: " + tokenActual.getLinea() + " ---> ÁMBITO " + AmbitoActual.peek() + " ---> CERRADO\n");
    }
    /*private void vaciarEpsilones(Stack<Integer> ps) {
        while (!ps.isEmpty()) {
            int cima = ps.peek();
            if (cima < 0&& cima>0) break; 
            int resultado = LeerCSV2.getValor( cima, 0);
            if (resultado == 147) {
                ps.pop();
                ContadorCiclos.aumentarContador(cima);
                log("Epsilon residual: NT=" + cima);
            } else {
                log("No se puede vaciar epsilon residual: NT=" + cima);
                break;
            }
        }
    }*/

    private void registrarError(Token t, String descripcion, int numError) {
        ContadorCiclos.ERRORES++;
        String codigo = String.format("ERR-SYN-%03d", ContadorCiclos.ERRORES);
        if (numError == -3000) {
            erroresSintaxis.add(new ErrorEntry(
                codigo,
                descripcion,
                t.getLinea(),
                "parser",
                ErrorEntry.Tipo.AMBITO,
                t.getLexema()
            ));
            System.out.println("[SYN-ERR] ln=" + t.getLinea()
                + " col=" + t.getColumna() + " | " + descripcion);
        }
        if (numError == -2000) {
            erroresSintaxis.add(new ErrorEntry(
                codigo,
                descripcion,
                t.getLinea(),
                "parser",
                ErrorEntry.Tipo.SINTAXIS,
                t.getLexema()
            ));
            System.out.println("[SYN-ERR] ln=" + t.getLinea()
                + " col=" + t.getColumna() + " | " + descripcion);
        }else {
            codigo = String.format("ERR-SYN-"+numError, ContadorCiclos.ERRORES);
            erroresSintaxis.add(new ErrorEntry(
                codigo,
                ErrorEntry.definirDescripcionSintaxis(numError),
                t.getLinea(),
                "parser",
                ErrorEntry.Tipo.SINTAXIS,
                t.getLexema()
            ));
            System.out.println("SYN-ERR " + numError + t.getLinea()
                + " col=" + t.getColumna() + " | " + descripcion);
        }
    }

    private void log(String msg) {
        if (TRACE) System.out.println("[TRACE] " + msg);
    }

    private void AbrirAmbito(Token tokenActual) {
        AmbitoActual.push(ContadorAmbito++);
        logBuilder.append("Línea: " + tokenActual.getLinea() + " ---> ÁMBITO " + AmbitoActual.peek() + " ---> ABIERTO\n");
    }
    private void CerrarAmbito(Token tokenActual) {
        logBuilder.append("Línea: " + tokenActual.getLinea() + " ---> ÁMBITO " + AmbitoActual.peek() + " ---> CERRADO\n");
        AmbitoActual.pop();
    }

    private String obtenerClaseDeProduccion(int numProduccion,Stack<Integer> stack) {
        if (numProduccion==12 && stack.peek()==-9) {
            return "Variable";
        }
        if (numProduccion==12 && stack.peek()!=-9) {
            return "Arreglo";
        }
        switch (numProduccion) {
            case 7: return "Funcion";
            case 35: 
            NumParametros++;
            return "Parametro";
            case 8: return "Constante";
            case 20: return "Constante";
            default:
                return "";
        }
    }
    private String obtenerTipoDeToken(int tokenID) {
        switch (tokenID) {
            case -60: return "Cadena";
            case -61: return "Binario";
            case -62: return "Decimal";
            case -63: return "Octal";
            case -64: return "Hexadecimal";
            case -65: return "Real";
            case -66: return "Exponencial";
            case -67: return "Booleanas";
            default:
                return "";
        }
    }
}