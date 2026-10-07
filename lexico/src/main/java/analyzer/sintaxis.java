package analyzer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import analyzer.TablasComparacion.LeerCSVComparacion;

public class sintaxis {

    // Variable trace para imprimir el seguimiento
    // del análisis sintáctico
    private static final boolean TRACE = true;

    // Límite máximo de errores
    private static final int MAX_ERRORES = 500;

    private final List<ErrorEntry> erroresSintaxis = new ArrayList<>();

    private final List<ErrorEntry> erroresAmbito = new ArrayList<>();

    // DAO de la tabla de símbolos
    private final TablaSimbolosDAO tabla = new TablaSimbolosDAO();

    private final StringBuilder logBuilder = new StringBuilder();

    // Espejo en memoria de lo que se va escribiendo en ARCHIVO_POSTFIJAS
    // (una línea "Linea N: <prefijo>" por operación), para que la IDE
    // pueda ofrecer un diálogo "Guardar como..." sin depender de leer
    // el archivo físico de vuelta.
    private final StringBuilder logExpresiones = new StringBuilder();
    
    // =========================================================
    // ÁREAS
    // =========================================================

    private boolean AreaDeDeclaraciones = true;
    private boolean AreaDeEjecuciones = false;

    // =========================================================
    // ÁMBITOS
    // =========================================================

    Stack<Integer> AmbitoActual = new Stack<>();

    int ContadorAmbito = 1;

    // =========================================================
    // PRODUCCIÓN ACTUAL
    // =========================================================

    int produccionActual = 0;

    // =========================================================
    // IDENTIFICADORES
    // =========================================================

    boolean encontrado = false;

    // =========================================================
    // ARREGLOS
    // =========================================================

    String TamañoArreglo = "";
    String idArreglo = "";
    int DimensionesArreglo = 0;
    int ambitoArreglo = -1;

    // =========================================================
    // FUNCIONES
    // =========================================================

    String idFuncion = "";
    int NumParametros = 0;
    int ambitoFuncion = -1;

    // Bandera: el próximo AbrirAmbito() corresponde al cuerpo/lista
    // de parámetros de la función recién insertada (idFuncion/ambitoFuncion).
    // Por el orden de la producción 7 (ID -> abrir ámbito -> lista de
    // parámetros), ese siguiente ámbito abierto es siempre el suyo.
    boolean pendienteAmbitoFuncion = false;

    // =========================================================
    // SEMANTICA
    // =========================================================
    boolean EstadoDeOperacion = false;
    Stack<Token> operadoresPila = new Stack<>();
    Stack<Token> operandosPila = new Stack<>();
    int lineaDondeEstaLaOperacion = 0;

    // =========================================================
    // AVANCE 2: OPERACIONES CON TEMPORALES TIPADOS
    // =========================================================
    private final LeerCSVComparacion comparacion = new LeerCSVComparacion();

    // Tipo semántico (1..10, los de LeerCSVComparacion) de cada operando
    // original de la operación en curso. Un ID no declarado vale Variant.
    private final Map<Token, Integer> tiposOperando = new IdentityHashMap<>();

    // temporal -> tipo (TVariant1 -> 10, TDec1 -> 2, ...). Nunca se deduce del texto.
    private final Map<String, Integer> tipoDeTemporal = new HashMap<>();

    // Contador independiente por tipo. Se reinicia solo en parsear().
    private final Map<Integer, Integer> contadorTemporales = new HashMap<>();

    // Operaciones generadas ("Linea N:" + cuádruplas) de toda la compilación.
    private final StringBuilder logOperaciones = new StringBuilder();

    // Estadísticas por línea de operación (Avance final Semántica 1).
    // línea -> int[10]: [0..8] = temporales GENERADOS de TBin, TDec, TOct, THex,
    // TReal, Texp, TCadena, TBoolean, TVariant; [9] = asignaciones.
    // Los errores no se guardan aquí: ya viven en erroresSintaxis (Tipo.SEMANTICA).
    private final Map<Integer, int[]> estadisticasSemantica = new TreeMap<>();
    private final Map<Integer, List<String>> asignacionesDetalle = new TreeMap<>();
    private int[] estadisticaLineaActual = null;

    private static class Operando {
        final String texto;
        final int tipo;
        Operando(String texto, int tipo) { this.texto = texto; this.tipo = tipo; }
    }

    // Los marcadores 804/805 (producción 44/45/46) se anidan: se abren y
    // cierran una vez por cada identificador que aparece como factor
    // dentro de la expresión, no una sola vez por asignación completa.
    // Este contador distingue el 804 "externo" (el que realmente abre la
    // operación completa) de los 804 "internos" (uno por cada ID), y de
    // igual forma distingue el 805 que de verdad cierra la operación
    // completa. Solo se limpian/inicializan las pilas en el 804 que hace
    // que la profundidad pase de 0 a 1, y solo se vacía/escribe el
    // resultado en el 805 que hace que la profundidad vuelva a 0.
    int profundidadOperacion = 0;

    // Texto de la operación tal como la va consumiendo el compilador
    // (se reconstruye token por token mientras EstadoDeOperacion == true).
    private final StringBuilder infijoActual = new StringBuilder();

    // Archivo donde se van agregando, por cada operación, la línea,
    // la expresión original (infija) y su equivalente postfijo.
    private static final String ARCHIVO_POSTFIJAS = "Avance1-DavidAlatorre.txt";

    // -----------------------------------------------------------
    // Prioridad de operadores para el algoritmo infija -> postfija.
    // Nivel más alto = se evalúa/desapila primero. No existía esta
    // lógica en el proyecto, así que se crea aquí la estructura mínima
    // necesaria, usando exactamente los códigos de token del proyecto.
    // -----------------------------------------------------------
    private static final Map<Integer, Integer> PRECEDENCIA_OPERADOR = new HashMap<>();
    private static final Set<Integer> ASOCIATIVIDAD_DERECHA = new HashSet<>();

