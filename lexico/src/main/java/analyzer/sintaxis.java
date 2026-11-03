package analyzer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
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

    // temporal -> tipo (TVariant1 -> 10, TDec1 -> 2, ...). Nunca se deduce del
    // texto.
    private final Map<String, Integer> tipoDeTemporal = new HashMap<>();

    // Contador independiente por tipo. Se reinicia solo en parsear().
    private final Map<Integer, Integer> contadorTemporales = new HashMap<>();

    // Operaciones generadas ("Linea N:" + cuádruplas) de toda la compilación.
    private final StringBuilder logOperaciones = new StringBuilder();

    // =========================================================
    // AVANCE 1 DE SEMÁNTICA 2: MUESTREO DE LAS REGLAS 1 A 5
    // Solo se registra información que el análisis ya tiene.
    // No se valida nada (ver sección "AVANCE 1 SEMÁNTICA 2").
    // =========================================================
    private static final String ARCHIVO_MUESTREO_SEM2 = "Semantica2-Avance2-DavidAlatorre.txt";

    private final MuestreoSemantico muestreo = new MuestreoSemantico();

    // Último identificador consumido (cualquiera) y el ámbito donde se resolvió.
    private String ultimoId = "";
    private int ultimoIdAmbito = 0;
    // Último identificador consumido fuera de corchetes: es el receptor de una
    // asignación.
    private String ultimoIdFueraIndice = "";

    // Condición de control en curso (IF/ELSEIF/WHILE/DO-WHILE). condClave == 0:
    // ninguna.
    private int condClave = 0;
    private String condDescripcion = "";
    private int condLinea = 0;
    private int condColumna = 0;
    private int condProf = 0;
    private final StringBuilder condTexto = new StringBuilder();

    // Tamaños de pila donde espera su "while" cada "do" abierto.
    private final Set<Integer> doWhilePendientes = new HashSet<>();

    // Accesos a arreglo "id[...]" en curso (pueden anidarse: a[b[1]]).
    private final List<AccesoArreglo> accesosArreglo = new ArrayList<>();

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

        Operando(String texto, int tipo) {
            this.texto = texto;
            this.tipo = tipo;
        }
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
        for (int t : new int[] { -33, -34, -35, -36, -37, -38, -39, -40, -41, -42, -43, -44 }) {
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
        for (int t : new int[] { -24, -25, -26, -27, -28 }) {
            PRECEDENCIA_OPERADOR.put(t, 8);
        }
        // Relacional
        for (int t : new int[] { -20, -21, -22, -23 }) {
            PRECEDENCIA_OPERADOR.put(t, 9);
        }
        // Desplazamiento de bits
        for (int t : new int[] { -17, -18, -19 }) {
            PRECEDENCIA_OPERADOR.put(t, 10);
        }
        // Aditivos
        PRECEDENCIA_OPERADOR.put(-11, 11);
        PRECEDENCIA_OPERADOR.put(-12, 11);
        // Multiplicativos
        for (int t : new int[] { -13, -14, -15 }) {
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

    public String getLogMuestreoSemantica2() {
        return muestreo.generarTabla();
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

        reiniciarMuestreoSemantica2();

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

                    enEncabezadoFor = false;
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
                    registrarMuestreoPorProduccion(resultado, tokenActual, ps);
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

                            // Si ya había un arreglo pendiente en la misma lista
                            // ("var #DA[5], @B[3];") se guarda ahora, antes de
                            // pisar idArreglo; antes solo se guardaba el último
                            // al llegar el ';' y los anteriores quedaban como Variable.
                            if (!idArreglo.isEmpty() && DimensionesArreglo > 0) {
                                tabla.actualizarArreglo(
                                        idArreglo,
                                        ambitoArreglo,
                                        TamañoArreglo,
                                        DimensionesArreglo);
                            }

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

                            // Avance 3 Sem2: contexto semántico de la función/procedimiento.
                            // Un id con nombre de registro (-107) es PROCEDIMIENTO; uno tipado es FUNCIÓN.
                            defPendiente = new FuncInfo(id, obtenerTipoDeToken(tc), tc == -107,
                                    AmbitoActual.peek(), tokenActual.getLinea(), tokenActual.getColumna());
                        }
                        // =================================================
                        // DECLARACIONES
                        // =================================================
                        int ambitoEncontrado = -1;

                        // Encabezado de un FOR: la gramática (marcador 801) abre
                        // un área de declaración para la primera expresión. Un
                        // identificador que ya existe en un ámbito accesible se
                        // reutiliza; si no existe, se declara en el ámbito del FOR.
                        boolean encabezadoFor = AreaDeDeclaraciones
                                && enEncabezadoFor
                                && produccionActual == 44;

                        if (encabezadoFor) {
                            encontrado = false;
                            for (int i = AmbitoActual.size() - 1; i >= 0; i--) {
                                int amb = AmbitoActual.get(i);
                                if (tabla.idExiste(id, amb)) {
                                    encontrado = true;
                                    ambitoEncontrado = amb;
                                    break;
                                }
                            }
                            if (!encontrado) {
                                ambitoEncontrado = AmbitoActual.peek();
                                tabla.insertar(
                                        id,
                                        obtenerTipoDeToken(tc),
                                        "Variable",
                                        ambitoEncontrado,
                                        null, null, null, null);
                                encontrado = true;
                                log("Símbolo declarado en encabezado FOR: "
                                        + id + " | ámbito=" + ambitoEncontrado);
                            }
                        } else if (AreaDeDeclaraciones) {
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
                                // Avance 3 Sem2: se distingue la clase Procedimiento de Funcion.
                                if (produccionActual == 7 && tc == -107) {
                                    clase = "Procedimiento";
                                }
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

                                    if (defPendiente != null) {
                                        funciones.put(id + "|" + ambito, defPendiente);
                                    }

                                    // Avance 3 (ajuste): la declaración registra 1120
                                    // (función) o 1110 (procedimiento).
                                    if (insertado) {
                                        registrarDeclaracionCallable(tokenActual, id, tc);
                                    }
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

                                    if (insertado && !funcStack.isEmpty()) {
                                        funcStack.peek().paramNombres.add(id);
                                        funcStack.peek().paramTipos.add(tipo);
                                    }
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
                        // Avance 1 Semántica 2: seguimiento para el muestreo
                        // =================================================
                        boolean modoUso = AreaDeEjecuciones || encabezadoFor;
                        ultimoId = id;
                        ultimoIdAmbito = (modoUso && encontrado && ambitoEncontrado >= 0)
                                ? ambitoEncontrado
                                : AmbitoActual.peek();
                        if (accesosArreglo.isEmpty()) {
                            ultimoIdFueraIndice = id;
                        }
                        if (condClave != 0 || !accesosArreglo.isEmpty()) {
                            String tipoId = modoUso
                                    ? ((encontrado && ambitoEncontrado >= 0)
                                            ? tabla.obtenerTipo(id, ambitoEncontrado)
                                            : null)
                                    : obtenerTipoDeToken(tc);
                            alimentarMuestreo(tokenActual,
                                    (tipoId == null || tipoId.isEmpty())
                                            ? "No declarado"
                                            : tipoId);
                        }

                        // Avance 2 Semántica 2: reglas 10 (1100), 7 (++/--) y
                        // alimentación de las capturas de expresión.
                        procesarIdSemantica2(tokenActual, id, tc, modoUso,
                                encontrado, ambitoEncontrado);

                        // =================================================
                        // MATCH IDENTIFICADOR
                        // =================================================

                        if (EstadoDeOperacion && llamadaOpProf > 0) {
                            llamadaOpAcumular(id); // argumento de una llamada: no es operando de la operación externa
                        } else if (EstadoDeOperacion) {
                            // Avance 2: tipo del operando. No declarado => Variant
                            // (el error de "no declarado" ya se registró arriba).
                            int tipoSem = LeerCSVComparacion.T_VARIANT;
                            if (modoUso) {
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

                    // Avance 1 Semántica 2: el "while" que cierra un do-while
                    // (se reconoce por la posición que ocupa en la pila).
                    if (cima == -95 && doWhilePendientes.remove(ps.size())) {
                        iniciarCondicion(1013, "DO-WHILE", tokenActual);
                    }

                    ps.pop();

                    lt.removeFirst();

                    log(
                            "Match: " +
                                    tokenActual.getLexema());

                    alimentarMuestreo(tokenActual, null);

                    // Avance 2 Semántica 2: cierre de SWITCH (su "}") y capturas.
                    if (cima == -46 && !switches.isEmpty()
                            && switches.peek().base == ps.size()) {
                        switches.pop();
                    }
                    alimentarCapturas(tokenActual);

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

                    if (tokenActual.getTokenClass() == -9 /* ";" (el lexema puede traer \r en archivos CRLF) */
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

                    if (tokenActual.getTokenClass() == -50 /* ")" (el lexema puede traer \r en archivos CRLF) */
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

        // Avance 1 Semántica 2: se escribe el TXT de muestreo
        String rutaMuestreo = muestreo.guardarArchivo(ARCHIVO_MUESTREO_SEM2);
        if (rutaMuestreo != null) {
            System.out.println("[MUESTREO] Archivo generado: " + rutaMuestreo
                    + " (" + muestreo.cantidad() + " registros)");
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
    // AVANCE 1 SEMÁNTICA 2: MUESTREO DE LAS REGLAS 1 A 5
    //
    // Estos métodos NO validan nada. Solo observan por dónde pasa el
    // análisis y anotan, para cada regla, la información que el
    // compilador ya tiene (línea, lexema, ámbito, datos de la tabla
    // de símbolos). La "situación" es siempre "Activada" más los datos
    // disponibles; no se compara ningún tipo, dimensión ni rango.
    //
    // 1010 IF -> producción 87 (NT STATU con "if")
    // 1011 ELSEIF -> producción 98 (NT del else-part con "elseif")
    // 1012 WHILE -> producción 90 (NT STATU con "while")
    // 1013 DO-WHILE -> producción 91 (do) y match del "while" de cierre
    // 1020 = -> producción 38 (NT ASIG)
    // 1021 += -> producción 39
    // 1022 -= *= /= -> producciones 40, 41 y 42
    // 1030/1040/1050 -> producción 81 (NT ARR: acceso "id[...]"),
    // se registran al cerrar el "]"
    // =========================================================

    private static class AccesoArreglo {
        final String id;
        final int linea;
        final int columna;
        final String tarr; // tamaños declarados, ej. "3,4" (null si no es arreglo)
        final String dimarr; // número de dimensiones declaradas (null si no es arreglo)
        final boolean existe; // el identificador está declarado en algún ámbito accesible
        final String clase; // Variable, Arreglo, Parametro, Funcion...
        boolean abierto = false;
        int par = 0;
        final List<List<String>> idxLexemas = new ArrayList<>();
        final List<List<Token>> idxToks = new ArrayList<>();

        AccesoArreglo(String id, int linea, int columna, String tarr, String dimarr,
                boolean existe, String clase) {
            this.id = id;
            this.linea = linea;
            this.columna = columna;
            this.tarr = tarr;
            this.dimarr = dimarr;
            this.existe = existe;
            this.clase = clase;
        }

        void nuevoIndice() {
            idxLexemas.add(new ArrayList<>());
            idxToks.add(new ArrayList<>());
        }

        void agregar(String lexema, Token t) {
            if (idxLexemas.isEmpty()) {
                nuevoIndice();
            }
            idxLexemas.get(idxLexemas.size() - 1).add(lexema);
            idxToks.get(idxToks.size() - 1).add(t);
        }
    }

    private void reiniciarMuestreoSemantica2() {
        muestreo.limpiar();
        ultimoId = "";
        ultimoIdAmbito = 0;
        ultimoIdFueraIndice = "";
        condClave = 0;
        condProf = 0;
        condTexto.setLength(0);
        doWhilePendientes.clear();
        accesosArreglo.clear();
        reiniciarSemantica2Avance2();
    }

    private void registrarMuestreoPorProduccion(int produccion, Token t, Stack<Integer> ps) {
        switch (produccion) {
            case 87 -> iniciarCondicion(1010, "IF", t);
            case 98 -> iniciarCondicion(1011, "ELSEIF", t);
            case 90 -> iniciarCondicion(1012, "WHILE", t);
            // Ya apiladas -50 35 -49 -95 24 -96 (-96 en la cima): el -95
            // queda dos posiciones por debajo de la cima actual.
            case 91 -> doWhilePendientes.add(ps.size() - 2);
            case 38 -> registrarAsignacion(1020, "Asignación básica", t);
            case 39 -> registrarAsignacion(1021, "Asignación +=", t);
            case 40, 41, 42 -> registrarAsignacion(1022,
                    "Asignación compuesta (" + t.getLexema().trim() + ")", t);
            case 81 -> abrirAccesoArreglo(t);
            // ---- Avance 2 de Semántica 2 (reglas 6 a 10) ----
            case 93 -> iniciarCapturaParen(CAP_FOR, t, null);
            case 94 -> {
                // Ya apiladas -99 -49 35 -50 -45 30 -46: el "}" de cierre
                // (-46) queda siete posiciones por debajo del tope.
                SwitchCtx sw = new SwitchCtx(ps.size() - 7);
                switches.push(sw);
                iniciarCapturaParen(CAP_SWITCH, t, sw);
            }
            case 107 -> capCase = new CapCase(t);
            case 45, 46 -> unarioPendiente = t;
            case 47 -> iniciarCapturaParen(CAP_NOT, t, null);
            // ---- Avance 3 de Semántica 2 (reglas 11 a 16) ----
            case 52 -> iniciarLlamada(t); // ID X1 con '(': llamada a función/procedimiento
            case 92 -> iniciarReturn(t); // STATU -> return OR
            case 48 -> iniciarCapturaParen(CAP_TILDE, t, null);
            default -> {
            }
        }
    }

    private void registrarAsignacion(int regla, String descripcion, Token operador) {
        muestreo.registrar(
                operador.getLinea(),
                regla,
                descripcion,
                "Activada",
                ultimoIdFueraIndice,
                obtenerAmbitoActual(),
                operador.getColumna());
        validarClaseReceptor(operador);
    }

    // ---------- Regla 1: condiciones ----------

    private void iniciarCondicion(int clave, String descripcion, Token t) {
        condClave = clave;
        condDescripcion = descripcion;
        condLinea = t.getLinea();
        condColumna = t.getColumna();
        condProf = 0;
        condTexto.setLength(0);
    }

    private void alimentarCondicion(Token t) {
        if (condClave == 0) {
            return;
        }
        int tc = t.getTokenClass();
        String lex = t.getLexema().trim();

        if (tc == -49) { // (
            condProf++;
            if (condProf == 1) {
                return; // paréntesis exterior: no es parte de EXP
            }
        } else if (tc == -50) { // )
            if (condProf == 0) {
                return;
            }
            condProf--;
            if (condProf == 0) {
                muestreo.registrar(
                        condLinea,
                        condClave,
                        condDescripcion,
                        "Activada",
                        condTexto.toString(),
                        obtenerAmbitoActual(),
                        condColumna);
                condClave = 0;
                return;
            }
        } else if (condProf == 0) {
            return; // palabra clave u otros tokens antes del "("
        }

        if (condTexto.length() > 0) {
            condTexto.append(' ');
        }
        condTexto.append(lex);
    }

    // ---------- Reglas 3, 4 y 5: accesos a arreglo ----------

    private void abrirAccesoArreglo(Token corcheteAbre) {
        // El ID recién consumido es el arreglo. Se resuelve en los ámbitos
        // accesibles (no solo en el último ámbito visto) y se lee su clase y sus
        // dimensiones/tamaños de la tabla de símbolos.
        String id = ultimoId;
        int amb = id.isEmpty() ? -1 : buscarAmbitoSimbolo(id);
        String clase = "";
        String[] datos = null;
        if (amb >= 0) {
            String c = tabla.obtenerClase(id, amb);
            clase = c == null ? "" : c.trim();
            datos = tabla.obtenerDatosArreglo(id, amb);
        }

        accesosArreglo.add(new AccesoArreglo(
                id,
                corcheteAbre.getLinea(),
                corcheteAbre.getColumna(),
                datos != null ? datos[0] : null,
                datos != null ? datos[1] : null,
                amb >= 0,
                clase));
    }

    private void alimentarAccesosArreglo(Token t, String tipoId) {
        int n = accesosArreglo.size();
        if (n == 0) {
            return;
        }
        int tc = t.getTokenClass();
        String lex = t.getLexema().trim();

        // Los accesos exteriores solo acumulan lo que pasa dentro de sus
        // corchetes (ej. a[b[1]] -> el índice de "a" es "b [ 1 ]").
        for (int i = 0; i < n - 1; i++) {
            AccesoArreglo exterior = accesosArreglo.get(i);
            if (exterior.abierto) {
                exterior.agregar(lex, t);
            }
        }

        AccesoArreglo a = accesosArreglo.get(n - 1);

        if (tc == -47 && !a.abierto) { // [
            a.abierto = true;
            a.nuevoIndice();
            return;
        }
        if (!a.abierto) {
            return;
        }
        if (tc == -48 && a.par == 0) { // ]
            accesosArreglo.remove(n - 1);
            registrarAccesoArreglo(a);
            return;
        }
        if (tc == -7 && a.par == 0) { // , separa índices
            a.nuevoIndice();
            return;
        }
        if (tc == -49) {
            a.par++;
        } else if (tc == -50 && a.par > 0) {
            a.par--;
        }
        a.agregar(lex, t);
    }

    /**
     * Reglas 3, 4 y 5 sobre un acceso id[i1, i2, ...] ya cerrado:
     *  1030 cantidad de índices = dimensiones declaradas (y que id sea arreglo)
     *  1040 cada índice debe ser entero (Decimal, Octal, Binario o Hexadecimal)
     *  1050 cada índice debe estar en 0..tamaño-1 de su dimensión (slide 7:
     *       declarado #DmiLista[3], el acceso [3] es ERROR). Solo se verifica si
     *       el índice es una constante calculable en compilación.
     * Si el identificador no está declarado no se valida nada: ya lo reporta 1100.
     */
    private void registrarAccesoArreglo(AccesoArreglo a) {

        if (!a.existe) {
            return; // no declarado: sin símbolo no hay dimensiones, tipos ni rangos que validar
        }

        int usados = a.idxLexemas.size();
        int base = a.columna * 100;

        // Regla 3 (1030): ¿es un arreglo?
        if (!"Arreglo".equals(a.clase)) {
            filaSitConError(a.linea, 1030, false, null,
                    a.id + " no es un arreglo (clase "
                            + (a.clase.isEmpty() ? "desconocida" : a.clase)
                            + "); no puede usarse con índices",
                    a.id, base);
            return; // lo demás sería un error secundario
        }

        String[] tamanos = a.tarr == null ? new String[0] : a.tarr.split(",");
        int declaradas = tamanos.length;
        try {
            if (a.dimarr != null) {
                declaradas = Integer.parseInt(a.dimarr.trim());
            }
        } catch (NumberFormatException e) {
            // se conserva la cantidad deducida de los tamaños
        }

        // Regla 3 (1030): cantidad de índices = dimensiones declaradas
        filaSitConError(a.linea, 1030, usados == declaradas,
                "dimensiones declaradas=" + declaradas + ", índices usados=" + usados,
                "se usaron " + usados + " índice(s) y " + a.id + " declara "
                        + declaradas + " dimensión(es)",
                a.id, base);

        for (int k = 0; k < usados; k++) {
            List<Token> toks = a.idxToks.get(k);
            String textoIdx = String.join(" ", a.idxLexemas.get(k));

            // Regla 4 (1040): el índice debe ser entero. Variant (no declarado /
            // sin tipo conocido) se acepta: su error ya lo da otra regla.
            int tipo = evaluar(toks).tipo;
            boolean entero = esEntero(tipo) || tipo == LeerCSVComparacion.T_VARIANT;
            filaSitConError(a.linea, 1040, entero,
                    "tipo del índice: " + nombreTipoSem(tipo),
                    "el índice " + (k + 1) + " de " + a.id
                            + " debe ser entero (Decimal, Octal, Binario o Hexadecimal), se encontró "
                            + nombreTipoSem(tipo),
                    textoIdx, base + k + 1);

            // Regla 5 (1050): solo si el índice es entero y existe esa dimensión.
            if (!entero || k >= tamanos.length) {
                continue; // sin tipo válido o sin dimensión declarada no hay rango que comparar
            }
            long tam;
            try {
                tam = Long.parseLong(tamanos[k].trim());
            } catch (NumberFormatException e) {
                continue;
            }
            Long valor = valorConstanteEntero(toks);
            if (valor == null) {
                filaSitConError(a.linea, 1050, true,
                        "dimensión " + (k + 1) + ", tamaño declarado=" + tam
                                + "; índice no constante, rango no verificable en compilación",
                        null, textoIdx, base + k + 1);
            } else {
                filaSitConError(a.linea, 1050, valor >= 0 && valor < tam,
                        "dimensión " + (k + 1) + ", tamaño declarado=" + tam + ", índice=" + valor,
                        "el índice " + valor + " está fuera del rango 0.." + (tam - 1)
                                + " de la dimensión " + (k + 1) + " de " + a.id,
                        textoIdx, base + k + 1);
            }
        }
    }

    /**
     * Valor de una expresión formada solo por constantes Decimal con + - * y
     * paréntesis (ej. "3+6", "4+70"); null si no es una constante calculable.
     */
    private static Long valorConstanteEntero(List<Token> toks) {
        if (toks == null || toks.isEmpty()) {
            return null;
        }
        int[] pos = { 0 };
        Long v = constExpr(toks, pos);
        return (v != null && pos[0] == toks.size()) ? v : null;
    }

    private static Long constExpr(List<Token> t, int[] p) {
        Long v = constTermino(t, p);
        while (v != null && p[0] < t.size()) {
            int tc = t.get(p[0]).getTokenClass();
            if (tc != -11 && tc != -12) {
                break;
            }
            p[0]++;
            Long r = constTermino(t, p);
            if (r == null) {
                return null;
            }
            v = tc == -11 ? v + r : v - r;
        }
        return v;
    }

    private static Long constTermino(List<Token> t, int[] p) {
        Long v = constFactor(t, p);
        while (v != null && p[0] < t.size() && t.get(p[0]).getTokenClass() == -13) {
            p[0]++;
            Long r = constFactor(t, p);
            if (r == null) {
                return null;
            }
            v = v * r;
        }
        return v;
    }

    private static Long constFactor(List<Token> t, int[] p) {
        if (p[0] >= t.size()) {
            return null;
        }
        Token tok = t.get(p[0]);
        int tc = tok.getTokenClass();
        if (tc == -11 || tc == -12) { // signo unario
            p[0]++;
            Long v = constFactor(t, p);
            return v == null ? null : (tc == -12 ? -v : v);
        }
        if (tc == -49) { // ( expr )
            p[0]++;
            Long v = constExpr(t, p);
            if (v == null || p[0] >= t.size() || t.get(p[0]).getTokenClass() != -50) {
                return null;
            }
            p[0]++;
            return v;
        }
        if (tc == -55) { // constante Decimal
            try {
                long v = Long.parseLong(tok.getLexema().trim());
                p[0]++;
                return v;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null; // identificador, real, etc.: no es constante calculable
    }

    /** Nombre del tipo de un token, solo con lo que el compilador ya sabe de él. */
    private String tipoDeTokenParaMuestreo(int tc, String tipoId) {
        if (tipoId != null) {
            return tipoId; // identificador: tipo de la tabla de símbolos
        }
        boolean constante = (tc <= -53 && tc >= -59) || tc == -72 || tc == -73;
        if (!constante) {
            return null;
        }
        return switch (LeerCSVComparacion.tipoDeToken(tc)) {
            case LeerCSVComparacion.T_BIN -> "Binario";
            case LeerCSVComparacion.T_DEC -> "Decimal";
            case LeerCSVComparacion.T_OCT -> "Octal";
            case LeerCSVComparacion.T_HEX -> "Hexadecimal";
            case LeerCSVComparacion.T_REAL -> "Real";
            case LeerCSVComparacion.T_EXP -> "Exponencial";
            case LeerCSVComparacion.T_CADENA -> "Cadena";
            case LeerCSVComparacion.T_BOOLEAN -> "Booleanas";
            default -> null;
        };
    }

    /** Punto único por el que pasa cada token consumido, para el muestreo. */
    private void alimentarMuestreo(Token t, String tipoId) {
        alimentarCondicion(t);
        alimentarAccesosArreglo(t, tipoId);
    }

    // =========================================================
    // AVANCE 2 DE SEMÁNTICA 2: REGLAS 6 A 10
    //
    // 1060/1061 SWITCH-CASE | 1070 operador unario | 1080..1084 FOR
    // 1090 clase del receptor de una asignación | 1100 identificador declarado
    //
    // A diferencia del Avance 1 (solo muestreo), estas reglas SÍ validan:
    // cada una escribe una fila "ACEPT" o "ERROR (motivo)" en el muestreo y,
    // si es error, agrega un ErrorEntry (Tipo.SEMANTICA) a erroresSintaxis.
    //
    // El tipo de una expresión completa (condición del FOR, expresión del
    // SWITCH, valor de un CASE...) NO lo calcula la maquinaria infija->postfija
    // (esa solo cubre asignaciones), así que aquí se captura la lista de tokens
    // de la expresión y se evalúa con las mismas tablas CSV (LeerCSVComparacion)
    // y la misma tabla de símbolos del proyecto.
    // =========================================================

    private static final int CAP_SWITCH = 1;
    private static final int CAP_NOT = 2;
    private static final int CAP_TILDE = 3;
    private static final int CAP_FOR = 4;

    /** Lo que el análisis resolvió de un identificador en el momento de consumirlo. */
    private static class IdInfo {
        final boolean declarado;
        final String tipo;
        final String clase;
        final int ambito;

        IdInfo(boolean declarado, String tipo, String clase, int ambito) {
            this.declarado = declarado;
            this.tipo = tipo;
            this.clase = clase;
            this.ambito = ambito;
        }
    }

    /** Captura de los tokens entre un "(" y su ")" (SWITCH, FOR, ! y ~). */
    private static class CapParen {
        final int clase;
        final Token origen;
        final SwitchCtx sw;
        final List<Token> toks = new ArrayList<>();
        boolean iniciada = false;
        int par = 0;

        CapParen(int clase, Token origen, SwitchCtx sw) {
            this.clase = clase;
            this.origen = origen;
            this.sw = sw;
        }
    }

    /** Captura de la expresión de un CASE (hasta su ":"). */
    private static class CapCase {
        final Token origen;
        final List<Token> toks = new ArrayList<>();
        boolean iniciada = false;
        int par = 0, brk = 0, tern = 0;

        CapCase(Token origen) {
            this.origen = origen;
        }
    }

    /** Un SWITCH abierto: sirve para validar sus CASE (pueden anidarse). */
    private static class SwitchCtx {
        final int base; // posición, en la pila del parser, de su "}"
        String texto = "";
        Integer tipoRef = null; // tipo contra el que se comparan los CASE
        SwitchCtx(int base) {
            this.base = base;
        }
    }

    /** Resultado de evaluar una expresión. */
    private static class Val {
        final int tipo;
        Token idSimple = null; // si la expresión es un identificador solo
        boolean arreglo = false; // ese identificador es de clase Arreglo (sin índice)

        Val(int tipo) {
            this.tipo = tipo;
        }
    }

    private final Map<Token, IdInfo> idInfos = new IdentityHashMap<>();
    private final List<CapParen> capsParen = new ArrayList<>();
    private final Stack<SwitchCtx> switches = new Stack<>();
    private CapCase capCase = null;
    private Token unarioPendiente = null;
    private Token ultimoIdFueraTok = null;
    private boolean enEncabezadoFor = false;

    private void reiniciarSemantica2Avance2() {
        funciones.clear();
        funcStack.clear();
        defPendiente = null;
        ultimoIdTok = null;
        capReturn = null;
        llamadaOpPendiente = false;
        llamadaOpProf = 0;
        llamadaOpCallee = null;
        llamadaOpTexto.setLength(0);
        idInfos.clear();
        capsParen.clear();
        switches.clear();
        capCase = null;
        unarioPendiente = null;
        ultimoIdFueraTok = null;
        enEncabezadoFor = false;
    }

    // ---------- Registro de filas y errores ----------

    private static String descripcionRegla(int clave) {
        return switch (clave) {
            case 1030 -> "AR-DIM";
            case 1040 -> "AR-ENT";
            case 1050 -> "AR-RANG";
            case 1060 -> "TIPO DEL SWITCH";
            case 1061 -> "CASE DEL MISMO TIPO";
            case 1070 -> "OPERADOR UNARIO EN VALOR NUMÉRICO";
            case 1080 -> "FOR INICIALIZACIÓN";
            case 1081 -> "ID COMPARACIÓN";
            case 1082 -> "INCREMENTO O DECREMENTO";
            case 1083 -> "MANEJO DE CADENAS";
            case 1084 -> "MANEJO DE ARREGLOS";
            case 1090 -> "TIPOS VÁLIDOS PARA RECIBIR VALOR";
            case 1100 -> "VARIABLE DECLARADA";
            case 1110 -> "PROCEDIMIENTO";
            case 1120 -> "FUNCIONES";
            case 1130 -> "RETURN DE FUNCION";
            case 1131 -> "TIPO DE RETORNO IGUAL";
            case 1140 -> "NO RETURN DE PROCEDIMIENTO";
            case 1150 -> "PARAMETROS DECLARADOS";
            case 1160 -> "PARAMETROS DE TIPO";
            default -> "";
        };
    }

    /** Escribe la fila en el muestreo. Devuelve false si era un duplicado. */
    private boolean fila(int linea, int clave, boolean ok, String motivo,
            String lexema, int pos) {
        return muestreo.registrar(
                linea, clave, descripcionRegla(clave),
                ok ? "ACEPT" : "ERROR (" + motivo + ")",
                lexema, obtenerAmbitoActual(), pos);
    }

    /** Fila + ErrorEntry cuando la regla falla. */
    private void filaConError(int linea, int clave, boolean ok, String motivo,
            String lexema, int pos) {
        filaSitConError(linea, clave, ok, null, motivo, lexema, pos);
    }

    /**
     * Igual que filaConError, pero la situación aceptada puede llevar detalle:
     * "ACEPT (detalle)". Si falla: "ERROR (motivo)" + ErrorEntry (sin duplicar).
     */
    private void filaSitConError(int linea, int clave, boolean ok, String detalleOk,
            String motivo, String lexema, int pos) {
        String situacion = ok
                ? ((detalleOk == null || detalleOk.isEmpty()) ? "ACEPT" : "ACEPT (" + detalleOk + ")")
                : "ERROR (" + motivo + ")";
        boolean nueva = muestreo.registrar(
                linea, clave, descripcionRegla(clave), situacion,
                lexema, obtenerAmbitoActual(), pos);
        if (!ok && nueva) {
            ContadorCiclos.ERRORES++;
            erroresSintaxis.add(new ErrorEntry(
                    "Semántica 2",
                    "Semántica 2 (" + clave + "): " + motivo,
                    linea,
                    "parser",
                    ErrorEntry.Tipo.SEMANTICA,
                    lexema,
                    obtenerAmbitoActual()));
            System.out.println("[SEM2-ERR] ln=" + linea + " | " + clave + " | " + motivo);
        }
    }

    private static String nombreTipoSem(int t) {
        switch (t) {
            case LeerCSVComparacion.T_BIN: return "Binario";
            case LeerCSVComparacion.T_DEC: return "Decimal";
            case LeerCSVComparacion.T_OCT: return "Octal";
            case LeerCSVComparacion.T_HEX: return "Hexadecimal";
            case LeerCSVComparacion.T_REAL: return "Real";
            case LeerCSVComparacion.T_EXP: return "Exponencial";
            case LeerCSVComparacion.T_CADENA: return "Cadena";
            case LeerCSVComparacion.T_CARACTER: return "Caracter";
            case LeerCSVComparacion.T_BOOLEAN: return "Booleanas";
            default: return "Variant";
        }
    }

    private static String textoDe(List<Token> toks) {
        StringBuilder sb = new StringBuilder();
        for (Token t : toks) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(t.getLexema().trim());
        }
        return sb.toString();
    }

    private static boolean esIdToken(int tc) {
        return (tc >= -67 && tc <= -60) || tc == -107;
    }

    private static boolean esEntero(int t) {
        return t == LeerCSVComparacion.T_BIN || t == LeerCSVComparacion.T_DEC
                || t == LeerCSVComparacion.T_OCT || t == LeerCSVComparacion.T_HEX;
    }

    // ---------- Cada identificador consumido (reglas 10 y 7) ----------

    private void procesarIdSemantica2(Token t, String id, int tc, boolean uso,
            boolean encontradoId, int ambitoEnc) {

        ultimoIdTok = t; // Avance 3: callee potencial de una llamada

        IdInfo info;
        if (uso && encontradoId && ambitoEnc >= 0) {
            info = new IdInfo(true,
                    tabla.obtenerTipo(id, ambitoEnc),
                    tabla.obtenerClase(id, ambitoEnc),
                    ambitoEnc);
        } else if (uso) {
            info = new IdInfo(false, null, null, -1);
        } else {
            info = new IdInfo(true, obtenerTipoDeToken(tc), "", obtenerAmbitoActual());
        }
        idInfos.put(t, info);

        if (accesosArreglo.isEmpty()) {
            ultimoIdFueraTok = t;
        }

        // Regla 10 (1100): solo para USOS. El error ya lo registra el parser
        // como ERR-AMB; aquí solo se deja la fila del muestreo (sin duplicar).
        if (uso) {
            fila(t.getLinea(), 1100, info.declarado,
                    "identificador no declarado en los ámbitos accesibles",
                    id, t.getColumna());
        }

        // Regla 7 (1070) para ++ / --: el identificador debe ser Decimal (#D).
        // Variant (no declarado) se acepta; el error de 1100 ya lo cubre.
        if (unarioPendiente != null) {
            Token op = unarioPendiente;
            unarioPendiente = null;
            int tp = (uso && info.declarado)
                    ? tipoSemanticoDeSimbolo(info.tipo)
                    : LeerCSVComparacion.T_VARIANT;
            boolean ok = tp == LeerCSVComparacion.T_DEC || tp == LeerCSVComparacion.T_VARIANT;
            filaConError(op.getLinea(), 1070, ok,
                    "el operando de " + op.getLexema().trim()
                            + " debe ser Decimal (#D), se encontró " + nombreTipoSem(tp),
                    id, op.getColumna());
        }

        alimentarCapturas(t);
    }

    // ---------- Regla 9 (1090): clase del receptor ----------

    private void validarClaseReceptor(Token operador) {
        if (ultimoIdFueraTok == null) {
            return;
        }
        IdInfo info = idInfos.get(ultimoIdFueraTok);
        if (info == null) {
            return;
        }
        String id = ultimoIdFueraTok.getLexema().trim();
        boolean ok;
        String motivo = "";
        if (!info.declarado) {
            ok = true; // Variant: se acepta como variable (solo para esta regla)
        } else {
            String clase = info.clase == null ? "" : info.clase.trim();
            ok = clase.equals("Variable") || clase.equals("Parametro")
                    || clase.equals("Arreglo");
            if (!ok) {
                motivo = id + " es de clase "
                        + (clase.isEmpty() ? "desconocida" : clase)
                        + "; solo se asigna a Variable, Parametro o Arreglo";
            }
        }
        filaConError(operador.getLinea(), 1090, ok, motivo,
                ok ? id : id + " es " + (info.clase == null ? "?" : info.clase.toLowerCase()),
                operador.getColumna());
    }

    // ---------- Capturas de expresión ----------

    private void iniciarCapturaParen(int clase, Token origen, SwitchCtx sw) {
        if (clase == CAP_FOR) {
            enEncabezadoFor = true;
        }
        capsParen.add(new CapParen(clase, origen, sw));
    }

    /** Punto único por el que pasa cada terminal consumido (ID o no). */
    private void alimentarCapturas(Token t) {
        int tc = t.getTokenClass();

        List<CapParen> terminadas = null;
        for (Iterator<CapParen> it = capsParen.iterator(); it.hasNext();) {
            CapParen c = it.next();
            if (!c.iniciada) {
                if (tc == -49) {
                    c.iniciada = true;
                    c.par = 1;
                }
                continue;
            }
            if (tc == -49) {
                c.par++;
                c.toks.add(t);
            } else if (tc == -50) {
                c.par--;
                if (c.par == 0) {
                    it.remove();
                    if (terminadas == null) {
                        terminadas = new ArrayList<>();
                    }
                    terminadas.add(c);
                } else {
                    c.toks.add(t);
                }
            } else {
                c.toks.add(t);
            }
        }

        CapCase caso = null;
        if (capCase != null) {
            CapCase k = capCase;
            if (!k.iniciada) {
                if (tc == -102) {
                    k.iniciada = true;
                }
            } else {
                boolean plano = k.par == 0 && k.brk == 0;
                if (tc == -49) {
                    k.par++;
                } else if (tc == -50) {
                    k.par--;
                } else if (tc == -47) {
                    k.brk++;
                } else if (tc == -48) {
                    k.brk--;
                } else if (tc == -32 && plano) {
                    k.tern++;
                }
                if (tc == -10 && plano) {
                    if (k.tern > 0) {
                        k.tern--;
                        k.toks.add(t);
                    } else {
                        capCase = null;
                        caso = k;
                    }
                } else {
                    k.toks.add(t);
                }
            }
        }

        CapReturn ret = null;
        if (capReturn != null) {
            CapReturn k = capReturn;
            if (!k.iniciada) {
                if (tc == -97) {
                    k.iniciada = true;
                }
            } else {
                boolean plano = k.par == 0 && k.brk == 0;
                if (plano && esFinDeReturn(tc)) {
                    capReturn = null;
                    ret = k;
                } else {
                    if (tc == -49) {
                        k.par++;
                    } else if (tc == -50) {
                        k.par--;
                    } else if (tc == -47) {
                        k.brk++;
                    } else if (tc == -48) {
                        k.brk--;
                    }
                    k.toks.add(t);
                }
            }
        }

        if (terminadas != null) {
            for (CapParen c : terminadas) {
                finalizarCapturaParen(c);
            }
        }
        if (ret != null) {
            finalizarReturn(ret);
        }
        if (caso != null) {
            finalizarCase(caso);
        }
    }

    private void finalizarCapturaParen(CapParen c) {
        switch (c.clase) {
            case CAP_SWITCH -> finalizarSwitch(c);
            case CAP_NOT, CAP_TILDE -> finalizarUnarioLogico(c);
            case CAP_FOR -> finalizarFor(c);
            case CAP_CALL -> finalizarLlamada(c);
            default -> {
            }
        }
    }

    // ---------- Regla 6: SWITCH (1060) y CASE (1061) ----------

    private void finalizarSwitch(CapParen c) {
        Val v = evaluar(c.toks);
        String texto = textoDe(c.toks);
        boolean valido = v.tipo == LeerCSVComparacion.T_CADENA
                || v.tipo == LeerCSVComparacion.T_DEC
                || v.tipo == LeerCSVComparacion.T_VARIANT;

        c.sw.texto = texto;
        // Variant, o 1060 con error (se revisan los CASE "como si fuera Variant"):
        // el tipo de referencia lo fijará el primer CASE válido.
        c.sw.tipoRef = (valido && v.tipo != LeerCSVComparacion.T_VARIANT) ? v.tipo : null;

        filaConError(c.origen.getLinea(), 1060, valido,
                "la expresión del switch debe ser Cadena o Decimal (#D), se encontró "
                        + nombreTipoSem(v.tipo),
                texto, c.origen.getColumna());
    }

    private void finalizarCase(CapCase k) {
        if (switches.isEmpty()) {
            return;
        }
        SwitchCtx sw = switches.peek();
        Val v = evaluar(k.toks);
        String lexema = sw.texto + " vs " + textoDe(k.toks);
        int tipo = v.tipo;
        boolean cadenaODec = tipo == LeerCSVComparacion.T_CADENA || tipo == LeerCSVComparacion.T_DEC;
        boolean variant = tipo == LeerCSVComparacion.T_VARIANT;

        boolean ok = true;
        String motivo = "";
        if (!cadenaODec && !variant) {
            ok = false;
            motivo = "el case debe ser Cadena o Decimal (#D), se encontró " + nombreTipoSem(tipo);
        } else if (sw.tipoRef != null) {
            if (!variant && tipo != sw.tipoRef) {
                ok = false;
                motivo = "el case es " + nombreTipoSem(tipo)
                        + " y el tipo de referencia del switch es " + nombreTipoSem(sw.tipoRef);
            }
        } else if (cadenaODec) {
            sw.tipoRef = tipo; // primer CASE válido: fija el tipo de los siguientes
        }
        filaConError(k.origen.getLinea(), 1061, ok, motivo, lexema, k.origen.getColumna());
    }

    // ---------- Regla 7: ! y ~ (1070) ----------

    private void finalizarUnarioLogico(CapParen c) {
        Val v = evaluar(c.toks);
        boolean esNot = c.clase == CAP_NOT;
        boolean ok;
        String motivo;
        if (esNot) {
            ok = v.tipo == LeerCSVComparacion.T_BOOLEAN || v.tipo == LeerCSVComparacion.T_VARIANT;
            motivo = "el operando de ! debe ser Booleano, se encontró " + nombreTipoSem(v.tipo);
        } else {
            ok = esEntero(v.tipo) || v.tipo == LeerCSVComparacion.T_VARIANT;
            motivo = "el operando de ~ debe ser Decimal, Octal, Binario o Hexadecimal, se encontró "
                    + nombreTipoSem(v.tipo);
        }
        filaConError(c.origen.getLinea(), 1070, ok, motivo,
                textoDe(c.toks), c.origen.getColumna());
    }

    // ---------- Regla 8: FOR (1080 a 1084) ----------

    private void finalizarFor(CapParen c) {
        List<Token> toks = c.toks;
        int par = 0, brk = 0, tern = 0, idxColon = -1;
        List<Integer> puntoYComa = new ArrayList<>();
        for (int k = 0; k < toks.size(); k++) {
            int tc = toks.get(k).getTokenClass();
            if (tc == -49) {
                par++;
            } else if (tc == -50) {
                par--;
            } else if (tc == -47) {
                brk++;
            } else if (tc == -48) {
                brk--;
            } else if (par == 0 && brk == 0) {
                if (tc == -32) {
                    tern++;
                } else if (tc == -10) {
                    if (tern > 0) {
                        tern--;
                    } else if (idxColon < 0 && puntoYComa.isEmpty()) {
                        idxColon = k;
                    }
                } else if (tc == -9) {
                    puntoYComa.add(k);
                }
            }
        }

        if (idxColon >= 0 && puntoYComa.isEmpty()) {
            forEach(toks.subList(0, idxColon), toks.subList(idxColon + 1, toks.size()), c);
            return;
        }
        if (puntoYComa.size() < 2) {
            return; // encabezado mal formado: ya lo reporta el análisis sintáctico
        }
        List<Token> ini = toks.subList(0, puntoYComa.get(0));
        List<Token> cond = toks.subList(puntoYComa.get(0) + 1, puntoYComa.get(1));
        List<Token> inc = toks.subList(puntoYComa.get(1) + 1, toks.size());

        // 1080: cada inicialización debe ser "ID = EXP" (asignación a un identificador)
        for (List<Token> item : separarPorComas(ini)) {
            if (item.isEmpty()) {
                continue;
            }
            boolean ok = esFormaAsignacion(item);
            filaConError(item.get(0).getLinea(), 1080, ok,
                    "la inicialización del for debe ser una asignación a un identificador",
                    textoDe(item), item.get(0).getColumna());
        }

        // 1081: la comparación debe ser lógica (Variant se acepta)
        if (!cond.isEmpty()) {
            Val v = evaluar(cond);
            boolean ok = v.tipo == LeerCSVComparacion.T_BOOLEAN
                    || v.tipo == LeerCSVComparacion.T_VARIANT;
            filaConError(cond.get(0).getLinea(), 1081, ok,
                    "la condición del for debe ser lógica (Booleanas), se encontró "
                            + nombreTipoSem(v.tipo),
                    textoDe(cond), cond.get(0).getColumna());
        }

        // 1082: incremento o decremento: ++ / -- sobre un identificador
        for (List<Token> item : separarPorComas(inc)) {
            if (item.isEmpty()) {
                continue;
            }
            boolean ok = esFormaIncremento(item);
            filaConError(item.get(0).getLinea(), 1082, ok,
                    "el tercer componente del for debe ser un incremento o decremento (++ o --)",
                    textoDe(item), item.get(0).getColumna());
        }
    }

    private void forEach(List<Token> v1, List<Token> v2, CapParen c) {
        if (v1.isEmpty() || v2.isEmpty()) {
            return;
        }
        Val a = evaluar(v1);
        Val b = evaluar(v2);
        String lexema = textoDe(v1) + " : " + textoDe(v2);
        int linea = v1.get(0).getLinea();
        int pos = v1.get(0).getColumna();
        int t1 = a.tipo, t2 = b.tipo;
        boolean arr2 = b.arreglo && b.idSimple != null;
        boolean v1EsId = v1.size() == 1 && esIdToken(v1.get(0).getTokenClass());

        if (!v1EsId) {
            int clave = (t2 == LeerCSVComparacion.T_CADENA && !arr2) ? 1083 : 1084;
            filaConError(linea, clave, false,
                    "el valor 1 del for debe ser un identificador", lexema, pos);
            return;
        }

        if (t2 == LeerCSVComparacion.T_VARIANT && !arr2) {
            // Valor 2 Variant: se toma como arreglo del tipo del valor 1.
            // Si ambos son Variant, se aceptan ambas reglas.
            if (t1 == LeerCSVComparacion.T_VARIANT) {
                filaConError(linea, 1083, true, "", lexema, pos);
            }
            filaConError(linea, 1084, true, "", lexema, pos);
        } else if (arr2) {
            boolean ok = t1 == LeerCSVComparacion.T_VARIANT || t1 == t2;
            filaConError(linea, 1084, ok,
                    "el valor 1 es " + nombreTipoSem(t1)
                            + " y el arreglo es de " + nombreTipoSem(t2),
                    lexema, pos);
        } else if (t2 == LeerCSVComparacion.T_CADENA) {
            boolean ok = t1 == LeerCSVComparacion.T_DEC || t1 == LeerCSVComparacion.T_VARIANT;
            filaConError(linea, 1083, ok,
                    "al recorrer una cadena el valor 1 debe ser Decimal (#D), se encontró "
                            + nombreTipoSem(t1),
                    lexema, pos);
        } else {
            filaConError(linea, 1084, false,
                    "el valor 2 debe ser un arreglo o una cadena, se encontró "
                            + (b.idSimple == null ? "un elemento o expresión de tipo " : "un valor de tipo ")
                            + nombreTipoSem(t2),
                    lexema, pos);
        }
    }

    private static List<List<Token>> separarPorComas(List<Token> toks) {
        List<List<Token>> res = new ArrayList<>();
        List<Token> actual = new ArrayList<>();
        int par = 0, brk = 0;
        for (Token t : toks) {
            int tc = t.getTokenClass();
            if (tc == -49) {
                par++;
            } else if (tc == -50) {
                par--;
            } else if (tc == -47) {
                brk++;
            } else if (tc == -48) {
                brk--;
            }
            if (tc == -7 && par == 0 && brk == 0) {
                res.add(actual);
                actual = new ArrayList<>();
            } else {
                actual.add(t);
            }
        }
        res.add(actual);
        return res;
    }

    /** ID [ '[' ... ']' ]* operadorDeAsignación EXP */
    private static boolean esFormaAsignacion(List<Token> item) {
        int i = 0;
        if (item.isEmpty() || !esIdToken(item.get(0).getTokenClass())) {
            return false;
        }
        i = saltarIndices(item, 1);
        return i < item.size() - 1 && esAsignacion(item.get(i).getTokenClass());
    }

    /** (++|--) ID [índices]  |  ID [índices] (++|--) */
    private static boolean esFormaIncremento(List<Token> item) {
        int n = item.size();
        if (n < 2) {
            return false;
        }
        int primero = item.get(0).getTokenClass();
        if (primero == -1 || primero == -2) {
            return esIdToken(item.get(1).getTokenClass())
                    && saltarIndices(item, 2) == n;
        }
        if (esIdToken(primero)) {
            int i = saltarIndices(item, 1);
            return i == n - 1 && (item.get(i).getTokenClass() == -1
                    || item.get(i).getTokenClass() == -2);
        }
        return false;
    }

    private static int saltarIndices(List<Token> item, int i) {
        while (i < item.size() && item.get(i).getTokenClass() == -47) {
            int prof = 0;
            while (i < item.size()) {
                int tc = item.get(i).getTokenClass();
                if (tc == -47) {
                    prof++;
                } else if (tc == -48) {
                    prof--;
                    if (prof == 0) {
                        i++;
                        break;
                    }
                }
                i++;
            }
        }
        return i;
    }

    // ---------- Evaluador de tipo de una expresión ----------

    private Val evaluar(List<Token> toks) {
        if (toks == null || toks.isEmpty()) {
            return new Val(LeerCSVComparacion.T_VARIANT);
        }
        return new EvalExpr(toks).expr(1);
    }

    /**
     * Precedencia (PRECEDENCIA_OPERADOR) sobre la lista de tokens capturada.
     * El tipo de cada operación sale de las mismas tablas CSV que usa
     * generarOperaciones; si la tabla dice Error, o el operador no tiene tabla,
     * el resultado es Variant (el error de tipos de las asignaciones ya lo
     * reporta Semántica 1; aquí no se duplica).
     */
    private class EvalExpr {
        final List<Token> t;
        int i = 0;

        EvalExpr(List<Token> t) {
            this.t = t;
        }

        boolean fin() {
            return i >= t.size();
        }

        int tc() {
            return t.get(i).getTokenClass();
        }

        Val expr(int min) {
            Val lhs = unario();
            while (!fin()) {
                int c = tc();
                if (c == -32) { // ternario ?:
                    if (2 < min) {
                        break;
                    }
                    i++;
                    expr(1);
                    if (!fin() && tc() == -10) {
                        i++;
                    }
                    expr(2);
                    lhs = new Val(LeerCSVComparacion.T_VARIANT);
                    continue;
                }
                Integer p = PRECEDENCIA_OPERADOR.get(c);
                if (p == null || p < min) {
                    break;
                }
                i++;
                Val rhs = expr(ASOCIATIVIDAD_DERECHA.contains(c) ? p : p + 1);
                lhs = combinar(c, lhs, rhs);
            }
            return lhs;
        }

        Val combinar(int c, Val l, Val r) {
            if (esAsignacion(c)) {
                return new Val(l.tipo);
            }
            if (LeerCSVComparacion.tieneTabla(c)) {
                int res = comparacion.ObtenerTipoPorTipos(l.tipo, r.tipo, c);
                return new Val(res < 0 ? LeerCSVComparacion.T_VARIANT : res);
            }
            return new Val(LeerCSVComparacion.T_VARIANT);
        }

        Val unario() {
            if (fin()) {
                return new Val(LeerCSVComparacion.T_VARIANT);
            }
            int c = tc();
            if (c == -29) { // !
                i++;
                unario();
                return new Val(LeerCSVComparacion.T_BOOLEAN);
            }
            if (c == -3) { // ~
                i++;
                Val o = unario();
                return new Val(esEntero(o.tipo) ? o.tipo : LeerCSVComparacion.T_VARIANT);
            }
            if (c == -1 || c == -2 || c == -11 || c == -12) { // ++ -- + -
                i++;
                return new Val(unario().tipo);
            }
            return primario();
        }

        Val primario() {
            Token tok = t.get(i);
            int c = tok.getTokenClass();

            if (c == -49) { // ( EXP )
                i++;
                Val v = expr(1);
                if (!fin() && tc() == -50) {
                    i++;
                }
                return new Val(v.tipo);
            }
            if ((c <= -53 && c >= -59) || c == -72 || c == -73 || c == -74) {
                i++;
                return new Val(LeerCSVComparacion.tipoDeToken(c));
            }
            if (esIdToken(c)) {
                i++;
                IdInfo inf = idInfos.get(tok);
                int tp = (inf != null && inf.declarado)
                        ? tipoSemanticoDeSimbolo(inf.tipo)
                        : LeerCSVComparacion.T_VARIANT;
                Val v = new Val(tp);
                v.idSimple = tok;
                v.arreglo = inf != null && inf.declarado && "Arreglo".equals(inf.clase);
                while (!fin() && (tc() == -47 || tc() == -49)) {
                    saltarBalanceado(tc() == -47 ? -48 : -50);
                    v.idSimple = null; // índice o llamada: ya no es el identificador solo
                    v.arreglo = false;
                }
                if (!fin() && (tc() == -1 || tc() == -2)) {
                    i++;
                    v.idSimple = null;
                }
                return v;
            }
            if (c <= -75 && c >= -94) { // funciones predefinidas: resultado Variant
                i++;
                if (!fin() && tc() == -49) {
                    saltarBalanceado(-50);
                }
                return new Val(LeerCSVComparacion.T_VARIANT);
            }
            i++;
            return new Val(LeerCSVComparacion.T_VARIANT);
        }

        void saltarBalanceado(int cierre) {
            int abre = (cierre == -50) ? -49 : -47;
            int prof = 0;
            while (!fin()) {
                int c = tc();
                i++;
                if (c == abre) {
                    prof++;
                } else if (c == cierre) {
                    prof--;
                    if (prof == 0) {
                        return;
                    }
                }
            }
        }
    }

    // =========================================================
    // AVANCE 3 DE SEMÁNTICA 2: REGLAS 11 A 16
    //
    // 1110 PROCEDIMIENTO | 1120 FUNCIONES | 1130 RETURN DE FUNCIÓN
    // 1131 TIPO DE RETORNO IGUAL | 1140 NO RETURN DE PROCEDIMIENTO
    // 1150 PARÁMETROS DECLARADOS (cantidad) | 1160 PARÁMETROS DE TIPO
    //
    // Cómo distingue la gramática del proyecto cada cosa:
    //  - Declaración: producción 7 (def ID ...). Si el ID es de registro (token
    //    -107, nombre "plano" como m) es un PROCEDIMIENTO (clase "Procedimiento",
    //    tipo Registro). Si el ID es tipado (#Df, @f, $f...) es una FUNCIÓN
    //    (clase "Funcion") y debe devolver ese tipo.
    //  - Llamada: producción 52 (X1 -> ( lista de argumentos )). El nombre del
    //    callee indica cómo se está invocando: nombre de registro => se invoca
    //    como procedimiento (1110); nombre tipado => como función (1120).
    //  - return: producción 92 (STATU -> return OR).
    //  - Cuerpo: el ámbito que abre el marcador 802 justo tras "def ID"; se
    //    cierra con 803. Ahí se resuelven 1130/1140.
    // =========================================================

    private static final int CAP_CALL = 5;

    /** Lo que se sabe de una función/procedimiento declarado. */
    private static class FuncInfo {
        final String id;
        final String tipo; // "Decimal", "Cadena"... o "Registro" (procedimiento)
        final boolean procedimiento;
        final int ambitoDecl;
        final int linea;
        final int columna;
        int ambitoCuerpo = -1;
        int returns = 0;
        boolean returnRegistrado = false; // ya se dejó la fila 1130 ACEPT
        final List<String> paramNombres = new ArrayList<>();
        final List<String> paramTipos = new ArrayList<>();

        FuncInfo(String id, String tipo, boolean procedimiento, int ambitoDecl,
                int linea, int columna) {
            this.id = id;
            this.tipo = tipo;
            this.procedimiento = procedimiento;
            this.ambitoDecl = ambitoDecl;
            this.linea = linea;
            this.columna = columna;
        }
    }

    /** Captura de la expresión de un return (hasta ; } else ... ). */
    private static class CapReturn {
        final Token origen;
        final FuncInfo f;
        final List<Token> toks = new ArrayList<>();
        boolean iniciada = false;
        int par = 0, brk = 0;

        CapReturn(Token origen, FuncInfo f) {
            this.origen = origen;
            this.f = f;
        }
    }

    // id|ámbito -> info (los parámetros van EN ORDEN; la BD no garantiza orden).
    private final Map<String, FuncInfo> funciones = new HashMap<>();
    // Funciones/procedimientos cuyo cuerpo está abierto (pueden anidarse).
    private final Stack<FuncInfo> funcStack = new Stack<>();
    // "def ID" ya leído, aún sin abrir su ámbito.
    private FuncInfo defPendiente = null;
    private Token ultimoIdTok = null;
    private CapReturn capReturn = null;

    // Llamada tratada como un solo operando de la operación infija->postfija.
    private boolean llamadaOpPendiente = false;
    private int llamadaOpProf = 0;
    private Token llamadaOpCallee = null;
    private final StringBuilder llamadaOpTexto = new StringBuilder();

    // ---------- Utilidades ----------

    /** Ámbito (de los accesibles) donde está declarado id, o -1. */
    private int buscarAmbitoSimbolo(String id) {
        for (int i = AmbitoActual.size() - 1; i >= 0; i--) {
            int amb = AmbitoActual.get(i);
            if (tabla.idExiste(id, amb)) {
                return amb;
            }
        }
        return -1;
    }

    /**
     * Compatibilidad estricta destino<-origen para parámetros y retornos.
     * suma.csv deja que Cadena absorba cualquier tipo (concatenación), pero un
     * argumento Decimal NO es válido para un parámetro Cadena (ejemplo del
     * profesor: fun1(2,5,5) falla en la posición 2). Variant se acepta siempre.
     */
    private boolean tiposCompatibles(int dest, int orig) {
        if (dest == LeerCSVComparacion.T_VARIANT || orig == LeerCSVComparacion.T_VARIANT) {
            return true;
        }
        if (dest == orig) {
            return true;
        }
        if (dest == LeerCSVComparacion.T_CADENA || orig == LeerCSVComparacion.T_CADENA
                || dest == LeerCSVComparacion.T_BOOLEAN || orig == LeerCSVComparacion.T_BOOLEAN) {
            return false;
        }
        return comparacion.asignacionCompatible(dest, orig); // p. ej. Real <- Decimal
    }

    private static boolean esFinDeReturn(int tc) {
        // ; } else elseif case default while(do-while)
        return tc == -9 || tc == -46 || tc == -101 || tc == -100
                || tc == -102 || tc == -103 || tc == -95;
    }

    // ---------- return (reglas 1131 y 1140) ----------

    private void iniciarReturn(Token ret) {
        if (capReturn != null) { // return anterior que no llegó a cerrarse
            CapReturn previo = capReturn;
            capReturn = null;
            finalizarReturn(previo);
        }
        if (funcStack.isEmpty()) {
            return; // return en el main del programa: fuera de este avance
        }
        FuncInfo f = funcStack.peek();
        f.returns++;
        if (f.procedimiento) {
            // Regla 14 (1140): un procedimiento no debe tener return.
            filaConError(ret.getLinea(), 1140, false,
                    "el procedimiento " + f.id + " no debe contener return",
                    f.id, ret.getColumna());
            return;
        }
        capReturn = new CapReturn(ret, f);
    }

    /** Regla 13 (1131): el tipo devuelto debe ser el declarado para la función. */
    private void finalizarReturn(CapReturn k) {
        Val v = evaluar(k.toks);
        int declarado = tipoSemanticoDeSimbolo(k.f.tipo);
        // Regla 13 (1130): la función tiene (al menos) un return. Se registra
        // una vez, en la línea del primer return, no en la declaración.
        if (!k.f.returnRegistrado) {
            k.f.returnRegistrado = true;
            fila(k.origen.getLinea(), 1130, true, "", k.f.id, k.origen.getColumna());
        }
        boolean ok = tiposCompatibles(declarado, v.tipo);
        filaConError(k.origen.getLinea(), 1131, ok,
                "la función " + k.f.id + " devuelve " + nombreTipoSem(declarado)
                        + " y el return entrega " + nombreTipoSem(v.tipo),
                "return " + textoDe(k.toks), k.origen.getColumna());
    }

    /** Se cierra el cuerpo: resuelve 1130 (función sin return) y 1140 (ACEPT). */
    private void cerrarFuncion(FuncInfo f) {
        if (capReturn != null && capReturn.f == f) {
            CapReturn k = capReturn;
            capReturn = null;
            finalizarReturn(k);
        }
        if (f.procedimiento) {
            if (f.returns == 0) {
                fila(f.linea, 1140, true, "", f.id, f.columna);
            }
        } else {
            // Regla 13 (1130): solo falla aquí; el ACEPT se registra en el return.
            if (f.returns == 0) {
                filaConError(f.linea, 1130, false,
                        "la función " + f.id + " no contiene ninguna instrucción return",
                        f.id, f.columna);
            }
        }
    }

    // ---------- Llamadas (reglas 1110, 1120, 1150 y 1160) ----------

    private void iniciarLlamada(Token abre) {
        if (ultimoIdTok == null) {
            return;
        }
        // El ID recién consumido es el callee: X1 se expande justo después de él.
        capsParen.add(new CapParen(CAP_CALL, ultimoIdTok, null));
        if (EstadoDeOperacion && llamadaOpProf == 0) {
            llamadaOpPendiente = true;
        }
    }

    private void finalizarLlamada(CapParen c) {
        Token callee = c.origen;
        String id = callee.getLexema().trim();
        boolean comoProc = callee.getTokenClass() == -107;
        int clave = comoProc ? 1110 : 1120;
        int linea = callee.getLinea();
        int col = callee.getColumna();

        int amb = buscarAmbitoSimbolo(id);
        if (amb < 0) {
            // El error de "no declarado" ya lo registra la regla 1100 / el parser:
            // aquí solo queda la fila, sin duplicar el ErrorEntry.
            fila(linea, clave, false,
                    "identificador no declarado en los ámbitos accesibles", id, col);
            return;
        }

        String clase = tabla.obtenerClase(id, amb);
        clase = clase == null ? "" : clase.trim();
        boolean ok = comoProc ? clase.equals("Procedimiento") : clase.equals("Funcion");
        filaConError(linea, clave, ok,
                id + " es de clase " + (clase.isEmpty() ? "desconocida" : clase)
                        + "; se invocó como " + (comoProc ? "procedimiento" : "función")
                        + " pero no lo es",
                id, col);
        if (!ok) {
            return; // sin función/procedimiento no hay parámetros que comparar
        }

        List<List<Token>> args = c.toks.isEmpty()
                ? new ArrayList<List<Token>>()
                : separarPorComas(c.toks);
        int n = args.size();
        FuncInfo f = funciones.get(id + "|" + amb);
        Integer declN = tabla.obtenerNumParametros(id, amb);
        int declarados = declN != null ? declN : (f != null ? f.paramTipos.size() : n);

        // Regla 15 (1150): cantidad de argumentos = cantidad declarada.
        registrarCantidadArgumentos(id, f, args, n, declarados, linea, col, textoDe(c.toks));

        // Regla 16 (1160): tipo de cada argumento contra su parámetro, en orden.
        // Solo se revisan las posiciones que existen en ambos lados.
        if (f == null) {
            return;
        }
        int m = Math.min(n, Math.min(declarados, f.paramTipos.size()));
        for (int i = 0; i < m; i++) {
            List<Token> arg = args.get(i);
            Val v = evaluar(arg);
            int tParam = tipoSemanticoDeSimbolo(f.paramTipos.get(i));
            boolean okTipo = tiposCompatibles(tParam, v.tipo);
            if (!ARG_DECIMAL_ACEPTA_EN_REAL && tParam == LeerCSVComparacion.T_REAL
                    && esEntero(v.tipo)) {
                okTipo = false;
            }
            int lineaArg = arg.isEmpty() ? linea : arg.get(0).getLinea();
            filaConError(lineaArg, 1160, okTipo,
                    "posición " + (i + 1) + ": el argumento es " + nombreTipoSem(v.tipo)
                            + " y el parámetro " + f.paramNombres.get(i)
                            + " es " + nombreTipoSem(tParam),
                    arg.isEmpty() ? "(vacío)" : textoDe(arg), col * 100 + i + 1);
        }
    }

    // ---------- La llamada como UN operando de la infija->postfija ----------

    private void llamadaOpAcumular(String lex) {
        if (llamadaOpTexto.length() > 0) {
            llamadaOpTexto.append(' ');
        }
        llamadaOpTexto.append(lex);
    }

    /**
     * Al cerrar el ")" de la llamada, el callee (ya en operandosPila) y sus
     * argumentos pasan a ser un único operando "f(args)" cuyo tipo es el de
     * retorno de la función (Variant si es procedimiento o no se conoce).
     */
    private void cerrarLlamadaOperando() {
        Token callee = llamadaOpCallee;
        if (callee != null && !operandosPila.isEmpty() && operandosPila.peek() == callee) {
            operandosPila.pop();
            String id = callee.getLexema().trim();
            Token unico = new Token(id + "(" + llamadaOpTexto + ")", callee.getTipo(),
                    callee.getLinea(), callee.getColumna(), callee.getTokenClass());
            int tipo = LeerCSVComparacion.T_VARIANT;
            int amb = buscarAmbitoSimbolo(id);
            FuncInfo f = amb >= 0 ? funciones.get(id + "|" + amb) : null;
            if (f != null && !f.procedimiento) {
                tipo = tipoSemanticoDeSimbolo(f.tipo);
            }
            tiposOperando.put(unico, tipo);
            operandosPila.push(unico);
        }
        llamadaOpCallee = null;
        llamadaOpTexto.setLength(0);
    }

    /**
     * Interruptor de la regla 16 (1160): ¿un argumento entero (Decimal, Octal,
     * Binario, Hexadecimal) es válido para un parámetro Real? true = sí, como en
     * el ejemplo del profesor (fun1(2,"hola",6) con $k Real) y como en las
     * asignaciones ($r = 3). Poner false para exigir Real exacto.
     */
    private static final boolean ARG_DECIMAL_ACEPTA_EN_REAL = true;

    /** Declaración de función (1120) o procedimiento (1110): la clase quedó bien registrada. */
    private void registrarDeclaracionCallable(Token t, String id, int tc) {
        boolean proc = tc == -107;
        String clase = tabla.obtenerClase(id, AmbitoActual.peek());
        clase = clase == null ? "" : clase.trim();
        boolean ok = proc ? clase.equals("Procedimiento") : clase.equals("Funcion");
        filaConError(t.getLinea(), proc ? 1110 : 1120, ok,
                id + " no quedó registrado como " + (proc ? "procedimiento" : "función")
                        + " en la tabla de símbolos",
                id, t.getColumna());
    }

    /**
     * Regla 15 (1150) posición por posición. Si coincide la cantidad: una fila
     * ACEPT. Si no: una fila ERROR por cada posición que falta o que sobra; solo
     * la primera genera el ErrorEntry (las demás son detalle, sin duplicar).
     */
    private void registrarCantidadArgumentos(String id, FuncInfo f, List<List<Token>> args,
            int n, int declarados, int linea, int col, String textoArgs) {
        String llamada = id + "(" + textoArgs + ")";
        if (n == declarados) {
            filaConError(linea, 1150, true, "", llamada, col);
            return;
        }
        boolean faltan = n < declarados;
        int desde = Math.min(n, declarados);
        int hasta = Math.max(n, declarados);
        for (int i = desde; i < hasta; i++) {
            String detalle;
            int lineaFila = linea;
            if (faltan) {
                String nombre = (f != null && i < f.paramNombres.size())
                        ? " (parámetro " + f.paramNombres.get(i) + ")" : "";
                detalle = "posición " + (i + 1) + ": falta el argumento" + nombre;
            } else {
                detalle = "posición " + (i + 1) + ": argumento sobrante, " + id
                        + " solo declara " + declarados + " parámetro(s)";
                if (!args.get(i).isEmpty()) {
                    lineaFila = args.get(i).get(0).getLinea();
                }
            }
            int pos = col * 100 + i + 1;
            if (i == desde) {
                filaConError(lineaFila, 1150, false,
                        "se enviaron " + n + " argumento(s) y " + id + " declara "
                                + declarados + "; " + detalle,
                        llamada, pos);
            } else {
                fila(lineaFila, 1150, false, detalle, llamada, pos);
            }
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

        // Avance 3 Sem2: el primer ámbito abierto tras "def ID" es el cuerpo
        // de esa función/procedimiento; se apila su contexto.
        if (defPendiente != null) {
            defPendiente.ambitoCuerpo = AmbitoActual.peek();
            funcStack.push(defPendiente);
            defPendiente = null;
        }
    }

    private void CerrarAmbito(
            Token tokenActual) {

        if (AmbitoActual.size() <= 1) {

            return;
        }

        // Avance 3 Sem2: se cierra el cuerpo de una función/procedimiento
        // (reglas 1130 y 1140). Aún estamos dentro de su ámbito.
        if (!funcStack.isEmpty()
                && funcStack.peek().ambitoCuerpo == AmbitoActual.peek()) {
            cerrarFuncion(funcStack.pop());
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

        // Avance 3 Sem2: una llamada f(args) se trata como UN solo operando.
        if (llamadaOpProf > 0) {
            if (tc == -49) {
                llamadaOpProf++;
            } else if (tc == -50) {
                llamadaOpProf--;
            }
            if (llamadaOpProf == 0) {
                cerrarLlamadaOperando();
            } else {
                llamadaOpAcumular(t.getLexema().trim());
            }
            return;
        }
        if (llamadaOpPendiente && tc == -49) {
            llamadaOpPendiente = false;
            llamadaOpProf = 1;
            llamadaOpCallee = operandosPila.isEmpty() ? null : operandosPila.peek();
            llamadaOpTexto.setLength(0);
            return;
        }

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
                String derecho = pilaPrefijo.isEmpty() ? "" : pilaPrefijo.pop();
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
        llamadaOpPendiente = false;
        llamadaOpProf = 0;
        llamadaOpCallee = null;
        llamadaOpTexto.setLength(0);
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

    /**
     * "Decimal" / "Real" / ... (tabla de símbolos) -> tipo 1..10. Lo demás es
     * Variant.
     */
    private static int tipoSemanticoDeSimbolo(String tipo) {
        if (tipo == null)
            return LeerCSVComparacion.T_VARIANT;
        return switch (tipo.trim()) {
            case "Binario" -> LeerCSVComparacion.T_BIN;
            case "Decimal" -> LeerCSVComparacion.T_DEC;
            case "Octal" -> LeerCSVComparacion.T_OCT;
            case "Hexadecimal" -> LeerCSVComparacion.T_HEX;
            case "Real" -> LeerCSVComparacion.T_REAL;
            case "Exponencial" -> LeerCSVComparacion.T_EXP;
            case "Cadena" -> LeerCSVComparacion.T_CADENA;
            case "Booleanas" -> LeerCSVComparacion.T_BOOLEAN;
            default -> LeerCSVComparacion.T_VARIANT;
        };
    }

    private String nuevoTemporal(int tipo) {
        int n = contadorTemporales.merge(tipo, 1, Integer::sum);
        String nombre = LeerCSVComparacion.prefijoTemporal(tipo) + n;
        tipoDeTemporal.put(nombre, tipo);
        if (estadisticaLineaActual != null) {
            int col = columnaEstadistica(tipo);
            if (col >= 0)
                estadisticaLineaActual[col]++;
        }
        return nombre;
    }

    /** tipo (1..10) -> columna de estadisticasSemantica; -1 si no tiene columna. */
    private static int columnaEstadistica(int tipo) {
        if (tipo >= LeerCSVComparacion.T_BIN && tipo <= LeerCSVComparacion.T_CADENA)
            return tipo - 1;
        if (tipo == LeerCSVComparacion.T_BOOLEAN)
            return 7;
        if (tipo == LeerCSVComparacion.T_VARIANT)
            return 8;
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
            default -> (tc <= -20 && tc >= -26) ? "relacional" : "lógico";
        };
    }

    /**
     * Recorre el postfijo y emite "operador,valor1,valor2,temporal".
     * Cada operación genera un temporal cuyo tipo sale de las tablas CSV.
     * Si la tabla dice Error: se registra "Semántica 1" y el temporal queda
     * Variant.
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
            for (int i = 0; i < 10; i++)
                acumulado[i] += stats[i];

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