    static {
        // Asignación (nivel 1, asociatividad derecha)
        for (int t : new int[]{-33, -34, -35, -36, -37, -38, -39, -40, -41, -42, -43, -44}) {
            PRECEDENCIA_OPERADOR.put(t, 1);
            ASOCIATIVIDAD_DERECHA.add(t);
        }
        // Ternario (nivel 2, asociatividad derecha)
        PRECEDENCIA_OPERADOR.put(-32, 2);
        ASOCIATIVIDAD_DERECHA.add(-32);
        // OR lógico
        PRECEDENCIA_OPERADOR.put(-31, 3);
        // AND lógico
        PRECEDENCIA_OPERADOR.put(-30, 4);
        // OR bit a bit
        PRECEDENCIA_OPERADOR.put(-4, 5);
        // XOR bit a bit
        PRECEDENCIA_OPERADOR.put(-6, 6);
        // AND bit a bit
        PRECEDENCIA_OPERADOR.put(-5, 7);
        // Igualdad
        for (int t : new int[]{-24, -25, -26, -27, -28}) {
            PRECEDENCIA_OPERADOR.put(t, 8);
        }
        // Relacional
        for (int t : new int[]{-20, -21, -22, -23}) {
            PRECEDENCIA_OPERADOR.put(t, 9);
        }
        // Desplazamiento de bits
        for (int t : new int[]{-17, -18, -19}) {
            PRECEDENCIA_OPERADOR.put(t, 10);
        }
        // Aditivos
        PRECEDENCIA_OPERADOR.put(-11, 11);
        PRECEDENCIA_OPERADOR.put(-12, 11);
        // Multiplicativos
        for (int t : new int[]{-13, -14, -15}) {
            PRECEDENCIA_OPERADOR.put(t, 12);
        }
        // Potencia (asociatividad derecha)
        PRECEDENCIA_OPERADOR.put(-16, 13);
        ASOCIATIVIDAD_DERECHA.add(-16);
        // Unarios prefijo: NOT lógico (!) y NOT bit a bit (~) (asoc. derecha)
        PRECEDENCIA_OPERADOR.put(-29, 14);
        ASOCIATIVIDAD_DERECHA.add(-29);
        PRECEDENCIA_OPERADOR.put(-3, 14);
        ASOCIATIVIDAD_DERECHA.add(-3);
        // -1 (++) y -2 (--) NO se agregan aquí: son postfijos unarios y se
        // manejan aparte en procesarTokenDeOperacion (van directo a la
        // salida, sin pasar por la pila de operadores).
    }
    // =========================================================
    // GETTERS
    // =========================================================

    public List<ErrorEntry> getErroresSintaxis() {
        return erroresSintaxis;
    }

    public List<ErrorEntry> getErroresAmbito() {
        return erroresAmbito;
    }

    public String getLogAvance1() {
        return logBuilder.toString();
    }

    public String getLogExpresiones() {
        return logExpresiones.toString();
    }

    public String getLogOperaciones() {
        return logOperaciones.toString();
    }

    public Map<Integer, int[]> getEstadisticasSemantica() {
        return estadisticasSemantica;
    }

    public Map<Integer, List<String>> getAsignacionesDetalle() {
        return asignacionesDetalle;
    }

    public int getTipoTemporal(String temporal) {
        return tipoDeTemporal.getOrDefault(temporal, LeerCSVComparacion.T_VARIANT);
    }

    // =========================================================
    // PARSER
    // =========================================================

    public void parsear(List<Token> tokens) {

        // -----------------------------------------------------
        // LIMPIAR ESTADO DEL ANÁLISIS
        // -----------------------------------------------------

        logBuilder.setLength(0);

        erroresSintaxis.clear();
        erroresAmbito.clear();

        ContadorCiclos.resetearContadores();

        AreaDeDeclaraciones = true;
        AreaDeEjecuciones = false;

        produccionActual = 0;

        // Estado de la conversión infija -> prefija: por si quedó algo
        // a medias de un análisis anterior que no cerró bien.
        profundidadOperacion = 0;
        EstadoDeOperacion = false;
        operandosPila.clear();
        operadoresPila.clear();
        infijoActual.setLength(0);

        // Avance 2: temporales y tablas de compatibilidad (nueva compilación)
        tiposOperando.clear();
        tipoDeTemporal.clear();
        contadorTemporales.clear();
        logOperaciones.setLength(0);
        estadisticasSemantica.clear();
        asignacionesDetalle.clear();
        estadisticaLineaActual = null;
        comparacion.cargarTablas();

        // -----------------------------------------------------
        // REINICIAR ARCHIVO DE OPERACIONES POSTFIJAS
        // -----------------------------------------------------

        reiniciarArchivoPostfijas();

        // -----------------------------------------------------
        // LIMPIAR ESTADO DE TABLA DE SÍMBOLOS
        // -----------------------------------------------------

        if (!tabla.limpiarTablaSimbolos()) {

            System.err.println(
                    "[BD] No se pudo limpiar la tabla de símbolos.");

            return;
        }

        // -----------------------------------------------------
        // CARGAR TABLA SINTÁCTICA
        // -----------------------------------------------------

        LeerCSV2.LeerCSV();

        LinkedList<Token> lt = new LinkedList<>(tokens);

        Stack<Integer> ps = new Stack<>();

        // -----------------------------------------------------
        // INICIALIZAR ÁMBITOS
        // -----------------------------------------------------

        ContadorAmbito = 1;

        AmbitoActual.clear();

        AmbitoActual.push(0);

        // -----------------------------------------------------
        // INICIALIZAR PILA DEL PARSER
        // -----------------------------------------------------

        ps.push(0);

        Token tokenActual = null;

        logBuilder.append(
                "Línea: 1 ---> ÁMBITO 0 ---> ABIERTO\n");
        // =====================================================
        // CICLO PRINCIPAL
        // =====================================================
        while (!lt.isEmpty() && !ps.isEmpty()) {
            if (ContadorCiclos.ERRORES >= MAX_ERRORES) {

                log(
                        "Límite de errores alcanzado, " +
                                "abortando análisis.");

                break;
            }
            tokenActual = lt.getFirst();
            int cima = ps.peek();
            log(
                    "Cima: " + cima +
                            " | Token: " + tokenActual.getLexema() +
                            " (clase " +
                            tokenActual.getTokenClass() +
                            ") ln:" +
                            tokenActual.getLinea());
            // =================================================
            // MANEJO DE NO TERMINALES
            // =================================================
            if (cima >= 0) {
                // -------------------------------------------------
                // Cambio de declaraciones a ejecuciones
                // -------------------------------------------------
                if (cima == 800) {

                    AreaDeDeclaraciones = false;
                    AreaDeEjecuciones = true;

                    ps.pop();

                    continue;
                }
                // -------------------------------------------------
                // Cambio de ejecuciones a declaraciones
                // -------------------------------------------------
                if (cima == 801) {

                    AreaDeEjecuciones = false;
                    AreaDeDeclaraciones = true;

                    ps.pop();

                    continue;
                }
                // -------------------------------------------------
                // Abrir ámbito
                // -------------------------------------------------
                if (cima == 802) {

                    AbrirAmbito(tokenActual);

                    ps.pop();

                    continue;
                }
                // -------------------------------------------------
                // Cerrar ámbito
                // -------------------------------------------------
                if (cima == 803) {

                    CerrarAmbito(tokenActual);

                    ps.pop();

                    continue;
                }
                // -------------------------------------------------
                // Abrir estado de operando
                // -------------------------------------------------
                 if (cima == 804) {
                    if (profundidadOperacion == 0) {
                        EstadoDeOperacion = true;
                        lineaDondeEstaLaOperacion = tokenActual.getLinea();
                        // Estado limpio por si quedó algo de una operación
                        // previa que no se haya podido cerrar bien. Solo
                        // se hace en el 804 externo: un 804 anidado (el
                        // que abre cada identificador-factor) no debe
                        // borrar lo que ya se lleva acumulado de la
                        // operación completa.
                        operandosPila.clear();
                        operadoresPila.clear();
                        infijoActual.setLength(0);
                    }
                    profundidadOperacion++;
                    ps.pop();
                    continue;
                }
                // -------------------------------------------------
                // Cerrar estado de operando
                // -------------------------------------------------
                 if (cima == 805) {
                    if (profundidadOperacion > 0) {
                        profundidadOperacion--;
                    }
                    if (profundidadOperacion == 0) {
                        EstadoDeOperacion = false;
                        finalizarOperacionPostfija();
                    }
                    ps.pop();
                    continue;
                }
                // -------------------------------------------------
                // Obtener columna
                // -------------------------------------------------
                int columna = LeerCSV2.clasificarTransicion(
                        tokenActual.getTokenClass());
                if (columna < 0) {
                    registrarError(
                            tokenActual,
                            "Token no reconocido por la tabla sintáctica",
                            -2000);

                    lt.removeFirst();
                    continue;
                }
                // -------------------------------------------------
                // Obtener resultado
                // -------------------------------------------------
                int resultado = LeerCSV2.getValor(
                        cima,
                        columna);
                // -------------------------------------------------
                // ERROR DE NO TERMINAL
                // -------------------------------------------------
                if (resultado >= 512) {
                    registrarError(
                            tokenActual,
                            "Error sintáctico: no hay producción " +
                                    "para NT=" + cima +
                                    " con token=" +
                                    tokenActual.getLexema(),
                            resultado);

                    lt.removeFirst();
                }
                // -------------------------------------------------
                // EPSILON
                // -------------------------------------------------
                else if (resultado == 147) {

                    ps.pop();

                    ContadorCiclos.aumentarContador(cima);

                    log(
                            "Epsilon: NT=" +
                                    cima +
                                    " derivó en ε");
                }
                // -------------------------------------------------
                // PRODUCCIÓN
                // -------------------------------------------------
                else {
                    ps.pop();
                    ContadorCiclos.aumentarContador(cima);
                    produccionActual = resultado;
                    Producciones.aplicarProduccion(
                            ps,
                            resultado);
                    log(
                            "NT=" + cima +
                                    " → producción " +
                                    resultado);
                }
            }
            // =====================================================
            // TERMINALES
            // =====================================================
            else {
                // =================================================
                // IDENTIFICADOR
                // =================================================
                if (cima == -1000) {
                    int tc = tokenActual.getTokenClass();
                    if ((tc >= -67 && tc <= -60)
                            || tc == -107) {

                        String id = tokenActual.getLexema().trim();
                        // -------------------------------------------------
                        // Guardar información especial de arreglo
                        // -------------------------------------------------
                        if (produccionActual == 12
                                || produccionActual == 17) {

                            idArreglo = id;

                            TamañoArreglo = "";

                            DimensionesArreglo = 0;

                            ambitoArreglo = AmbitoActual.peek();
                        }
                        // -------------------------------------------------
                        // Guardar información especial de función
                        // -------------------------------------------------
                        if (produccionActual == 7) {

                            idFuncion = id;

                            NumParametros = 0;

                            ambitoFuncion = AmbitoActual.peek();
                        }
                        // =================================================
                        // DECLARACIONES
                        // =================================================
                        if (AreaDeDeclaraciones) {
                            encontrado = tabla.idExiste(id, AmbitoActual.peek());
                            // ---------------------------------------------
                            // ID YA EXISTE EN EL ÁMBITO ACTUAL
                            // ---------------------------------------------
                            if (encontrado) {
                                registrarError(
                                        tokenActual,
                                        "Identificador ya declarado en el ámbito actual: " +
                                                id,
                                        -3000);
                            }
                            // =================================================
                            // ID NUEVO
                            // =================================================
                            else {
                                String tipo = obtenerTipoDeToken(tc);
                                String clase = obtenerClaseDeProduccion(
                                        produccionActual,
                                        ps);
                                int ambito = AmbitoActual.peek();

                                boolean insertado = false;
                                // -----------------------------------------
                                // FUNCIÓN
                                // -----------------------------------------
                                if (produccionActual == 7) {

                                    insertado = tabla.insertar(
                                            id,
                                            tipo,
                                            clase,
                                            ambito,
                                            null,
                                            null,
                                            0,
                                            null);

                                    ambitoFuncion = ambito;

                                    // El ámbito hijo real (TParr) todavía
                                    // no existe: se abrirá con el marcador
                                    // 802 inmediatamente después. Se
                                    // completa en AbrirAmbito().
                                    pendienteAmbitoFuncion = true;
                                }

                                // -----------------------------------------
                                // ARREGLO / VARIABLE
                                // -----------------------------------------

                                else if (produccionActual == 12
                                        ||
                                        produccionActual == 17) {

                                    // No se puede saber por el número de
                                    // producción si es Variable o Arreglo:
                                    // la producción 17 se reutiliza para
                                    // CUALQUIER identificador que no sea
                                    // el primero de una lista "var a, b, c;",
                                    // sea o no arreglo. Se inserta como
                                    // Variable y, si luego aparecen
                                    // corchetes reales, actualizarArreglo()
                                    // reclasifica la fila a Arreglo.

                                    insertado = tabla.insertar(
                                            id,
                                            tipo,
                                            "Variable",
                                            ambito,
                                            null,
                                            null,
                                            null,
                                            null);

                                    ambitoArreglo = ambito;
                                }

                                // -----------------------------------------
                                // PARÁMETRO
                                // -----------------------------------------

                                else if (produccionActual == 35 ||
                                        produccionActual == 36) {

                                    insertado = tabla.insertar(
                                            id,
                                            tipo,
                                            clase,
                                            ambito,
                                            null,
                                            null,
                                            null,
                                            idFuncion);
                                }

                                // -----------------------------------------
                                // CONSTANTE
                                // -----------------------------------------

                                else if (produccionActual == 8
                                        ||
                                        produccionActual == 20) {

                                    insertado = tabla.insertar(
                                            id,
                                            tipo,
                                            clase,
                                            ambito,
                                            null,
                                            null,
                                            null,
                                            null);
                                }
                                if (insertado) {
                                    log(
                                            "Símbolo registrado en BD: "
                                                    + id
                                                    + " | clase="
                                                    + clase
                                                    + " | ámbito="
                                                    + ambito);
                                }
                            }
                        }

                        // =================================================
                        // EJECUCIONES
                        // =================================================

                        int ambitoEncontrado = -1;

                        if (AreaDeEjecuciones) {

                            encontrado = false;

                            for (int i = AmbitoActual.size() - 1; i >= 0; i--) {

                                int ambito = AmbitoActual.get(i);

                                if (tabla.idExiste(
                                        id,
                                        ambito)) {

                                    encontrado = true;
                                    ambitoEncontrado = ambito;

                                    break;
                                }
                            }

                            // ---------------------------------------------
                            // ID NO DECLARADO
                            // ---------------------------------------------

                            if (!encontrado) {

                                registrarError(
                                        tokenActual,
                                        "Identificador no declarado " +
                                                "en los ámbitos accesibles: " +
                                                id,
                                        -3000);
                            }
                        }

                        // =================================================
                        // MATCH IDENTIFICADOR
                        // =================================================

                        if (EstadoDeOperacion) {
                            // Avance 2: tipo del operando. No declarado => Variant
                            // (el error de "no declarado" ya se registró arriba).
                            int tipoSem = LeerCSVComparacion.T_VARIANT;
                            if (AreaDeEjecuciones) {
                                if (encontrado && ambitoEncontrado >= 0) {
                                    tipoSem = tipoSemanticoDeSimbolo(
                                            tabla.obtenerTipo(id, ambitoEncontrado));
                                }
                            } else {
                                tipoSem = tipoSemanticoDeSimbolo(obtenerTipoDeToken(tc));
                            }
                            tiposOperando.put(tokenActual, tipoSem);

                            operandosPila.push(tokenActual);
                            if (infijoActual.length() > 0) {
                                infijoActual.append(" ");
                            }
                            infijoActual.append(id);
                        }

                        ps.pop();

                        lt.removeFirst();

                        log(
                                "Match ID: " + id);

                    }

                    else {

                        registrarError(
                                tokenActual,
                                "Se esperaba un identificador, " +
                                        "se encontró: " +
                                        tokenActual.getLexema(),
                                -2000);

                        lt.removeFirst();

                        ps.pop();
                    }
                }

                // =====================================================
                // TERMINAL NORMAL
                // =====================================================

                else if (cima == tokenActual.getTokenClass()) {

                    // -------------------------------------------------
                    // DIMENSIONES DE ARREGLO
                    // -------------------------------------------------

                    if ((produccionActual == 14
                            &&
                            tokenActual.getTokenClass() == -55)
                            ||
                            (produccionActual == 16
                                    &&
                                    tokenActual.getTokenClass() == -55)) {

                        DimensionesArreglo++;

                        if (TamañoArreglo.isEmpty()) {

                            TamañoArreglo = tokenActual.getLexema();

                        } else {

                            TamañoArreglo = TamañoArreglo
                                    + ","
                                    + tokenActual.getLexema();
                        }
                    }

                    // -------------------------------------------------
                    // MATCH NORMAL
                    // -------------------------------------------------

                    ps.pop();

                    lt.removeFirst();

                    log(
                            "Match: " +
                                    tokenActual.getLexema());

                    // -------------------------------------------------
                    // ALGORITMO INFIJA -> POSTFIJA
                    //
                    // Solo participan los tokens que van llegando mientras
                    // estamos dentro de una operación (EstadoDeOperacion,
                    // delimitada por los marcadores 804/805 de la
                    // producción 44). Usa las pilas ya existentes:
                    // operandosPila termina siendo la salida postfija (se
                    // recorre en orden de inserción al finalizar) y
                    // operadoresPila es la pila de trabajo del algoritmo.
                    // -------------------------------------------------

                    if (EstadoDeOperacion) {
                        procesarTokenDeOperacion(tokenActual);
                    }

                    // -------------------------------------------------
                    // ACTUALIZACIÓN DE ARREGLO
                    //
                    // Cuando llega el ; se considera terminada
                    // la declaración del arreglo.
                    // -------------------------------------------------

                    if (";".equals(
                            tokenActual.getLexema())
                            &&
                            !idArreglo.isEmpty()
                            &&
                            DimensionesArreglo > 0) {

                        tabla.actualizarArreglo(
                                idArreglo,
                                ambitoArreglo,
                                TamañoArreglo,
                                DimensionesArreglo);

                        idArreglo = "";

                        TamañoArreglo = "";

                        DimensionesArreglo = 0;

                        ambitoArreglo = -1;
                    }

                    // -------------------------------------------------
                    // ACTUALIZACIÓN DE FUNCIÓN
                    //
                    // Cuando termina la lista de parámetros.
                    // -------------------------------------------------

                    if (")".equals(
                            tokenActual.getLexema())
                            &&
                            !idFuncion.isEmpty()
                            &&
                            ambitoFuncion >= 0) {

                        tabla.actualizarFuncion(
                                idFuncion,
                                ambitoFuncion,
                                NumParametros);

                        idFuncion = "";

                        NumParametros = 0;

                        ambitoFuncion = -1;
                    }

                }

                // =====================================================
                // ERROR DE TERMINAL
                // =====================================================

                else {

                    registrarError(
                            tokenActual,
                            "Se esperaba terminal " +
                                    cima +
                                    " pero se encontró " +
                                    tokenActual.getTokenClass() +
                                    " ('" +
                                    tokenActual.getLexema() +
                                    "')",
                            -2000);

                    break;
                }
            }

            log(
                    "Pila: " + ps);
        }

        // =========================================================
        // VERIFICACIÓN FINAL
        // =========================================================

        if (lt.isEmpty()
                &&
                !ps.isEmpty()) {

            registrarError(
                    tokenActual,
                    "Bloque incompleto: quedan " +
                            "no terminales sin resolver en la pila " +
                            "al finalizar los tokens. " +
                            "Cima residual: " +
                            tokenActual.getLexema(),
                    -2000);
        }

        boolean exitoso = lt.isEmpty()
                &&
                ps.isEmpty()
                &&
                ContadorCiclos.ERRORES == 0;

        if (exitoso) {

            System.out.println(
                    "\nAnálisis sintáctico correcto.");

        } else {

            System.out.println(
                    "\nAnálisis sintáctico finalizado con "
                            +
                            ContadorCiclos.ERRORES
                            +
                            " error(es) sintáctico(s).");
        }

        if (tokenActual != null
                &&
                !AmbitoActual.isEmpty()) {

            logBuilder.append(
                    "Línea: "
                            +
                            tokenActual.getLinea()
                            +
                            " ---> ÁMBITO "
                            +
                            AmbitoActual.peek()
                            +
                            " ---> CERRADO\n");
        }
    }

    // =========================================================
    // ERRORES
    // =========================================================

    private void registrarError(
            Token t,
            String descripcion,
            int numError) {

        ContadorCiclos.ERRORES++;

        String codigo = String.format(
                "ERR-SYN-%03d",
                ContadorCiclos.ERRORES);

        if (numError == -3000) {
            codigo = String.format(
                "ERR-AMB-%03d",
                ContadorCiclos.ERRORES);
            erroresSintaxis.add(
                    new ErrorEntry(
                            codigo,
                            descripcion,
                            t.getLinea(),
                            "parser",
                            ErrorEntry.Tipo.AMBITO,
                            t.getLexema(),
                            obtenerAmbitoActual()));

            System.out.println(
                    "[SYN-ERR] ln="
                            + t.getLinea()
                            + " col="
                            + t.getColumna()
                            + " | "
                            + descripcion);

        } else if (numError == -2000) {
            erroresSintaxis.add(
                    new ErrorEntry(
                            codigo,
                            descripcion,
                            t.getLinea(),
                            "parser",
                            ErrorEntry.Tipo.SINTAXIS,
                            t.getLexema(),
                            obtenerAmbitoActual()));

            System.out.println(
                    "[SYN-ERR] ln="
                            + t.getLinea()
                            + " col="
                            + t.getColumna()
                            + " | "
                            + descripcion);

        } else {

            codigo = String.format(
                    "ERR-SYN-%d",
                    numError);

            erroresSintaxis.add(
                    new ErrorEntry(
                            codigo,
                            ErrorEntry.definirDescripcionSintaxis(
                                    numError),
                            t.getLinea(),
                            "parser",
                            ErrorEntry.Tipo.SINTAXIS,
                            t.getLexema(),
                            obtenerAmbitoActual()));

            System.out.println(
                    "SYN-ERR "
                            + numError
                            + " "
                            + t.getLinea()
                            + " col="
                            + t.getColumna()
                            + " | "
                            + descripcion);
        }
    }

    // =========================================================
    // LOG
    // =========================================================

    private void log(String msg) {

        if (TRACE) {

            System.out.println(
                    "[TRACE] " + msg);
        }
    }

    // =========================================================
    // ÁMBITOS
    // =========================================================

    private int obtenerAmbitoActual() {
        return AmbitoActual.isEmpty()
                ? ErrorEntry.AMBITO_DESCONOCIDO
                : AmbitoActual.peek();
    }

    private void AbrirAmbito(
            Token tokenActual) {

        AmbitoActual.push(
                ContadorAmbito++);

        logBuilder.append(
                "Línea: "
                        + tokenActual.getLinea()
                        + " ---> ÁMBITO "
                        + AmbitoActual.peek()
                        + " ---> ABIERTO\n");

        // Este es el ámbito hijo real de la función que se acaba
        // de declarar: se registra como su TParr, tal cual lo
        // asignó el parser (no una fórmula).
        if (pendienteAmbitoFuncion) {

            tabla.actualizarTParFuncion(
                    idFuncion,
                    ambitoFuncion,
                    AmbitoActual.peek());

            pendienteAmbitoFuncion = false;
        }
    }

    private void CerrarAmbito(
            Token tokenActual) {

        if (AmbitoActual.size() <= 1) {

            return;
        }

        logBuilder.append(
                "Línea: "
                        + tokenActual.getLinea()
                        + " ---> ÁMBITO "
                        + AmbitoActual.peek()
                        + " ---> CERRADO\n");

        AmbitoActual.pop();
    }

    // =========================================================
    // CLASE DEL SÍMBOLO
    // =========================================================

    private String obtenerClaseDeProduccion(
            int numProduccion,
            Stack<Integer> stack) {

        /*
         * Producciones 12 y 17:
         *
         * Ambas declaran un identificador simple (variable u
         * arreglo). La 17 se reutiliza para cualquier elemento
         * que NO es el primero de una lista "var a, b, c;" —
         * no significa "es arreglo". La clase real (Variable vs
         * Arreglo) se decide más adelante, según si aparecen o
         * no corchetes reales (ver actualizarArreglo en el DAO).
         *
         * Esto evita depender de stack.peek(),
         * porque cuando estamos procesando el ID
         * la cima puede seguir siendo -1000.
         */

        switch (numProduccion) {

            case 7:
                return "Funcion";

            case 12:
            case 17:
                return "Variable";

            case 35, 36:

                NumParametros++;

                return "Parametro";

            case 8:
                return "Constante";

            case 20:
                return "Constante";

            default:
                return "";
        }
    }

    // =========================================================
    // TIPO
    // =========================================================

    private String obtenerTipoDeToken(
            int tokenID) {

        switch (tokenID) {

            case -60:
                return "Cadena";

            case -61:
                return "Binario";

            case -62:
                return "Decimal";

            case -63:
                return "Octal";

            case -64:
                return "Hexadecimal";

            case -65:
                return "Real";

            case -66:
                return "Exponencial";

            case -67:
                return "Booleanas";
            case -107:
                return "Registro";
            default:
                return "";
        }
    }

    // =========================================================
    // INFIJA -> POSTFIJA
    // =========================================================

    /**
     * Procesa, dentro del algoritmo de conversión infija->postfija, un
     * token que llegó por la rama de "TERMINAL NORMAL" (constantes,
     * operadores, paréntesis y tokens de control) mientras
     * EstadoDeOperacion es true. Los identificadores NO pasan por aquí:
     * se agregan directo a operandosPila en el bloque "MATCH
     * IDENTIFICADOR", porque en la pila del parser se representan con
     * el símbolo genérico -1000 y no con su clase real de token.
     */
    private void procesarTokenDeOperacion(Token t) {

        int tc = t.getTokenClass();

        // El texto original se reconstruye tal cual el compilador va
        // consumiendo los tokens de la operación.
        if (infijoActual.length() > 0) {
            infijoActual.append(" ");
        }
        infijoActual.append(t.getLexema());

        // ---------------------------------------------------------
        // OPERANDO (constantes: Binario, Decimal, Octal, Hexadecimal,
        // Real, Exponencial, y el resto del rango de operandos dado).
        // ---------------------------------------------------------
        if (esOperandoDeExpresion(tc)) {
            operandosPila.push(t);
            return;
        }

        // ---------------------------------------------------------
        // PARÉNTESIS: tratamiento especial del algoritmo.
        // ---------------------------------------------------------
        if (tc == -49) { // "("
            operadoresPila.push(t);
            return;
        }

        if (tc == -50) { // ")"
            while (!operadoresPila.isEmpty()
                    && operadoresPila.peek().getTokenClass() != -49) {
                operandosPila.push(operadoresPila.pop());
            }
            if (!operadoresPila.isEmpty()) {
                operadoresPila.pop(); // descarta el "(" que abrió el grupo
            }
            return;
        }

        // ---------------------------------------------------------
        // ++ / -- : unarios POSTfijos. En la salida postfija van
        // exactamente en el mismo lugar en que aparecen en la entrada
        // (justo después de su operando), así que se agregan directo
        // a la salida sin pasar por la pila de operadores.
        // ---------------------------------------------------------
        if (tc == -1 || tc == -2) {
            operandosPila.push(t);
            return;
        }

        // ---------------------------------------------------------
        // OPERADOR: se desapilan a la salida los operadores de mayor
        // o igual prioridad (según asociatividad) antes de apilar el
        // que acaba de llegar.
        // ---------------------------------------------------------
        if (PRECEDENCIA_OPERADOR.containsKey(tc)) {
            while (!operadoresPila.isEmpty()
                    && operadoresPila.peek().getTokenClass() != -49
                    && tienePrioridadParaDesapilar(
                            operadoresPila.peek().getTokenClass(), tc)) {
                operandosPila.push(operadoresPila.pop());
            }
            operadoresPila.push(t);
            return;
        }

        // ---------------------------------------------------------
        // Tokens de control (-7 , / -8 . / -9 ; / -10 :) y el resto de
        // símbolos de agrupamiento ({ } [ ]): por instrucción explícita,
        // NO se meten a la pila de operadores. Se respeta cómo los usan
        // las producciones y solo quedan registrados en el texto
        // original de la operación.
    }

    private boolean esOperandoDeExpresion(int tc) {
        // -54..-68 cubre Binario, Decimal, Octal, Hexadecimal, Real,
        // Exponencial y los identificadores (@, #B, #D, #O, #X, $, ¿, ¡,
        // Letras/cadenas); -107 es Registro. Los identificadores en la
        // práctica llegan por el bloque "MATCH IDENTIFICADOR" (ver
        // arriba), pero se deja el rango completo por si algún día se
        // representan como terminal directo en la pila del parser.
        // Avance 2: también -53 (cadena), -72/-73 (true/false) y -74 (null),
        // que antes se perdían de la salida postfija.
        return (tc >= -68 && tc <= -54) || tc == -107
                || tc == -53 || tc == -72 || tc == -73 || tc == -74;
    }

    private boolean tienePrioridadParaDesapilar(int tokenEnPila, int tokenEntrante) {

        Integer precPila = PRECEDENCIA_OPERADOR.get(tokenEnPila);
        Integer precEntrante = PRECEDENCIA_OPERADOR.get(tokenEntrante);

        if (precPila == null || precEntrante == null) {
            return false;
        }

        if (ASOCIATIVIDAD_DERECHA.contains(tokenEntrante)) {
            return precPila > precEntrante;
        }

        return precPila >= precEntrante;
    }

    /**
     * Se ejecuta al cerrar la operación (marcador 805).
     *
     * Paso 1: vacía lo que quede en operadoresPila hacia operandosPila.
     * Recorriendo operandosPila de fondo a tope (orden de inserción de
     * un Stack/Vector) queda exactamente la expresión en POSTFIJO
     * (es el resultado clásico del algoritmo shunting-yard, esto ya
     * funcionaba bien).
     *
     * Paso 2 (lo que faltaba): esa secuencia postfija se dobla a
     * PREFIJO con el algoritmo estándar basado en pila: se recorre el
     * postfijo de izquierda a derecha; cada operando se apila tal
     * cual; cada operador desapila sus dos operandos (tope = derecho,
     * siguiente = izquierdo) y apila el string
     * "operador izquierdo derecho". Al terminar, lo único que queda en
     * la pila es la expresión completa en prefijo. Esto reutiliza el
     * mismo par de pilas del proyecto; solo se agrega una pila local
     * de Strings (pilaPrefijo) porque el plegado necesita ir
     * combinando texto, no Tokens.
     */
    private void finalizarOperacionPostfija() {

        while (!operadoresPila.isEmpty()) {
            operandosPila.push(operadoresPila.pop());
        }

        List<Token> postfijo = new ArrayList<>(operandosPila);

        // Avance 2: cuádruplas con temporales tipados a partir del postfijo.
        generarOperaciones(postfijo, lineaDondeEstaLaOperacion);

        Stack<String> pilaPrefijo = new Stack<>();
        for (Token t : postfijo) {
            int tc = t.getTokenClass();
            String lex = t.getLexema().trim();

            if (tc == -1 || tc == -2) {
                // Postfijos unarios (++ / --): un solo operando.
                String operando = pilaPrefijo.isEmpty() ? "" : pilaPrefijo.pop();
                pilaPrefijo.push(lex + " " + operando);
            } else if (PRECEDENCIA_OPERADOR.containsKey(tc)) {
                // Operador binario: tope = operando derecho.
                String derecho   = pilaPrefijo.isEmpty() ? "" : pilaPrefijo.pop();
                String izquierdo = pilaPrefijo.isEmpty() ? "" : pilaPrefijo.pop();
                pilaPrefijo.push(lex + " " + izquierdo + " " + derecho);
            } else {
                // Operando (constante o identificador).
                pilaPrefijo.push(lex);
            }
        }

        String prefijo = pilaPrefijo.isEmpty() ? "" : pilaPrefijo.pop();

        escribirOperacionEnArchivo(lineaDondeEstaLaOperacion, prefijo);

        operandosPila.clear();
        operadoresPila.clear();
        infijoActual.setLength(0);
        tiposOperando.clear();
    }

    private void reiniciarArchivoPostfijas() {
        logExpresiones.setLength(0);
        try (FileWriter fw = new FileWriter(ARCHIVO_POSTFIJAS, false)) {
            // Deja el archivo vacío al iniciar un nuevo análisis.
        } catch (IOException e) {
            System.err.println(
                    "[POSTFIJA] No se pudo reiniciar " + ARCHIVO_POSTFIJAS);
            e.printStackTrace();
        }
    }

    private void escribirOperacionEnArchivo(int linea, String prefijo) {

        logExpresiones.append("Linea ").append(linea).append(": ")
                .append(prefijo).append("\n\n");

        try (FileWriter fw = new FileWriter(ARCHIVO_POSTFIJAS, true);
                PrintWriter pw = new PrintWriter(fw)) {

            pw.println("Linea " + linea + ": " + prefijo);
            pw.println();

        } catch (IOException e) {
            System.err.println(
                    "[POSTFIJA] No se pudo escribir en " + ARCHIVO_POSTFIJAS);
            e.printStackTrace();
        }
    }

    // =========================================================
    // AVANCE 2: GENERADOR DE OPERACIONES
    // =========================================================

    /** "Decimal" / "Real" / ... (tabla de símbolos) -> tipo 1..10. Lo demás es Variant. */
    private static int tipoSemanticoDeSimbolo(String tipo) {
        if (tipo == null) return LeerCSVComparacion.T_VARIANT;
        return switch (tipo.trim()) {
            case "Binario"     -> LeerCSVComparacion.T_BIN;
            case "Decimal"     -> LeerCSVComparacion.T_DEC;
            case "Octal"       -> LeerCSVComparacion.T_OCT;
            case "Hexadecimal" -> LeerCSVComparacion.T_HEX;
            case "Real"        -> LeerCSVComparacion.T_REAL;
            case "Exponencial" -> LeerCSVComparacion.T_EXP;
            case "Cadena"      -> LeerCSVComparacion.T_CADENA;
            case "Booleanas"   -> LeerCSVComparacion.T_BOOLEAN;
            default            -> LeerCSVComparacion.T_VARIANT;
        };
    }

    private String nuevoTemporal(int tipo) {
        int n = contadorTemporales.merge(tipo, 1, Integer::sum);
        String nombre = LeerCSVComparacion.prefijoTemporal(tipo) + n;
        tipoDeTemporal.put(nombre, tipo);
        if (estadisticaLineaActual != null) {
            int col = columnaEstadistica(tipo);
            if (col >= 0) estadisticaLineaActual[col]++;
        }
        return nombre;
    }

    /** tipo (1..10) -> columna de estadisticasSemantica; -1 si no tiene columna. */
    private static int columnaEstadistica(int tipo) {
        if (tipo >= LeerCSVComparacion.T_BIN && tipo <= LeerCSVComparacion.T_CADENA) return tipo - 1;
        if (tipo == LeerCSVComparacion.T_BOOLEAN) return 7;
        if (tipo == LeerCSVComparacion.T_VARIANT) return 8;
        return -1;
    }

    private Operando sacar(Stack<Operando> pila) {
        return pila.isEmpty()
                ? new Operando("", LeerCSVComparacion.T_VARIANT)
                : pila.pop();
    }

    private static boolean esAsignacion(int tc) {
        return tc <= -33 && tc >= -44;
    }

    private static String nombreOperacion(int tc) {
        return switch (tc) {
            case -11 -> "suma";
            case -12 -> "resta";
            case -13 -> "multiplicación";
            case -14 -> "división";
            case -15 -> "módulo";
            case -20 -> "menor que";
            case -21 -> "mayor que";
            case -22 -> "menor o igual que";
            case -23 -> "mayor o igual que";
            case -24 -> "igual a";
            case -25 -> "diferente de";
            case -30 -> "AND";
            case -31 -> "OR";
            default  -> (tc <= -20 && tc >= -26) ? "relacional" : "lógico";
        };
    }

    /**
     * Recorre el postfijo y emite "operador,valor1,valor2,temporal".
     * Cada operación genera un temporal cuyo tipo sale de las tablas CSV.
     * Si la tabla dice Error: se registra "Semántica 1" y el temporal queda Variant.
     * Operadores sin tabla (potencia, bit a bit, shifts, igualdad estricta,
     * ternario, unarios): el temporal es Variant, sin error.
     */
    private void generarOperaciones(List<Token> postfijo, int linea) {

        Stack<Operando> pila = new Stack<>();
        StringBuilder bloque = new StringBuilder();
        int[] stats = new int[10];
        estadisticaLineaActual = stats;

        for (Token t : postfijo) {

            int tc = t.getTokenClass();
            String lex = t.getLexema().trim();

            // ++ / -- : unario postfijo, conserva el tipo del operando
            if (tc == -1 || tc == -2) {
                Operando o = sacar(pila);
                String tmp = nuevoTemporal(o.tipo);
                bloque.append(lex).append(",").append(o.texto).append(",").append(tmp).append("\n");
                pila.push(new Operando(tmp, o.tipo));
            }
            // ! y ~ : unarios prefijos (no hay tabla -> Variant)
            else if (tc == -29 || tc == -3) {
                Operando o = sacar(pila);
                String tmp = nuevoTemporal(LeerCSVComparacion.T_VARIANT);
                bloque.append(lex).append(",").append(o.texto).append(",").append(tmp).append("\n");
                pila.push(new Operando(tmp, LeerCSVComparacion.T_VARIANT));
            }
            // asignación: no genera temporal
            else if (esAsignacion(tc)) {
                stats[9]++; // asignación (no genera temporal)
                Operando valor = sacar(pila);
                Operando destino = sacar(pila);
                bloque.append(lex).append(",").append(destino.texto).append(",")
                        .append(valor.texto).append("\n");

                // Formato exacto de asignación: #DX -> TBoolean3
                String formatoAsig = destino.texto + " -> " + valor.texto;
                asignacionesDetalle.computeIfAbsent(linea, k -> new ArrayList<>()).add(formatoAsig);

                // Comprobar compatibilidad de tipos en la asignación
                int tipoDestino = tipoSemanticoDeSimbolo(tabla.obtenerTipo(destino.texto, obtenerAmbitoActual()));
                if (tipoDestino != LeerCSVComparacion.T_VARIANT && valor.tipo != LeerCSVComparacion.T_VARIANT) {
                    int rAsig = comparacion.ObtenerTipoPorTipos(tipoDestino, valor.tipo, -33);
                    if (rAsig < 0) {
                        registrarErrorSemantico(linea, destino.texto + " = " + valor.texto,
                                "Tipos de datos incompatibles en la operación de asignación");
                    }
                }

                pila.push(destino);
            }
            // ternario ?: (sin tabla)
            else if (tc == -32 && pila.size() >= 3) {
                Operando c = sacar(pila);
                Operando b = sacar(pila);
                Operando a = sacar(pila);
                String tmp = nuevoTemporal(LeerCSVComparacion.T_VARIANT);
                bloque.append(lex).append(",").append(a.texto).append(",").append(b.texto)
                        .append(",").append(c.texto).append(",").append(tmp).append("\n");
                pila.push(new Operando(tmp, LeerCSVComparacion.T_VARIANT));
            }
            // operador binario
            else if (PRECEDENCIA_OPERADOR.containsKey(tc)) {
                Operando der = sacar(pila);
                Operando izq = sacar(pila);

                int tipoRes = LeerCSVComparacion.T_VARIANT;

                if (LeerCSVComparacion.tieneTabla(tc)) {
                    int r = comparacion.ObtenerTipoPorTipos(izq.tipo, der.tipo, tc);
                    if (r < 0) {
                        // Concatenar expresión completa para el lexema del error semántico: "54 * #OE"
                        String lexemaCompleto = izq.texto + " " + lex + " " + der.texto;
                        registrarErrorSemantico(linea, lexemaCompleto,
                                "incompatibilidad de tipo " + nombreOperacion(tc));
                        log("Semántica 1: " + izq.texto + "(" + izq.tipo + ") " + lex
                                + " " + der.texto + "(" + der.tipo + ")");
                    } else {
                        tipoRes = r;
                    }
                }

                String tmp = nuevoTemporal(tipoRes);
                bloque.append(lex).append(",").append(izq.texto).append(",")
                        .append(der.texto).append(",").append(tmp).append("\n");
                pila.push(new Operando(tmp, tipoRes));
            }
            // operando (ID o constante)
            else {
                int tipo = tiposOperando.getOrDefault(t, LeerCSVComparacion.tipoDeToken(tc));
                pila.push(new Operando(lex, tipo));
            }
        }

        estadisticaLineaActual = null;

        if (bloque.length() > 0) {
            // Varias operaciones en la misma línea se acumulan en una sola fila.
            int[] acumulado = estadisticasSemantica.computeIfAbsent(linea, k -> new int[10]);
            for (int i = 0; i < 10; i++) acumulado[i] += stats[i];

            logOperaciones.append("Linea ").append(linea).append(":\n\n")
                    .append(bloque).append("\n");
        }
    }

    private void registrarErrorSemantico(int linea, String lexema, String descripcion) {

        ContadorCiclos.ERRORES++;

        erroresSintaxis.add(
                new ErrorEntry(
                        "Semántica 1",
                        descripcion,
                        linea,
                        "parser",
                        ErrorEntry.Tipo.SEMANTICA,
                        lexema,
                        obtenerAmbitoActual()));

        System.out.println("[SEM-ERR] ln=" + linea + " | Semántica 1: " + descripcion);
    }
}