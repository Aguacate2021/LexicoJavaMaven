package analyzer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
    // Operando que es una llamada nativa -> temporal donde quedó su resultado.
    private final Map<Token, String> temporalOperando = new IdentityHashMap<>();

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
    private final List<Token> condToks = new ArrayList<>(); // tokens de la condición, para evaluar su tipo

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
        String vis = null;    // texto que muestra el muestreo semántico si no es el del temporal
        boolean proc = false; // el operando es un procedimiento (o su llamada): no produce ni recibe valores

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

    public List<String[]> getFilasMuestreoSemantica2() {
        return muestreo.filas();
    }

    // Semántica 3: reglas 2001-2018 de parámetros y retornos de las funciones nativas.
    private final SemanticAnalyzerV3 semantica3 = new SemanticAnalyzerV3();
    // Orden de ejecución compartido por las filas de Semántica 2 y 3 (solo para exportarlas intercaladas).
    private final java.util.concurrent.atomic.AtomicLong secuenciaFilas = new java.util.concurrent.atomic.AtomicLong();
    {
        muestreo.setSecuencia(secuenciaFilas::incrementAndGet);
        semantica3.setSecuencia(secuenciaFilas::incrementAndGet);
    }
    private static final String ARCHIVO_SEM3 = "Semantica3-DavidAlatorre.txt";

    /** Tabla Semántica 3: {Regla, Función, Tope Pila, Valor Real, Fila, Edo, Ambito}. */
    public List<String[]> getFilasSemantica3() {
        return semantica3.filas();
    }

    /** Contadores de Semántica 3: {Función, Entradas, Salida, Aceptados, Errores}. */
    public List<String[]> getContadoresSemantica3() {
        return semantica3.contadores().filas();
    }

    public String getLogSemantica3() {
        return semantica3.generarTabla();
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

    /**
     * Analiza los tokens. Si la base de datos de la tabla de símbolos deja de responder,
     * el análisis se detiene con UN error explícito: seguir sin tabla de símbolos solo
     * produciría errores falsos de "identificador no declarado".
     */
    public void parsear(List<Token> tokens) {
        try {
            parsear0(tokens);
        } catch (ConexionBD.FalloBaseDatos e) {
            ContadorCiclos.ERRORES++;
            erroresSintaxis.add(new ErrorEntry(
                    "ERR-BD",
                    "Análisis detenido: " + e.getMessage(),
                    lineaFalloBD,
                    "parser",
                    ErrorEntry.Tipo.AMBITO,
                    "-",
                    ErrorEntry.AMBITO_DESCONOCIDO));
            System.err.println("[BD] " + e.getMessage());
        }
    }

    private int lineaFalloBD = 0; // última línea procesada (para ubicar un fallo de BD)

    private void parsear0(List<Token> tokens) {

        // -----------------------------------------------------
        // LIMPIAR ESTADO DEL ANÁLISIS
        // -----------------------------------------------------

        logBuilder.setLength(0);

        reiniciarMuestreoSemantica2();
        semantica3.limpiar();
        secuenciaFilas.set(0);

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
        infijoToks.clear();

        // Avance 2: temporales y tablas de compatibilidad (nueva compilación)
        tiposOperando.clear();
        temporalOperando.clear();
        retornoNativa.clear();
        pendientesConsole.clear();
        temporalExpresion.clear();
        temporalNativa.clear();
        cuadruplasNativas.setLength(0);
        tipoDeTemporal.clear();
        contadorTemporales.clear();
        contTempSem.clear();
        memoTempSem.clear();
        tokTempSem.clear();
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
            lineaFalloBD = tokenActual.getLinea();
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
                        prevOpTok = null;
                        signoPend = null;
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
        infijoToks.clear();
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
                            defDuplicada = false;

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
                        // un área de declaración para la primera expresión. La
                        // variable de control se declara en el ámbito propio del FOR
                        // (aunque exista afuera, como en la referencia); solo se
                        // reutiliza si ya está declarada en ese mismo ámbito.
                        boolean encabezadoFor = (AreaDeDeclaraciones
                                && enEncabezadoFor
                                && produccionActual == 44)
                                || (forEnCurso() != null && idTrasComaEnInitFor(forEnCurso()));

                        if (encabezadoFor) {
                            if (esPrimerIdDelFor()) {
                                // variable de control: solo cuenta el ámbito del propio FOR
                                encontrado = tabla.idExiste(id, AmbitoActual.peek());
                                if (encontrado) {
                                    ambitoEncontrado = AmbitoActual.peek();
                                }
                            } else {
                                // otros ids de la inicialización (for (#Dk = inc(#Dx); ...)): son usos
                                encontrado = false;
                                for (int i = AmbitoActual.size() - 1; i >= 0; i--) {
                                    int amb = AmbitoActual.get(i);
                                    if (tabla.idExiste(id, amb)) {
                                        encontrado = true;
                                        ambitoEncontrado = amb;
                                        break;
                                    }
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
                                // Redefinición de función/procedimiento: la primera definición es la
                                // válida; esta no debe pisar su nopar (ni sus parámetros) en la tabla.
                                if (produccionActual == 7) {
                                    defDuplicada = true;
                                }
                                // Variable repetida (var #Dv[9] cuando #Dv[5] ya existe): queda la primera
                                // declaración; los tamaños/dimensiones de la repetida no deben pisarla.
                                if (produccionActual == 12 || produccionActual == 17) {
                                    idArreglo = "";
                                    TamañoArreglo = "";
                                    DimensionesArreglo = 0;
                                    ambitoArreglo = -1;
                                }
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

                                    // NoPar de un parámetro = su posición (1, 2, 3...) en la
                                    // lista; NumParametros ya se incrementó al obtener su clase.
                                    insertado = tabla.insertar(
                                            id,
                                            tipo,
                                            clase,
                                            ambito,
                                            null,
                                            null,
                                            NumParametros,
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
                            infijoToks.add(tokenActual);
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

                            if (signoPend != null) { // signo delante de un id: operador normal (no es constante)
                                Token sg = signoPend;
                                signoPend = null;
                                procesarTokenDeOperacion0(sg);
                            }
                            prevOpTok = tokenActual;
                            operandosPila.push(tokenActual);
                            if (infijoActual.length() > 0) {
                                infijoActual.append(" ");
                            }
                            infijoActual.append(id);
                            infijoToks.add(tokenActual);
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

                        if (!defDuplicada) {
                            tabla.actualizarFuncion(
                                    idFuncion,
                                    ambitoFuncion,
                                    NumParametros);
                        }
                        defDuplicada = false;

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

        volcarAsignacionesPendientes();

        // Semántica 3: sus errores se suman a la lista (no cuentan para abortar el análisis).
        erroresSintaxis.addAll(semantica3.errores());
        String rutaSem3 = semantica3.guardarArchivo(ARCHIVO_SEM3);
        if (rutaSem3 != null) {
            System.out.println("[SEM3] Archivo generado: " + rutaSem3
                    + " (" + semantica3.cantidad() + " registros)");
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

            ContadorCiclos.ERRORES_SINTAXIS++;
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

            ContadorCiclos.ERRORES_SINTAXIS++;
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
        boolean cerrado = false; // ya llegó el "]" final: el último índice está completo
        int validadas = 0;       // dimensiones ya revisadas (1030/1040/1050)
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
        asigPendientes.clear();
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
            // ---- Semántica 3 (reglas 2001 a 2018): funciones nativas ----
            case 63 -> validarFuncionNativa(t, new ArrayList<>()); // CLEAR: sin paréntesis ni parámetros
            case 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80 ->
                iniciarCapturaParen(CAP_NATIVA, t, null);
            case 86 -> iniciarCapturaParen(CAP_CONSOLE, t, null); // Console.log ( EXP )
            default -> {
            }
        }
    }

    private void registrarAsignacion(int regla, String descripcion, Token operador) {
        // La fila se escribe al evaluar la asignación (ACEPT / ERROR): ver verificarAsignacion.
        boolean enInicializacionFor = enInicializacionDelFor();
        asigPendientes.put(operador, new AsigPend(operador.getLinea(), regla, descripcion,
                ultimoIdFueraIndice, obtenerAmbitoActual(), operador.getColumna(),
                enInicializacionFor));
        validarClaseReceptor(operador, enInicializacionFor);
    }

    private static class AsigPend {
        final int linea, regla, ambito, posicion;
        final String descripcion, lexema;
        final boolean soloSiFalla; // inicialización del for: la fila solo se escribe si hay ERROR

        AsigPend(int linea, int regla, String descripcion, String lexema, int ambito, int posicion,
                boolean soloSiFalla) {
            this.soloSiFalla = soloSiFalla;
            this.linea = linea;
            this.regla = regla;
            this.descripcion = descripcion;
            this.lexema = lexema;
            this.ambito = ambito;
            this.posicion = posicion;
        }
    }

    // operador de asignación (identidad del Token) -> fila 1020/1021/1022 por escribir
    private final Map<Token, AsigPend> asigPendientes = new IdentityHashMap<>();

    /** Asignación que nunca llegó a evaluarse (error de sintaxis): conserva la fila "Activada". */
    private void volcarAsignacionesPendientes() {
        for (AsigPend p : asigPendientes.values()) {
            if (p.soloSiFalla) {
                continue;
            }
            muestreo.registrar(p.linea, p.regla, "-", p.descripcion, "Activada", p.lexema,
                    p.ambito, p.posicion);
        }
        asigPendientes.clear();
    }

    /**
     * Valida destino <- valor con asignacionCompatible (tablas existentes). Si algún
     * tipo es Variant (no declarado, o resultado de operación sin tipo) se acepta.
     * Escribe la fila 1020/1021/1022 con el veredicto y, si falla, un ErrorEntry.
     */
    private void verificarAsignacion(Token op, Operando destino, Operando valor, int linea) {
        boolean conocido = destino.tipo != LeerCSVComparacion.T_VARIANT
                && valor.tipo != LeerCSVComparacion.T_VARIANT;
        boolean ok = !conocido || comparacion.asignacionCompatible(destino.tipo, valor.tipo);
        // Un procedimiento no recibe valores ni los devuelve: la asignación es incompatible.
        boolean conProc = destino.proc || valor.proc;
        if (conProc) {
            conocido = true;
            ok = false;
        }
        String tipos = nombreTipoSem(destino.tipo) + " <- " + nombreTipoSem(valor.tipo);
        // Asignación compuesta (+= -= *= /=): primero debe ser válida la OPERACIÓN según su
        // tabla (suma, resta, multiplicación, división) y luego su resultado asignable al destino.
        // Ej.: Cadena -= Cadena no existe en resta.csv aunque Cadena <- Cadena sí es compatible.
        int opTabla = operacionDeCompuesta(op);
        if (opTabla != 0 && conocido && !conProc) {
            int res = comparacion.ObtenerTipoPorTipos(destino.tipo, valor.tipo, opTabla);
            ok = res > 0 && comparacion.asignacionCompatible(destino.tipo, res);
            tipos = nombreTipoSem(destino.tipo) + " " + op.getLexema().trim() + " "
                    + nombreTipoSem(valor.tipo);
        }
        AsigPend p = asigPendientes.remove(op);
        if (p != null && !(p.soloSiFalla && ok)) {
            muestreo.registrar(p.linea, p.regla, destino.proc ? "TReg" : tipoCorto(destino.tipo), p.descripcion,
                    !conocido ? "ACEPT (tipo no verificable: " + tipos + ")"
                            : ok ? "ACEPT (" + tipos + ")"
                                    : "ERROR (" + tipos + ": tipos incompatibles)",
                    valorRhs(op, valor, p.lexema),
                    p.ambito, p.posicion);
        }
        if (!ok && opTabla != 0) {
            // Asignación compuesta (+= -= *= /=): es la regla 1021/1022 de Semántica 2, así
            // que su error se clasifica en Semántica 2 (como la referencia), no en Semántica 1.
            int regla = p != null ? p.regla : 1022;
            ContadorCiclos.ERRORES++;
            String motivo = "Tipos de datos incompatibles en la operación de asignación compuesta ("
                    + op.getLexema().trim() + ")";
            erroresSintaxis.add(new ErrorEntry(
                    "Semántica 2",
                    "Semántica 2 (" + regla + "): " + motivo,
                    linea,
                    "parser",
                    ErrorEntry.Tipo.SEMANTICA,
                    destino.texto + " " + op.getLexema().trim() + " " + valor.texto,
                    obtenerAmbitoActual()));
            System.out.println("[SEM2-ERR] ln=" + linea + " | " + regla + " | " + motivo);
        } else if (!ok) {
            registrarErrorSemantico(linea,
                    destino.texto + " " + op.getLexema().trim() + " " + valor.texto,
                    "Tipos de datos incompatibles en la operación de asignación");
        }
    }

    /** Operador de la tabla que usa una asignación compuesta (0 si es "=" u otra sin tabla). */
    private static int operacionDeCompuesta(Token op) {
        return switch (op.getLexema().trim()) {
            case "+=" -> -11;
            case "-=" -> -12;
            case "*=" -> -13;
            case "/=" -> -14;
            default -> 0;
        };
    }

    // ---------- Regla 1: condiciones ----------

    private void iniciarCondicion(int clave, String descripcion, Token t) {
        condClave = clave;
        condDescripcion = descripcion;
        condLinea = t.getLinea();
        condColumna = t.getColumna();
        condProf = 0;
        condTexto.setLength(0);
        condToks.clear();
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
                // La condición debe ser lógica (Booleanas). Variant (id no declarado) se acepta:
                // su error ya lo da la regla 1100.
                contarTemporalesExpresion(condToks, condLinea);
                Val v = evaluar(condToks);
                boolean esNullCond = condToks.size() == 1 && condToks.get(0).getTokenClass() == -74;
                boolean ok = !esNullCond && (v.tipo == LeerCSVComparacion.T_BOOLEAN
                        || v.tipo == LeerCSVComparacion.T_VARIANT);
                if (ok) {
                    muestreo.registrar(
                            condLinea,
                            condClave,
                            tipoCorto(LeerCSVComparacion.T_BOOLEAN),
                            condDescripcion,
                            "Activada",
                            valorSem(condToks),
                            obtenerAmbitoActual(),
                            condColumna);
                } else {
                    filaSitConError(condLinea, condClave, tipoCorto(LeerCSVComparacion.T_BOOLEAN),
                            false, null,
                            "la condición del " + condDescripcion.toLowerCase()
                                    + " debe ser lógica (Booleanas), se encontró "
                                    + nombreTipoSem(v.tipo),
                            valorSem(condToks), condColumna);
                }
                condClave = 0;
                condToks.clear();
                return;
            }
        } else if (condProf == 0) {
            return; // palabra clave u otros tokens antes del "("
        }

        if (condTexto.length() > 0) {
            condTexto.append(' ');
        }
        condTexto.append(lex);
        condToks.add(t);
    }

    // ---------- Reglas 3, 4 y 5: accesos a arreglo ----------

    private void abrirAccesoArreglo(Token corcheteAbre) {
        // id[...] dentro de una operación: el elemento es UN solo operando.
        if (EstadoDeOperacion && llamadaOpProf == 0) {
            llamadaOpPendiente = true;
            llamadaOpAbre = -47;
            llamadaOpCierra = -48;
        }

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
            a.cerrado = true;
            dimensionCerrada(a);
            registrarAccesoArreglo(a);
            return;
        }
        if (tc == -7 && a.par == 0) { // , separa índices
            a.nuevoIndice();
            dimensionCerrada(a); // el índice anterior ya está completo
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
     *  1040 cada índice debe ser Decimal
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
            filaSitConError(a.linea, 1030, "Dim 0", false, null,
                    a.id + " no es un arreglo (clase "
                            + (a.clase.isEmpty() ? "desconocida" : a.clase)
                            + "); no puede usarse con índices",
                    a.id, base);
            return; // lo demás sería un error secundario
        }

        // Cada dimensión con índice y declarada se revisó al cerrarse su índice
        // (dimensionCerrada); aquí solo quedan las que faltan o sobran.
        if (a.validadas < Math.min(usados, declaradasDe(a)) && usados > 0) {
            dimensionCerrada(a);
        }
        int declaradas = declaradasDe(a);
        String topeDim = "Dim " + tamanosDe(a).length;
        boolean primerFallo1030 = true;
        int total = Math.max(usados, declaradas);
        for (int k = 0; k < total; k++) {
            if (k < usados && k < declaradas) {
                continue;
            }
            int pos = base + k + 1;
            if (k >= declaradas && !primerFallo1030) {
                continue; // los índices sobrantes se marcan una sola vez
            }
            String detalle = k >= usados
                    ? "dimensión " + (k + 1) + ": falta el índice (" + a.id + " declara "
                            + declaradas + " y se usaron " + usados + ")"
                    : "dimensión " + (k + 1) + ": índice sobrante (" + a.id + " solo declara "
                            + declaradas + " dimensión(es))";
            if (primerFallo1030) { // un solo ErrorEntry por acceso; las demás filas son detalle
                filaSitConError(a.linea, 1030, topeDim, false, null, detalle, a.id, pos);
                primerFallo1030 = false;
            } else {
                fila(a.linea, 1030, topeDim, false, detalle, a.id, pos);
            }
        }
    }

    private static String[] tamanosDe(AccesoArreglo a) {
        return a.tarr == null ? new String[0] : a.tarr.split(",");
    }

    private static int declaradasDe(AccesoArreglo a) {
        int declaradas = tamanosDe(a).length;
        try {
            if (a.dimarr != null) {
                declaradas = Integer.parseInt(a.dimarr.trim());
            }
        } catch (NumberFormatException e) {
            // se conserva la cantidad deducida de los tamaños
        }
        return declaradas;
    }

    /**
     * Se cerró el índice de la siguiente dimensión (coma o "]"): se revisa en ese momento, así las
     * filas quedan en el orden del código fuente (los accesos anidados dentro de un índice
     * ya dejaron las suyas).
     */
    private void dimensionCerrada(AccesoArreglo a) {
        if (!a.existe || !"Arreglo".equals(a.clase)) {
            return;
        }
        int k = a.validadas;
        if (k >= a.idxLexemas.size() - (a.cerrado ? 0 : 1) || k >= declaradasDe(a)) {
            return;
        }
        a.validadas++;
        String[] tamanos = tamanosDe(a);
        int declaradas = declaradasDe(a);
        String topeDim = "Dim " + tamanos.length; // dimensiones declaradas (tabla de símbolos)
        int pos = a.columna * 100 + k + 1;

        filaSitConError(a.linea, 1030, topeDim, true,
                "dimensión " + (k + 1) + " de " + declaradas, null, a.id, pos);

        List<Token> toks = a.idxToks.get(k);
        contarTemporalesExpresion(toks, a.linea); // Semántica 1: operaciones del índice
        String textoIdx = valorSem(toks);

        // Regla 4 (1040): el índice debe ser Decimal. Variant (no declarado / sin tipo
        // conocido) se acepta: su error ya lo da otra regla.
        int tipo = evaluar(toks).tipo;
        // El índice es Decimal (TDec). null y los procedimientos no tienen valor entero.
        boolean sinValor = (toks.size() == 1 && toks.get(0).getTokenClass() == -74)
                || (!toks.isEmpty() && idInfos.get(toks.get(0)) != null
                        && "Procedimiento".equals(idInfos.get(toks.get(0)).clase));
        boolean entero = !sinValor
                && (tipo == LeerCSVComparacion.T_DEC || tipo == LeerCSVComparacion.T_VARIANT);
        filaSitConError(a.linea, 1040, TOPE_ENTERO, entero,
                "tipo del índice: " + nombreTipoSem(tipo),
                "el índice " + (k + 1) + " de " + a.id
                        + " debe ser Decimal, se encontró " + nombreTipoSem(tipo),
                textoIdx, pos);
        if (!entero) {
            return; // sin 1050 para esta dimensión
        }

        // Regla 5 (1050): solo si hay tamaño declarado para esa dimensión.
        long tam;
        try {
            tam = Integer.parseInt(tamanos[k].trim()); // un tamaño fuera de int no se puede verificar
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return;
        }
        String topeRango = "0.." + (tam - 1); // rango válido según la declaración
        Long valor = valorConstanteEntero(toks);
        if (valor == null) {
            filaSitConError(a.linea, 1050, topeRango, true,
                    "dimensión " + (k + 1) + ", tamaño declarado=" + tam
                            + "; índice no constante, rango no verificable en compilación",
                    null, textoIdx, pos);
        } else {
            filaSitConError(a.linea, 1050, topeRango, valor >= 0 && valor < tam,
                    "dimensión " + (k + 1) + ", tamaño declarado=" + tam + ", índice=" + valor,
                    "el índice " + valor + " está fuera del rango 0.." + (tam - 1)
                            + " de la dimensión " + (k + 1) + " de " + a.id,
                    textoIdx, pos);
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
        int brk = 0;            // [ ] abiertos dentro de la captura (las comas dentro no separan argumentos)
        int argIni = 0;         // índice en toks donde empieza el argumento en curso (llamadas)
        int emitidos = 0;       // argumentos ya revisados (llamadas)
        boolean fallo1150 = false;
        int sec = 0;            // ";" ya procesados en un for (1080 al 1.º, 1081 al 2.º)
        final List<SemanticAnalyzerV3.Argumento> nativaArgs = new ArrayList<>(); // parámetros ya validados (nativas)
        int ultimoSep = -1;

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
            case 1010 -> "IF";
            case 1011 -> "ELSEIF";
            case 1012 -> "WHILE";
            case 1013 -> "DO-WHILE";
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
    private boolean fila(int linea, int clave, String tope, boolean ok, String motivo,
            String lexema, int pos) {
        return muestreo.registrar(
                linea, clave, tope, descripcionRegla(clave),
                ok ? "ACEPT" : "ERROR (" + motivo + ")",
                lexema, obtenerAmbitoActual(), pos);
    }

    /** Fila + ErrorEntry cuando la regla falla. */
    private void filaConError(int linea, int clave, String tope, boolean ok, String motivo,
            String lexema, int pos) {
        filaSitConError(linea, clave, tope, ok, null, motivo, lexema, pos);
    }

    /**
     * Igual que filaConError, pero la situación aceptada puede llevar detalle:
     * "ACEPT (detalle)". Si falla: "ERROR (motivo)" + ErrorEntry (sin duplicar).
     */
    private void filaSitConError(int linea, int clave, String tope, boolean ok, String detalleOk,
            String motivo, String lexema, int pos) {
        String situacion = ok
                ? ((detalleOk == null || detalleOk.isEmpty()) ? "ACEPT" : "ACEPT (" + detalleOk + ")")
                : "ERROR (" + motivo + ")";
        boolean nueva = muestreo.registrar(
                linea, clave, tope, descripcionRegla(clave), situacion,
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

    // ---------- Tope Pila ----------
    // Columna "Tope Pila" del muestreo: lo que la regla tiene en el tope de la pila
    // semántica al evaluarse (tabla del profesor: id, Arr/id/par, TV, CE, CB...). Cada
    // llamada lo obtiene del elemento real (clase del token, tipo declarado, tamaño de la
    // dimensión, nombre del parámetro...), nunca del número de regla.

    private static final String TOPE_ENTERO = "TDec";
    private static final String TOPE_RECEPTOR = "Var/Par/Arr";

    /** Símbolo del token en la pila: "id" para cualquier identificador; si no, su tipo. */
    private static String topeSimbolo(int tc) {
        if (esIdToken(tc)) {
            return "id";
        }
        return tipoCorto(LeerCSVComparacion.tipoDeToken(tc));
    }

    /** Nombre corto del tipo en la nomenclatura del profesor: TDec, TCadena, TBoolean... */
    private static String tipoCorto(int t) {
        switch (t) {
            case LeerCSVComparacion.T_BIN: return "TBin";
            case LeerCSVComparacion.T_DEC: return "TDec";
            case LeerCSVComparacion.T_OCT: return "TOct";
            case LeerCSVComparacion.T_HEX: return "THex";
            case LeerCSVComparacion.T_REAL: return "TReal";
            case LeerCSVComparacion.T_EXP: return "TExp";
            case LeerCSVComparacion.T_CADENA: return "TCadena";
            case LeerCSVComparacion.T_CARACTER: return "TChar";
            case LeerCSVComparacion.T_BOOLEAN: return "TBoolean";
            default: return "TVariant";
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
        // El primer identificador del encabezado del for (inicialización o valor 1) se declara
        // ahí mismo: no es un uso y no genera fila.
        if (uso && !(info.declarado && esPrimerIdDelFor())) {
            fila(t.getLinea(), 1100, topeSimbolo(t.getTokenClass()), info.declarado,
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
            filaConError(op.getLinea(), 1070, tipoCorto(LeerCSVComparacion.T_DEC), ok,
                    "el operando de " + op.getLexema().trim()
                            + " debe ser Decimal (#D), se encontró " + nombreTipoSem(tp),
                    id, op.getColumna());
        }

        alimentarCapturas(t);
    }

    // ---------- Regla 9 (1090): clase del receptor ----------

    private void validarClaseReceptor(Token operador, boolean soloSiFalla) {
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
        if (ok && soloSiFalla) {
            return; // inicialización del for: el id se declara en el encabezado, solo se muestra si falla
        }
        filaConError(operador.getLinea(), 1090, TOPE_RECEPTOR, ok, motivo,
                id, operador.getColumna());
    }

    // ---------- Capturas de expresión ----------

    private void iniciarCapturaParen(int clase, Token origen, SwitchCtx sw) {
        if (clase == CAP_FOR) {
            enEncabezadoFor = true;
        }
        capsParen.add(new CapParen(clase, origen, sw));
    }

    /** Punto único por el que pasa cada terminal consumido (ID o no). */
    /** Captura del for en curso si el último paréntesis abierto es el de su encabezado. */
    private CapParen forEnCurso() {
        if (capsParen.isEmpty()) {
            return null;
        }
        CapParen c = capsParen.get(capsParen.size() - 1);
        return (c.clase == CAP_FOR && c.iniciada) ? c : null;
    }

    /** El token que se consume es el primero del encabezado: el id inicial o el valor 1 del for. */
    private boolean esPrimerIdDelFor() {
        CapParen c = forEnCurso();
        return c != null && (c.toks.isEmpty() || idTrasComaEnInitFor(c));
    }

    /** Primer id de una inicialización posterior del encabezado: for(#Di=0, #Dj=1; ...). */
    private boolean idTrasComaEnInitFor(CapParen c) {
        if (c.toks.isEmpty() || c.par != 1 || c.brk != 0
                || c.toks.get(c.toks.size() - 1).getTokenClass() != -7) {
            return false;
        }
        for (Token k : c.toks) {
            if (k.getTokenClass() == -9) {
                return false;
            }
        }
        return true;
    }

    /** Estamos en el primer componente del encabezado (antes del primer ';'): la inicialización. */
    private boolean enInicializacionDelFor() {
        CapParen c = forEnCurso();
        if (c == null) {
            return false;
        }
        for (Token k : c.toks) {
            if (k.getTokenClass() == -9) {
                return false;
            }
        }
        return true;
    }

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
                if (tc == -47) {
                    c.brk++;
                } else if (tc == -48) {
                    c.brk--;
                }
                if (c.clase == CAP_FOR && tc == -9 && c.par == 1 && c.brk == 0) {
                    if (c.sec == 0) {
                        emitirFor1080(new ArrayList<>(c.toks));
                    } else if (c.sec == 1) {
                        emitirFor1081(new ArrayList<>(c.toks.subList(Math.min(c.ultimoSep + 1, c.toks.size()), c.toks.size())));
                    }
                    c.sec++;
                    c.ultimoSep = c.toks.size();
                }
                if (c.clase == CAP_NATIVA && tc == -7 && c.par == 1 && c.brk == 0) {
                    // Semántica 3: el parámetro se valida en cuanto se completa (orden del código)
                    parametroNativa(c, new ArrayList<>(c.toks.subList(Math.min(c.argIni, c.toks.size()), c.toks.size())));
                    c.argIni = c.toks.size() + 1;
                }
                if (c.clase == CAP_CALL) {
                    if (tc == -7 && c.par == 1 && c.brk == 0) { // coma que cierra un argumento
                        List<Token> argCerrado = new ArrayList<>(c.toks.subList(Math.min(c.argIni, c.toks.size()), c.toks.size()));
                        contarTemporalesExpresion(argCerrado, c.origen.getLinea()); // Semántica 1
                        revisarArgumento(c, argCerrado);
                        c.argIni = c.toks.size() + 1;
                    }
                }
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
            case CAP_CALL -> {
                // Semántica 1: operaciones del último argumento (los anteriores se contaron en su coma)
                contarTemporalesExpresion(new ArrayList<>(c.toks.subList(Math.min(c.argIni, c.toks.size()),
                        c.toks.size())), c.origen.getLinea());
                finalizarLlamada(c);
            }
            case CAP_CONSOLE -> finalizarConsole(c);
            case CAP_NATIVA -> finalizarNativa(c);
            default -> {
            }
        }
    }

    // ---------- Regla 6: SWITCH (1060) y CASE (1061) ----------

    private void finalizarSwitch(CapParen c) {
        Val v = evaluar(c.toks);
        String texto = textoDe(c.toks);
        // null y los procedimientos no tienen valor Cadena ni Decimal: no son selector válido.
        boolean sinValor = (c.toks.size() == 1 && c.toks.get(0).getTokenClass() == -74)
                || (!c.toks.isEmpty() && idInfos.get(c.toks.get(0)) != null
                        && "Procedimiento".equals(idInfos.get(c.toks.get(0)).clase));
        boolean valido = !sinValor && (v.tipo == LeerCSVComparacion.T_CADENA
                || v.tipo == LeerCSVComparacion.T_DEC
                || v.tipo == LeerCSVComparacion.T_VARIANT);

        c.sw.texto = texto;
        // Variant, o 1060 con error (se revisan los CASE "como si fuera Variant"):
        // el tipo de referencia lo fijará el primer CASE válido.
        c.sw.tipoRef = (valido && v.tipo != LeerCSVComparacion.T_VARIANT) ? v.tipo : null;

        filaConError(c.origen.getLinea(), 1060, "TDec/TCadena", valido,
                "la expresión del switch debe ser Cadena o Decimal (#D), se encontró "
                        + nombreTipoSem(v.tipo),
                valorSem(c.toks), c.origen.getColumna());
    }

    private void finalizarCase(CapCase k) {
        if (switches.isEmpty()) {
            return;
        }
        SwitchCtx sw = switches.peek();
        Val v = evaluar(k.toks);
        String lexema = valorSem(k.toks); // Valor Real: el case; el selector ya quedó en su fila 1060
        int tipo = v.tipo;
        boolean esNullCase = k.toks.size() == 1 && k.toks.get(0).getTokenClass() == -74;
        boolean cadenaODec = tipo == LeerCSVComparacion.T_CADENA || tipo == LeerCSVComparacion.T_DEC;
        boolean variant = tipo == LeerCSVComparacion.T_VARIANT && !esNullCase;

        // Tope: tipo ya fijado por el switch / primer case; mientras no exista, los dos permitidos.
        String topeCase = sw.tipoRef != null ? tipoCorto(sw.tipoRef) : "TDec/TCadena";
        boolean ok = true;
        String motivo = "";
        if (!cadenaODec && !variant) {
            ok = false;
            motivo = "el case debe ser Cadena o Decimal (#D), se encontró "
                    + (esNullCase ? "null" : nombreTipoSem(tipo));
        } else if (sw.tipoRef != null) {
            if (!variant && tipo != sw.tipoRef) {
                ok = false;
                motivo = "el case es " + nombreTipoSem(tipo)
                        + " y el tipo de referencia del switch es " + nombreTipoSem(sw.tipoRef);
            }
        } else if (cadenaODec) {
            sw.tipoRef = tipo; // primer CASE válido: fija el tipo de los siguientes
        }
        filaConError(k.origen.getLinea(), 1061, topeCase, ok, motivo, lexema, k.origen.getColumna());
    }

    // ---------- Regla 7: ! y ~ (1070) ----------

    private void finalizarUnarioLogico(CapParen c) {
        Val v = evaluar(c.toks);
        boolean esNot = c.clase == CAP_NOT;
        boolean ok;
        String motivo;
        // null y los procedimientos no tienen valor: no son operando válido de ! ni de ~.
        boolean sinValor = (c.toks.size() == 1 && c.toks.get(0).getTokenClass() == -74)
                || (!c.toks.isEmpty() && idInfos.get(c.toks.get(0)) != null
                        && "Procedimiento".equals(idInfos.get(c.toks.get(0)).clase));
        if (esNot) {
            ok = !sinValor && (v.tipo == LeerCSVComparacion.T_BOOLEAN || v.tipo == LeerCSVComparacion.T_VARIANT);
            motivo = "el operando de ! debe ser Booleano, se encontró " + nombreTipoSem(v.tipo);
        } else {
            ok = !sinValor && (esEntero(v.tipo) || v.tipo == LeerCSVComparacion.T_VARIANT);
            motivo = "el operando de ~ debe ser Decimal, Octal, Binario o Hexadecimal, se encontró "
                    + nombreTipoSem(v.tipo);
        }
        filaConError(c.origen.getLinea(), 1070,
                esNot ? tipoCorto(LeerCSVComparacion.T_BOOLEAN) : "TDec/TBin/TOct/THex", ok, motivo,
                valorSem(c.toks), c.origen.getColumna());
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

        // 1080 y 1081 ya se emitieron al consumir cada ";" (ver alimentarCapturas), para que las
        // filas queden en el orden del código fuente; aquí solo falta el incremento (1082).
        if (c.sec < 1) {
            emitirFor1080(ini);
        }
        if (c.sec < 2) {
            emitirFor1081(cond);
        }
        emitirFor1082(inc);
    }

    /** 1080: cada inicialización debe ser "ID = EXP" (asignación simple a un identificador). */
    private void emitirFor1080(List<Token> ini) {
        Set<String> vistos = new HashSet<>();
        for (List<Token> item : separarPorComas(ini)) {
            if (item.isEmpty()) {
                continue;
            }
            // el mismo id repetido en el encabezado ya es error 544: no genera una segunda fila
            if (!vistos.add(item.get(0).getLexema().trim())) {
                continue;
            }
            boolean ok = esFormaAsignacion(item);
            filaConError(item.get(0).getLinea(), 1080, "id", ok,
                    "la inicialización del for debe ser una asignación (=) a un identificador",
                    item.get(0).getLexema().trim(), item.get(0).getColumna());
        }
    }

    /** 1081: la comparación debe ser lógica (Variant se acepta; null no). */
    private void emitirFor1081(List<Token> cond) {
        if (cond.isEmpty()) {
            return;
        }
        contarTemporalesExpresion(cond, cond.get(0).getLinea());
        Val v = evaluar(cond);
        boolean esNullC = cond.size() == 1 && cond.get(0).getTokenClass() == -74;
        boolean ok = !esNullC && (v.tipo == LeerCSVComparacion.T_BOOLEAN
                || v.tipo == LeerCSVComparacion.T_VARIANT);
        filaConError(cond.get(0).getLinea(), 1081, tipoCorto(LeerCSVComparacion.T_BOOLEAN), ok,
                "la condición del for debe ser lógica (Booleanas), se encontró "
                        + (esNullC ? "null" : nombreTipoSem(v.tipo)),
                valorSem(cond), cond.get(0).getColumna());
    }

    /** 1082: incremento o decremento: ++ / -- sobre un identificador. */
    private void emitirFor1082(List<Token> inc) {
        for (List<Token> item : separarPorComas(inc)) {
            if (item.isEmpty()) {
                continue;
            }
            boolean ok = esFormaIncremento(item);
            filaConError(item.get(0).getLinea(), 1082, "++/--", ok,
                    "el tercer componente del for debe ser un incremento o decremento (++ o --)",
                    valorIncremento(item), item.get(0).getColumna());
        }
    }

    /** Valor Real del 1082: el identificador que se incrementa. */
    private static String valorIncremento(List<Token> item) {
        for (Token t : item) {
            if (esIdToken(t.getTokenClass())) {
                return t.getLexema().trim();
            }
        }
        return textoDe(item);
    }

    private void forEach(List<Token> v1, List<Token> v2, CapParen c) {
        if (v1.isEmpty() || v2.isEmpty()) {
            return;
        }
        Val a = evaluar(v1);
        Val b = evaluar(v2);
        String val1 = valorSem(v1);
        String val2 = valorSem(v2);
        int linea = v1.get(0).getLinea();
        int pos = v1.get(0).getColumna();
        int t1 = a.tipo, t2 = b.tipo;
        boolean arr2 = b.arreglo && b.idSimple != null;
        boolean v2Null = v2.size() == 1 && v2.get(0).getTokenClass() == -74;
        // Valor 2 sin declarar (Variant): su tipo se toma del prefijo de su nombre (@ = Cadena,
        // #D = Decimal...); el error de "no declarado" ya lo da 1100. Un id #D se toma como arreglo.
        int t2Declarado = t2; // Variant si el valor 2 no está declarado
        if (t2 == LeerCSVComparacion.T_VARIANT && t1 != LeerCSVComparacion.T_VARIANT && !v2Null && v2.size() == 1
                && esIdToken(v2.get(0).getTokenClass()) && idInfos.get(v2.get(0)) != null
                && !idInfos.get(v2.get(0)).declarado) {
            t2 = tipoSemanticoDeSimbolo(obtenerTipoDeToken(v2.get(0).getTokenClass()));
            arr2 = t2 != LeerCSVComparacion.T_CADENA && t2 != LeerCSVComparacion.T_VARIANT;
        }
        // El valor 1 puede ser un identificador o una constante (ej. for(5 : cad)); una constante
        // no puede recibir los elementos de un valor 2 conocido (cadena/arreglo declarado). Una
        // expresión no es válida.
        int tc1 = v1.get(0).getTokenClass();
        boolean v1EsConst = v1.size() == 1 && ((tc1 <= -53 && tc1 >= -59) || tc1 == -72 || tc1 == -73);
        boolean v1EsId = v1.size() == 1 && (esIdToken(tc1) || v1EsConst);

        if (!v1EsId || (v1EsConst && t2Declarado != LeerCSVComparacion.T_VARIANT && !v2Null)) {
            int clave = (t2 == LeerCSVComparacion.T_CADENA && !arr2) ? 1083 : 1084;
            filaConError(linea, clave, clave == 1083 ? TOPE_ENTERO : "Arr " + tipoCorto(t1), false,
                    v1EsId ? "el valor 1 del for no puede ser una constante" : "el valor 1 del for debe ser un identificador",
                    clave == 1083 ? val1 : val2, pos);
            return;
        }

        if (v2Null) {
            filaConError(linea, 1084, "Arr " + tipoCorto(t1), false,
                    "el valor 2 debe ser un arreglo o una cadena, se encontró null", val2, pos);
        } else if (t2 == LeerCSVComparacion.T_VARIANT && !arr2) {
            // Valor 2 Variant: se toma como arreglo del tipo del valor 1.
            // Si ambos son Variant, se aceptan ambas reglas.
            if (t1 == LeerCSVComparacion.T_VARIANT) {
                filaConError(linea, 1083, TOPE_ENTERO, true, "", val1, pos);
            }
            filaConError(linea, 1084, "Arr " + tipoCorto(t1), true, "", val2, pos);
        } else if (arr2) {
            boolean ok = t1 == LeerCSVComparacion.T_VARIANT || t1 == t2;
            filaConError(linea, 1084, "Arr " + tipoCorto(t1 == LeerCSVComparacion.T_VARIANT ? t2 : t1), ok,
                    "el valor 1 es " + nombreTipoSem(t1)
                            + " y el arreglo es de " + nombreTipoSem(t2),
                    val2, pos);
        } else if (t2 == LeerCSVComparacion.T_CADENA) {
            boolean ok = t1 == LeerCSVComparacion.T_DEC || t1 == LeerCSVComparacion.T_VARIANT;
            filaConError(linea, 1083, TOPE_ENTERO, ok,
                    "al recorrer una cadena el valor 1 debe ser Decimal (#D), se encontró "
                            + nombreTipoSem(t1),
                    val1, pos);
        } else {
            filaConError(linea, 1084, "Arr " + tipoCorto(t1), false,
                    "el valor 2 debe ser un arreglo o una cadena, se encontró "
                            + (b.idSimple == null ? "un elemento o expresión de tipo " : "un valor de tipo ")
                            + nombreTipoSem(t2),
                    val2, pos);
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
        return i < item.size() - 1 && esAsignacion(item.get(i).getTokenClass())
                && item.get(i).getLexema().trim().equals("=");
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
        List<Object[]> temps = null; // si no es null: {tipo, token|null} de cada temporal que la evaluación crearía

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
                    if (temps != null) {
                        temps.add(new Object[] { LeerCSVComparacion.T_VARIANT, null });
                    }
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
            Val res;
            if (LeerCSVComparacion.tieneTabla(c)) {
                int rt = comparacion.ObtenerTipoPorTipos(l.tipo, r.tipo, c);
                res = new Val(rt < 0 ? LeerCSVComparacion.T_VARIANT : rt);
            } else {
                res = new Val(LeerCSVComparacion.T_VARIANT);
            }
            if (temps != null) {
                temps.add(new Object[] { res.tipo, null }); // cada operación binaria deja un temporal del tipo de su resultado
            }
            return res;
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
                if (temps != null && (inf == null || !inf.declarado)) {
                    temps.add(new Object[] { LeerCSVComparacion.T_VARIANT, tok }); // id sin declarar: su valor se materializa en un temporal Variant
                }
                v.idSimple = tok;
                v.arreglo = inf != null && inf.declarado && "Arreglo".equals(inf.clase);
                boolean sinDeclarar = inf == null || !inf.declarado;
                while (!fin() && (tc() == -47 || tc() == -49)) {
                    if (temps != null && sinDeclarar) {
                        contarInterior(tc() == -47 ? -48 : -50); // sus argumentos/índices no tendrán fila propia
                    }
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
            if (c <= -75 && c >= -94) { // funciones predefinidas: tipo de retorno de la llamada (Semántica 3)
                i++;
                List<Token> interior = null;
                if (!fin() && tc() == -49) {
                    int ini = i;
                    saltarBalanceado(-50);
                    interior = t.subList(ini + 1, Math.max(ini + 1, i - 1));
                }
                return new Val(tipoRetornoNativa(tok, interior));
            }
            i++;
            return new Val(LeerCSVComparacion.T_VARIANT);
        }

        /** Cuenta los temporales de los argumentos/índices que empiezan en i (el "(" o "[" actual). */
        void contarInterior(int cierre) {
            int abre = (cierre == -50) ? -49 : -47;
            int prof = 0, ini = i + 1;
            List<List<Token>> partes = new ArrayList<>();
            int k = i;
            int segIni = ini;
            while (k < t.size()) {
                int c = t.get(k).getTokenClass();
                if (c == abre || c == -49 || c == -47) {
                    prof++;
                } else if (c == cierre || c == -50 || c == -48) {
                    prof--;
                    if (prof == 0) {
                        if (k > segIni) partes.add(t.subList(segIni, k));
                        break;
                    }
                } else if (c == -7 && prof == 1) {
                    partes.add(t.subList(segIni, k));
                    segIni = k + 1;
                }
                k++;
            }
            for (List<Token> parte : partes) {
                if (!parte.isEmpty()) {
                    EvalExpr sub = new EvalExpr(parte);
                    sub.temps = temps;
                    sub.expr(1);
                }
            }
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
        int ambito = ErrorEntry.AMBITO_DESCONOCIDO; // ámbito vigente al leer el return

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
    private boolean defDuplicada = false; // la def en curso repite un id ya declarado en su ámbito (queda la primera)
    private Token ultimoIdTok = null;
    private CapReturn capReturn = null;

    // Llamada tratada como un solo operando de la operación infija->postfija.
    private boolean llamadaOpPendiente = false;
    private int llamadaOpProf = 0;
    private int llamadaOpAbre = -49, llamadaOpCierra = -50; // () para llamadas, [] para arreglos
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

    /**
     * Compatibilidad parámetro <- argumento: la misma que en las asignaciones
     * (asignacionCompatible, sobre suma.csv). Un entero entra en Real/Exponencial.
     * Única excepción: un parámetro Cadena no acepta otro tipo, porque suma.csv deja
     * que Cadena absorba todo (concatenación) y la diapositiva 18 marca ERROR
     * fun1(2,5,5) en la posición 2. Variant se acepta.
     */
    private boolean argumentoCompatible(int dest, int orig) {
        if (dest == LeerCSVComparacion.T_VARIANT || orig == LeerCSVComparacion.T_VARIANT
                || dest == orig) {
            return true;
        }
        if (dest == LeerCSVComparacion.T_CADENA) {
            return false;
        }
        return comparacion.asignacionCompatible(dest, orig);
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
        // Regla 14 (1140) en un procedimiento: se resuelve al terminar la expresión del return
        // (en finalizarReturn), para que quede después de las filas de lo que devuelve.
        capReturn = new CapReturn(ret, f);
        capReturn.ambito = obtenerAmbitoActual();
    }

    /** Regla 13 (1131): el tipo devuelto debe ser el declarado para la función. */
    private void finalizarReturn(CapReturn k) {
        ambitoForzado = k.ambito;
        try {
            finalizarReturn0(k);
        } finally {
            ambitoForzado = -999;
        }
    }

    private void finalizarReturn0(CapReturn k) {
        contarTemporalesExpresion(k.toks, k.origen.getLinea());
        if (k.f.procedimiento) {
            // Regla 14 (1140): un procedimiento no debe tener return.
            filaConError(k.origen.getLinea(), 1140, "TVoid", false,
                    "el procedimiento " + k.f.id + " no debe contener return",
                    k.toks.isEmpty() ? k.f.id : valorArg(k.toks), k.origen.getColumna());
            return;
        }
        Val v = evaluar(k.toks);
        int declarado = tipoSemanticoDeSimbolo(k.f.tipo);
        // Regla 13 (1130): la función tiene (al menos) un return. Se registra
        // una vez, en la línea del primer return, no en la declaración.
        if (!k.f.returnRegistrado) {
            k.f.returnRegistrado = true;
            fila(k.origen.getLinea(), 1130, "return", true, "", k.f.id, k.origen.getColumna());
        }
        // 1131 exige el MISMO tipo que declara la función (una función Real no devuelve Decimal);
        // Variant (id no declarado) se acepta porque su error ya lo da 1100, pero null es un
        // valor sin tipo concreto y no cumple el tipo declarado.
        boolean esNull = k.toks.size() == 1 && k.toks.get(0).getTokenClass() == -74;
        boolean ok = !esNull && (declarado == LeerCSVComparacion.T_VARIANT
                || v.tipo == LeerCSVComparacion.T_VARIANT || declarado == v.tipo);
        filaConError(k.origen.getLinea(), 1131, tipoCorto(declarado), ok,
                "la función " + k.f.id + " devuelve " + nombreTipoSem(declarado)
                        + " y el return entrega " + nombreTipoSem(v.tipo),
                valorSem(k.toks), k.origen.getColumna());
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
                fila(f.linea, 1140, "TVoid", true, "", f.id, f.columna);
            }
        } else {
            // Regla 13 (1130): solo falla aquí; el ACEPT se registra en el return.
            if (f.returns == 0) {
                filaConError(f.linea, 1130, "return", false,
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
            llamadaOpAbre = -49;
            llamadaOpCierra = -50;
        }
    }

    private void finalizarLlamada(CapParen c) {
        Token callee = c.origen;
        String id = callee.getLexema().trim();
        boolean comoProc = callee.getTokenClass() == -107;
        int clave = comoProc ? 1110 : 1120;
        String topeCallee = comoProc ? "proc" : "func";
        int linea = callee.getLinea();
        int col = callee.getColumna();

        int amb = buscarAmbitoSimbolo(id);
        if (amb < 0) {
            // El error de "no declarado" ya lo registra la regla 1100 / el parser: no hay
            // símbolo, así que 1110/1120 no se ejecutan (no se duplica la fila). Sus argumentos
            // se evalúan igual (dejan sus temporales en la numeración del muestreo).
            if (!c.toks.isEmpty()) {
                for (List<Token> ar : separarPorComas(c.toks)) {
                    if (!ar.isEmpty()) {
                        valorSem(ar);
                    }
                }
            }
            return;
        }

        String clase = tabla.obtenerClase(id, amb);
        clase = clase == null ? "" : clase.trim();
        boolean ok = comoProc ? clase.equals("Procedimiento") : clase.equals("Funcion");
        if (!ok) {
            // El id existe pero no es invocable: no hay parámetros declarados contra los cuales
            // comparar, así que cada argumento enviado queda como 1150 de error (tope "-").
            List<List<Token>> as = c.toks.isEmpty()
                    ? new ArrayList<List<Token>>()
                    : separarPorComas(c.toks);
            String motivo = id + " es de clase " + (clase.isEmpty() ? "desconocida" : clase)
                    + "; se invocó como " + (comoProc ? "procedimiento" : "función")
                    + " pero no lo es";
            String llamadaNo = id + "(" + textoDe(c.toks) + ")";
            if (as.isEmpty()) {
                filaSitConError(linea, 1150, "-", false, null, motivo, llamadaNo, col);
                return;
            }
            boolean primero = true;
            for (int i = 0; i < as.size(); i++) {
                List<Token> ar = as.get(i);
                int p = col * 100 + i + 1;
                int lf = ar.isEmpty() ? linea : ar.get(0).getLinea();
                String val = ar.isEmpty() ? llamadaNo : textoDe(ar);
                if (primero) {
                    filaSitConError(lf, 1150, "-", false, null, motivo, val, p);
                    primero = false;
                } else {
                    fila(lf, 1150, "-", false, motivo, val, p);
                }
            }
            return;
        }

        // Los argumentos se revisan conforme se van cerrando (coma o ")"), así el orden de las
        // filas es el del código fuente: un argumento que es otra llamada deja primero sus
        // propias filas y después las del parámetro del que es argumento.
        FuncInfo f0 = funciones.get(id + "|" + amb);
        Integer declN0 = tabla.obtenerNumParametros(id, amb);
        int declarados0 = declN0 != null ? declN0 : (f0 != null ? f0.paramTipos.size() : 0);
        List<Token> resto = new ArrayList<>(c.toks.subList(Math.min(c.argIni, c.toks.size()), c.toks.size()));
        if (!resto.isEmpty() || c.emitidos > 0) {
            if (!resto.isEmpty() || c.emitidos < declarados0) {
                revisarArgumento(c, resto);
            }
        }
        if (c.emitidos == 0 && declarados0 == 0) {
            String llamada = id + "(" + textoDe(c.toks) + ")";
            filaSitConError(linea, 1150, "-", true, "sin parámetros ni argumentos", null, llamada, col);
            return;
        }
        // Faltan argumentos: un solo 1150 de error, en el primer parámetro sin argumento.
        if (!c.fallo1150 && c.emitidos < declarados0) {
            int i = c.emitidos;
            String topeParam = (f0 != null && i < f0.paramNombres.size()) ? f0.paramNombres.get(i) : "-";
            filaSitConError(linea, 1150, topeParam, false, null,
                    "se enviaron " + c.emitidos + " argumento(s) y " + id + " declara " + declarados0
                            + "; posición " + (i + 1) + ": falta el argumento"
                            + (f0 != null && i < f0.paramNombres.size()
                                    ? " (parámetro " + f0.paramNombres.get(i) + ")" : ""),
                    "-", col * 100 + i + 1);
            c.fallo1150 = true;
        }
    }

    /**
     * Revisa el argumento i-ésimo (i = c.emitidos) de una llamada a una función/procedimiento
     * declarado: 1150 (hay parámetro para él) y, solo si pasa, 1160 (tipo compatible). Tras el
     * primer 1150 fallido no se siguen revisando las posiciones restantes (un solo error).
     */
    private void revisarArgumento(CapParen c, List<Token> arg) {
        Token callee = c.origen;
        String id = callee.getLexema().trim();
        int linea = callee.getLinea();
        int col = callee.getColumna();
        int amb = buscarAmbitoSimbolo(id);
        if (amb < 0) {
            return;
        }
        String clase = tabla.obtenerClase(id, amb);
        clase = clase == null ? "" : clase.trim();
        boolean comoProc = callee.getTokenClass() == -107;
        if (!(comoProc ? clase.equals("Procedimiento") : clase.equals("Funcion"))) {
            return; // callee no invocable: se resuelve al cerrar la llamada
        }
        int i = c.emitidos;
        c.emitidos++;
        if (c.fallo1150) {
            return;
        }
        FuncInfo f = funciones.get(id + "|" + amb);
        Integer declN = tabla.obtenerNumParametros(id, amb);
        int declarados = declN != null ? declN : (f != null ? f.paramTipos.size() : 0);
        int pos = col * 100 + i + 1;
        boolean hayArg = arg != null && !arg.isEmpty();
        String topeParam = (i < declarados && f != null && i < f.paramNombres.size())
                ? f.paramNombres.get(i) : "-";
        if (i >= declarados || !hayArg) {
            String detalle = i >= declarados
                    ? "posición " + (i + 1) + ": argumento sobrante, " + id + " solo declara "
                            + declarados + " parámetro(s)"
                    : "posición " + (i + 1) + ": argumento vacío";
            filaSitConError(hayArg ? arg.get(0).getLinea() : linea, 1150, topeParam, false, null,
                    id + ": " + detalle, hayArg ? valorSem(arg) : "-", pos);
            c.fallo1150 = true;
            return;
        }
        filaSitConError(linea, 1150, topeParam, true, "posición " + (i + 1), null, valorSem(arg), pos);
        if (f == null || i >= f.paramTipos.size()) {
            return;
        }
        Val v = evaluar(arg);
        int tParam = tipoSemanticoDeSimbolo(f.paramTipos.get(i));
        boolean esNull = arg.size() == 1 && arg.get(0).getTokenClass() == -74;
        IdInfo infArg = idInfos.get(arg.get(0));
        boolean esProcArg = infArg != null && "Procedimiento".equals(infArg.clase); // un procedimiento no da valor
        filaConError(arg.get(0).getLinea(), 1160, tipoCorto(tParam),
                !esNull && !esProcArg && argumentoCompatible(tParam, v.tipo),
                "posición " + (i + 1) + ": el argumento es " + (esNull ? "null" : esProcArg ? "un procedimiento (sin valor)" : nombreTipoSem(v.tipo))
                        + " y el parámetro " + f.paramNombres.get(i)
                        + " es " + nombreTipoSem(tParam),
                valorSem(arg), pos);
    }

    // =========================================================
    // SEMÁNTICA 3: REGLAS 2001 A 2018 (funciones nativas)
    //
    // Producciones 63..80 (FUNCION -> CLEAR | SQRT(..) | POW(..,..) ...). Al expandirse se
    // abre una captura de sus paréntesis; al cerrarse, cada argumento se resuelve (si es id,
    // su tipo sale de la tabla de símbolos) y SemanticAnalyzerV3 aplica las reglas _p1/_p2/_p3
    // y la de salida. Los errores no detienen el análisis.
    // =========================================================

    private static final int CAP_NATIVA = 6;
    private static final int CAP_CONSOLE = 7; // argumento de Console.log (para sus temporales)

    /** Temporal de una nativa que espera a saber si es el argumento completo de un Console.log. */
    private static class TemporalPendiente {
        final Token llamada; final FuncionNativa f; final SemanticRuleV3.Tipo tipo; final List<List<Token>> args;
        TemporalPendiente(Token llamada, FuncionNativa f, SemanticRuleV3.Tipo tipo, List<List<Token>> args) {
            this.llamada = llamada; this.f = f; this.tipo = tipo; this.args = args;
        }
    }
    private final Map<CapParen, TemporalPendiente> pendientesConsole = new IdentityHashMap<>();

    /**
     * Cierra el argumento de Console.log: si era una sola llamada nativa, su resultado se
     * imprime directo y no genera temporal (igual que la referencia); si la nativa formaba
     * parte de una expresión, su temporal se registra ahora.
     */
    private void finalizarConsole(CapParen c) {
        TemporalPendiente p = pendientesConsole.remove(c);
        if (p != null && !(c.toks.get(0) == p.llamada && abarcaLlamada(c.toks))) {
            registrarTemporalNativa0(p.llamada, p.f, p.tipo, p.args);
        }
    }

    // Retorno calculado de cada llamada nativa (token de la función -> tipo 1..10) y el
    // temporal que la llamada produce (token -> TDec3...). Se llenan al validar la llamada.
    private final Map<Token, Integer> retornoNativa = new IdentityHashMap<>();
    private final Map<Token, String> temporalNativa = new IdentityHashMap<>();
    // Expresión (primer token + tamaño) -> temporal de Semántica 1 con su resultado.
    private final Map<Token, Object[]> temporalExpresion = new IdentityHashMap<>();

    /**
     * Temporal de Semántica 1 que ya guarda el resultado de la expresión (una nativa o una
     * expresión con operaciones), o null. Semántica 2 lo usa como Valor Real para que la
     * numeración sea la misma en ambas etapas (como la referencia).
     */
    private String temporalSemantica1(List<Token> toks) {
        if (toks == null || toks.isEmpty()) {
            return null;
        }
        Object[] m = temporalExpresion.get(toks.get(0));
        if (m != null && (Integer) m[0] == toks.size()) {
            return (String) m[1];
        }
        Token t0 = toks.get(0);
        if (FuncionNativa.deToken(t0.getTokenClass()) != null && temporalNativa.containsKey(t0)
                && abarcaLlamada(toks)) {
            return temporalNativa.get(t0);
        }
        return null;
    }
    // Cuádruplas de las nativas de la operación en curso (se escriben antes de la asignación).
    private final StringBuilder cuadruplasNativas = new StringBuilder();

    /**
     * Semántica 1: temporales de las operaciones de una expresión que NO es una asignación
     * (condición, return, argumento de una nativa o de una def). Cada operación binaria deja
     * un temporal del tipo de su resultado, igual que en generarOperaciones. Las llamadas que
     * contiene no se recorren aquí: cada nativa cuenta su propio temporal al validarse y los
     * argumentos de una def se cuentan al cerrar su llamada.
     */
    /** @return el último temporal creado (el resultado de la expresión), o null si no hubo operaciones */
    private String contarTemporalesExpresion(List<Token> toks, int linea) {
        if (toks == null || toks.isEmpty()) {
            return null;
        }
        EvalExpr ev = new EvalExpr(toks);
        ev.temps = new ArrayList<>();
        ev.expr(1);
        String ultimo = null;
        for (Object[] e : ev.temps) {
            if (e[1] != null) {
                continue; // id sin declarar (no es una operación)
            }
            int tipo = (Integer) e[0];
            ultimo = nuevoTemporal(tipo);
            int col = columnaEstadistica(tipo);
            if (col >= 0) {
                estadisticasSemantica.computeIfAbsent(linea, k -> new int[10])[col]++;
            }
        }
        if (ultimo != null) {
            temporalExpresion.put(toks.get(0), new Object[] { toks.size(), ultimo });
        }
        return ultimo;
    }

    /** Tipo (1..10) máximo de la función nativa; Variant si no devuelve valor (void). */
    private static int tipoRetornoNativa(int tokenClass) {
        FuncionNativa f = FuncionNativa.deToken(tokenClass);
        if (f == null || f.retorno == SemanticRuleV3.Tipo.VOID) {
            return LeerCSVComparacion.T_VARIANT;
        }
        return f.retorno.codigo;
    }

    /** Tipo (1..10) que retorna esta llamada: el ya calculado al validarla o, si aún no se validó, el de su p1. */
    private int tipoRetornoNativa(Token llamada, List<Token> interior) {
        Integer t = retornoNativa.get(llamada);
        if (t != null) {
            return t;
        }
        FuncionNativa f = FuncionNativa.deToken(llamada.getTokenClass());
        if (f == null) {
            return LeerCSVComparacion.T_VARIANT;
        }
        List<Token> p1 = (interior == null || interior.isEmpty()) ? null : separarPorComas(interior).get(0);
        SemanticRuleV3.Tipo r = retornoDeLlamada(f, (p1 == null || p1.isEmpty()) ? null : argumentoNativa(p1));
        return r == SemanticRuleV3.Tipo.VOID ? LeerCSVComparacion.T_VARIANT : r.codigo;
    }

    /** Retorno de la llamada según su primer argumento (máximo si es inválido o falta). */
    private static SemanticRuleV3.Tipo retornoDeLlamada(FuncionNativa f, SemanticAnalyzerV3.Argumento p1) {
        if (f.parametros.length == 0 || p1 == null) {
            return f.retorno;
        }
        boolean valido = f.parametros[0].acepta(p1.tipo, p1.esId, p1.declarado);
        return f.retornoPara(p1.tipo, valido);
    }

    /**
     * Semántica 1: cada llamada nativa que devuelve valor produce un temporal de su tipo de
     * retorno (TDec, TExp...), contado en la línea de la llamada.
     */
    private void registrarTemporalNativa(Token llamada, FuncionNativa f, SemanticRuleV3.Tipo tipo,
            List<List<Token>> argumentos) {
        if (tipo == SemanticRuleV3.Tipo.VOID) {
            return;
        }
        // Inicialización del for (for (#Dk = inc(#Dx); ...)): igual que la referencia, no es
        // una operación de Semántica 1 (tampoco cuenta como asignación).
        if (enInicializacionDelFor()) {
            return;
        }
        // Primera llamada dentro de un Console.log: se decide al cerrar el Console.log.
        if (!capsParen.isEmpty()) {
            CapParen c = capsParen.get(capsParen.size() - 1);
            if (c.clase == CAP_CONSOLE && c.par == 1 && !c.toks.isEmpty() && c.toks.get(0) == llamada) {
                pendientesConsole.put(c, new TemporalPendiente(llamada, f, tipo, argumentos));
                return;
            }
        }
        registrarTemporalNativa0(llamada, f, tipo, argumentos);
    }

    private void registrarTemporalNativa0(Token llamada, FuncionNativa f, SemanticRuleV3.Tipo tipo,
            List<List<Token>> argumentos) {
        String tmp = nuevoTemporal(tipo.codigo);
        temporalNativa.put(llamada, tmp);
        int col = columnaEstadistica(tipo.codigo);
        if (col >= 0) {
            estadisticasSemantica.computeIfAbsent(llamada.getLinea(), k -> new int[10])[col]++;
        }
        StringBuilder args = new StringBuilder();
        for (List<Token> a : argumentos) {
            if (args.length() > 0) args.append(' ');
            String v = a.isEmpty() ? "-" : valorArgNativa(a);
            args.append(v != null ? v : textoDe(a).replace(" ", ""));
        }
        String cuadrupla = f.nombre + "," + args + "," + tmp + "\n";
        if (EstadoDeOperacion) {
            cuadruplasNativas.append(cuadrupla); // se escribe junto con la operación que la contiene
        } else {
            logOperaciones.append("Linea ").append(llamada.getLinea()).append(":\n\n")
                    .append(cuadrupla).append("\n");
        }
    }

    private FuncionNativa nativaDe(Token nombre) {
        FuncionNativa f = FuncionNativa.deToken(nombre.getTokenClass());
        return f != null ? f : FuncionNativa.deNombre(nombre.getLexema()); // "char" se identifica como chr
    }

    /** Un parámetro de una llamada nativa que acaba de completarse (en su coma o en el ")"). */
    private void parametroNativa(CapParen c, List<Token> arg) {
        FuncionNativa f = nativaDe(c.origen);
        if (f == null) {
            return;
        }
        SemanticAnalyzerV3.Argumento a;
        if (arg.isEmpty()) {
            a = new SemanticAnalyzerV3.Argumento("-", SemanticRuleV3.Tipo.VARIANT, false, false,
                    c.origen.getLinea());
        } else {
            // Semántica 1: las operaciones del argumento dejan su temporal; ese temporal es
            // el Valor Real del parámetro (#Dx * 2 + #Dy -> TDec114).
            a = argumentoNativa(arg, contarTemporalesExpresion(arg, c.origen.getLinea()));
        }
        int i = c.nativaArgs.size();
        c.nativaArgs.add(a);
        semantica3.validarParametro(f, i, a, c.origen.getLinea(), obtenerAmbitoActual());
    }

    /** ")" de una llamada nativa: último parámetro, parámetros faltantes, salida y temporal. */
    private void finalizarNativa(CapParen c) {
        Token nombre = c.origen;
        FuncionNativa f = nativaDe(nombre);
        if (f == null) {
            return;
        }
        List<Token> ultimo = new ArrayList<>(c.toks.subList(Math.min(c.argIni, c.toks.size()), c.toks.size()));
        if (!(ultimo.isEmpty() && c.nativaArgs.isEmpty())) { // "f()" no tiene argumentos
            parametroNativa(c, ultimo);
        }
        int ambito = obtenerAmbitoActual();
        semantica3.validarFaltantes(f, c.nativaArgs.size(), nombre.getLinea(), ambito);
        SemanticRuleV3.Tipo retorno = retornoDeLlamada(f, c.nativaArgs.isEmpty() ? null : c.nativaArgs.get(0));
        retornoNativa.put(nombre, retorno == SemanticRuleV3.Tipo.VOID
                ? LeerCSVComparacion.T_VARIANT : retorno.codigo);
        semantica3.registrarSalida(f, retorno, nombre.getLinea(), ambito);
        registrarTemporalNativa(nombre, f, retorno,
                c.toks.isEmpty() ? new ArrayList<>() : separarPorComas(c.toks));
    }

    /** validateFunctionCall: aplica las reglas de Semántica 3 a una llamada nativa (CLEAR, sin paréntesis). */
    private void validarFuncionNativa(Token nombre, List<List<Token>> argumentos) {
        FuncionNativa f = FuncionNativa.deToken(nombre.getTokenClass());
        if (f == null) {
            f = FuncionNativa.deNombre(nombre.getLexema()); // "char" se identifica como chr
        }
        if (f == null) {
            return;
        }
        List<SemanticAnalyzerV3.Argumento> args = new ArrayList<>();
        for (List<Token> arg : argumentos) {
            if (arg.isEmpty()) {
                args.add(new SemanticAnalyzerV3.Argumento("-", SemanticRuleV3.Tipo.VARIANT,
                        false, false, nombre.getLinea()));
                continue;
            }
            // Semántica 1: las operaciones del argumento dejan su temporal; ese temporal es
            // el Valor Real del parámetro (#Dx * 2 + #Dy -> TDec114).
            String tmpExpr = contarTemporalesExpresion(arg, nombre.getLinea());
            args.add(argumentoNativa(arg, tmpExpr));
        }
        // Una llamada sin argumentos "f()" llega como una lista con un argumento vacío.
        if (args.size() == 1 && argumentos.get(0).isEmpty()) {
            args.clear();
        }
        SemanticRuleV3.Tipo retorno = retornoDeLlamada(f, args.isEmpty() ? null : args.get(0));
        retornoNativa.put(nombre, retorno == SemanticRuleV3.Tipo.VOID
                ? LeerCSVComparacion.T_VARIANT : retorno.codigo);
        semantica3.validarLlamada(f, args, retorno, nombre.getLinea(), obtenerAmbitoActual());
        registrarTemporalNativa(nombre, f, retorno, argumentos);
    }

    /**
     * Valor Real de un argumento de una nativa: la constante o el id; si es una llamada a
     * def o un acceso a arreglo, su id; si es otra nativa, el temporal que produjo; si es
     * una expresión, el tipo del temporal.
     */
    private String valorArgNativa(List<Token> arg) {
        Token t0 = arg.get(0);
        if (FuncionNativa.deToken(t0.getTokenClass()) != null && temporalNativa.containsKey(t0)
                && abarcaLlamada(arg)) {
            return temporalNativa.get(t0);
        }
        return null;
    }

    /** f( ... ) que abarca todo el argumento. */
    private static boolean abarcaLlamada(List<Token> arg) {
        if (arg.size() < 3 || arg.get(1).getTokenClass() != -49) {
            return arg.size() == 1;
        }
        int prof = 0;
        for (int k = 1; k < arg.size(); k++) {
            int tc = arg.get(k).getTokenClass();
            if (tc == -49) prof++;
            else if (tc == -50) {
                prof--;
                if (prof == 0) return k == arg.size() - 1;
            }
        }
        return false;
    }

    /** Resuelve el Valor Real y el tipo de un argumento de una función nativa. */
    private SemanticAnalyzerV3.Argumento argumentoNativa(List<Token> arg) {
        return argumentoNativa(arg, null);
    }

    /** @param tmpExpr temporal donde quedó el resultado si el argumento es una expresión con operaciones */
    private SemanticAnalyzerV3.Argumento argumentoNativa(List<Token> arg, String tmpExpr) {
        Token t0 = arg.get(0);
        boolean esId = esIdToken(t0.getTokenClass()) && (arg.size() == 1 || esAccesoArregloCompleto(arg));
        int tipo;
        boolean declarado = true;
        if (esId) {
            // Identificador: su tipo base real sale de la tabla de símbolos (no del lexema).
            IdInfo inf = idInfos.get(t0);
            declarado = inf != null && inf.declarado;
            tipo = declarado ? tipoSemanticoDeSimbolo(inf.tipo) : LeerCSVComparacion.T_VARIANT;
        } else {
            tipo = evaluar(arg).tipo;
        }
        boolean constante = arg.size() == 1
                || (arg.size() == 2 && (arg.get(0).getTokenClass() == -11 || arg.get(0).getTokenClass() == -12));
        boolean llamadaDef = esIdToken(t0.getTokenClass()) && abarcaLlamada(arg); // #DUd(1): se muestra el id
        String valorReal = (esId || constante || llamadaDef) ? valorArg(arg)
                : (tmpExpr != null ? tmpExpr : tipoCorto(tipo));
        if (constante && arg.size() == 2) {
            valorReal = textoDe(arg).replace(" ", "");
        }
        String tmpNativa = valorArgNativa(arg); // otra nativa: su temporal
        if (tmpNativa != null) {
            valorReal = tmpNativa;
        } else if (FuncionNativa.deToken(t0.getTokenClass()) != null && abarcaLlamada(arg)) {
            valorReal = t0.getLexema().trim(); // nativa sin valor (copy, val, CLEAR): su nombre
        }
        return new SemanticAnalyzerV3.Argumento(valorReal, SemanticRuleV3.Tipo.deCodigo(tipo),
                esId, declarado, t0.getLinea());
    }

    /** id[..][..] que abarca todo el argumento (elemento de un arreglo). */
    private static boolean esAccesoArregloCompleto(List<Token> arg) {
        if (arg.size() < 2 || arg.get(1).getTokenClass() != -47) {
            return false;
        }
        int prof = 0;
        for (int k = 1; k < arg.size(); k++) {
            int tc = arg.get(k).getTokenClass();
            if (prof == 0 && tc != -47) {
                return false;
            }
            if (tc == -47) prof++;
            else if (tc == -48) prof--;
        }
        return prof == 0;
    }

    // Contador de temporales del muestreo, por tipo, en orden de ejecución (TDec1, TDec2, TBoolean1...).
    private final Map<Integer, Integer> contTempSem = new HashMap<>();
    // Expresión (primer token + tamaño) -> temporal ya asignado, para que 1040 y 1050 (o 1150 y 1160)
    // de la misma expresión muestren el mismo.
    private final Map<Token, Object[]> memoTempSem = new IdentityHashMap<>();
    private final Map<Token, String> tokTempSem = new IdentityHashMap<>(); // id sin declarar -> su temporal

    /**
     * Valor Real de una expresión según las diapositivas: el operando mismo si es un identificador
     * declarado, una constante o un acceso id[..]/id(..) a algo declarado; si la expresión hace
     * operaciones (o usa ids sin declarar) el resultado vive en un temporal tipado.
     */
    private String valorSem(List<Token> toks) {
        if (toks == null || toks.isEmpty()) {
            return "-";
        }
        String t1 = temporalSemantica1(toks);
        if (t1 != null) {
            return t1;
        }
        List<Token> x = sinUnarios(toks);
        if (x.isEmpty()) {
            return "-";
        }
        t1 = temporalSemantica1(x);
        if (t1 != null) {
            return t1;
        }
        Object[] m = memoTempSem.get(x.get(0));
        if (m != null && (Integer) m[0] == x.size()) {
            return (String) m[1];
        }
        EvalExpr ev = new EvalExpr(x);
        ev.temps = new ArrayList<>();
        ev.expr(1);
        String res;
        if (ev.temps.isEmpty()) {
            res = valorArg(x);
        } else {
            String ultimo = null;
            for (Object[] e : ev.temps) {
                int tipo = (Integer) e[0];
                Token tk = (Token) e[1];
                if (tk != null && tokTempSem.containsKey(tk)) { // el mismo id ya se cargó en un temporal
                    ultimo = tokTempSem.get(tk);
                    continue;
                }
                int n = contTempSem.merge(tipo, 1, Integer::sum);
                ultimo = LeerCSVComparacion.prefijoTemporal(tipo) + n;
                if (tk != null) {
                    tokTempSem.put(tk, ultimo);
                }
            }
            res = ultimo;
        }
        memoTempSem.put(x.get(0), new Object[] { x.size(), res });
        return res;
    }

    /** Quita ! / ~ y paréntesis envolventes: !(!(x)) -> x (el Valor Real es el operando de fondo). */
    private static List<Token> sinUnarios(List<Token> toks) {
        List<Token> t = toks;
        boolean cambio = true;
        while (cambio && t.size() > 1) {
            cambio = false;
            int c0 = t.get(0).getTokenClass();
            if ((c0 == -29 || c0 == -3) && t.size() > 1) {
                t = t.subList(1, t.size());
                cambio = true;
            } else if (c0 == -49 && t.get(t.size() - 1).getTokenClass() == -50) {
                int prof = 0;
                boolean envuelve = true;
                for (int k = 0; k < t.size() - 1; k++) {
                    int tc = t.get(k).getTokenClass();
                    if (tc == -49) prof++;
                    else if (tc == -50) prof--;
                    if (prof == 0) { envuelve = false; break; }
                }
                if (envuelve) {
                    t = t.subList(1, t.size() - 1);
                    cambio = true;
                }
            }
        }
        return t;
    }

    /** Valor Real del 1020: el lado derecho (tokens que siguen al operador de la asignación). */
    private String valorRhs(Token op, Operando valor, String porDefecto) {
        if (valor != null && tipoDeTemporal.containsKey(valor.texto)) {
            return valor.texto; // el resultado ya está en un temporal de Semántica 1 (TDec3, TExp1...)
        }
        int ix = -1;
        for (int k = 0; k < infijoToks.size(); k++) {
            if (infijoToks.get(k) == op) {
                ix = k;
                break;
            }
        }
        if (ix >= 0 && ix + 1 < infijoToks.size()) {
            return valorSem(new ArrayList<>(infijoToks.subList(ix + 1, infijoToks.size())));
        }
        return valorAsig(valor, porDefecto);
    }

    /** Valor Real del 1020: el operando; si es una llamada o acceso a arreglo, su identificador. */
    private static String valorAsig(Operando v, String porDefecto) {
        String x = v.vis != null ? v.vis : v.texto;
        if (x == null || x.isEmpty()) {
            return porDefecto;
        }
        int i = x.indexOf('(');
        int j = x.indexOf('[');
        if (i < 0 || (j >= 0 && j < i)) {
            i = j;
        }
        return i > 0 ? x.substring(0, i) : x;
    }

    /** Valor Real de un argumento: el operando mismo; si es un acceso id[..] / id(..) completo, el id. */
    private static String valorArg(List<Token> arg) {
        if (arg == null || arg.isEmpty()) {
            return "-";
        }
        if (arg.size() == 1) {
            return arg.get(0).getLexema().trim();
        }
        int tc1 = arg.get(1).getTokenClass();
        if (esIdToken(arg.get(0).getTokenClass()) && (tc1 == -47 || tc1 == -49)) {
            int prof = 0;
            for (int k = 1; k < arg.size(); k++) {
                int tc = arg.get(k).getTokenClass();
                if (tc == -47 || tc == -49) prof++;
                else if (tc == -48 || tc == -50) {
                    prof--;
                    if (prof == 0 && k != arg.size() - 1) return textoDe(arg);
                }
            }
            return arg.get(0).getLexema().trim();
        }
        return textoDe(arg);
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
            boolean arreglo = llamadaOpAbre == -47;
            Token unico = new Token(id + (arreglo ? "[" : "(") + llamadaOpTexto + (arreglo ? "]" : ")"), callee.getTipo(),
                    callee.getLinea(), callee.getColumna(), callee.getTokenClass());
            int tipo = LeerCSVComparacion.T_VARIANT;
            int amb = buscarAmbitoSimbolo(id);
            FuncInfo f = amb >= 0 ? funciones.get(id + "|" + amb) : null;
            if (arreglo) {
                // el elemento tiene el tipo del arreglo (ya calculado al consumir el id)
                tipo = tiposOperando.getOrDefault(callee, LeerCSVComparacion.T_VARIANT);
            } else if (FuncionNativa.deToken(callee.getTokenClass()) != null) {
                tipo = tipoRetornoNativa(callee, null); // Semántica 3: retorno calculado de esta llamada
                String tmp = temporalNativa.get(callee);
                if (tmp != null) {
                    temporalOperando.put(unico, tmp);
                }
            } else if (f != null && !f.procedimiento) {
                tipo = tipoSemanticoDeSimbolo(f.tipo);
            }
            tiposOperando.put(unico, tipo);
            if (!arreglo && f != null && f.procedimiento) {
                tokensProc.add(unico);
            }
            operandosPila.push(unico);
        }
        llamadaOpCallee = null;
        llamadaOpTexto.setLength(0);
    }

    /** Declaración de función (1120) o procedimiento (1110): la clase quedó bien registrada. */
    private void registrarDeclaracionCallable(Token t, String id, int tc) {
        boolean proc = tc == -107;
        String clase = tabla.obtenerClase(id, AmbitoActual.peek());
        clase = clase == null ? "" : clase.trim();
        boolean ok = proc ? clase.equals("Procedimiento") : clase.equals("Funcion");
        filaConError(t.getLinea(), proc ? 1110 : 1120, proc ? "proc" : "func", ok,
                id + " no quedó registrado como " + (proc ? "procedimiento" : "función")
                        + " en la tabla de símbolos",
                id, t.getColumna());
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

    private int ambitoForzado = -999; // ámbito de una captura que se resuelve después de cerrar su bloque

    private int obtenerAmbitoActual() {
        if (ambitoForzado != -999) {
            return ambitoForzado;
        }
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
    private final Set<Token> tokensProc = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<Token> infijoToks = new ArrayList<>(); // tokens de la operación en curso (para el Valor Real del 1020)
    private Token prevOpTok = null;   // último token alimentado a la operación (para detectar signo unario)
    private Token signoPend = null;   // + / - en posición de operando, a la espera de su constante

    /**
     * Un + / - donde se espera un operando es el signo de la constante que sigue ("-5", "+7"):
     * se pliega en un solo operando (con su tipo) en vez de tratarse como resta/suma binaria,
     * que dejaba la pila de operandos desalineada y arruinaba el destino de la asignación.
     */
    private void procesarTokenDeOperacion(Token t) {
        if (llamadaOpProf > 0) {
            procesarTokenDeOperacion0(t);
            return;
        }
        if (signoPend != null) {
            Token sg = signoPend;
            signoPend = null;
            int c0 = t.getTokenClass();
            if (c0 <= -54 && c0 >= -59) {
                t = new Token(sg.getLexema().trim() + t.getLexema().trim(), t.getTipo(),
                        sg.getLinea(), sg.getColumna(), c0);
            } else {
                procesarTokenDeOperacion0(sg); // no era signo de constante: operador binario normal
            }
        }
        int tcx = t.getTokenClass();
        boolean esperaOperando = prevOpTok == null || prevOpTok.getTokenClass() == -49
                || PRECEDENCIA_OPERADOR.containsKey(prevOpTok.getTokenClass());
        if ((tcx == -11 || tcx == -12) && esperaOperando) {
            signoPend = t;
            return;
        }
        prevOpTok = t;
        procesarTokenDeOperacion0(t);
    }

    private void procesarTokenDeOperacion0(Token t) {

        int tc = t.getTokenClass();

        // El texto original se reconstruye tal cual el compilador va
        // consumiendo los tokens de la operación.
        if (infijoActual.length() > 0) {
            infijoActual.append(" ");
        }
        infijoActual.append(t.getLexema());
        infijoToks.add(t);

        // Avance 3 Sem2: una llamada f(args) se trata como UN solo operando.
        if (llamadaOpProf > 0) {
            if (tc == llamadaOpAbre) {
                llamadaOpProf++;
            } else if (tc == llamadaOpCierra) {
                llamadaOpProf--;
            }
            if (llamadaOpProf == 0) {
                cerrarLlamadaOperando();
            } else {
                llamadaOpAcumular(t.getLexema().trim());
            }
            return;
        }
        // Semántica 3: una función nativa (pow(...), CLEAR...) es UN operando cuyo tipo es su retorno.
        if (FuncionNativa.deToken(tc) != null) {
            tiposOperando.put(t, tipoRetornoNativa(t, null));
            operandosPila.push(t);
            if (tc != -75) { // CLEAR no lleva paréntesis
                llamadaOpPendiente = true;
                llamadaOpAbre = -49;
                llamadaOpCierra = -50;
            }
            return;
        }
        if (llamadaOpPendiente && tc == llamadaOpAbre) {
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
        infijoToks.clear();
        tiposOperando.clear();
        temporalOperando.clear();
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
        boolean initFor = enInicializacionDelFor();
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
                // ! da Booleano; ~ conserva el tipo entero de su operando (si no, Variant). Así la
                // asignación posterior puede verificar su compatibilidad (Cadena <- !x es error).
                int tipoU = tc == -29 ? LeerCSVComparacion.T_BOOLEAN
                        : (esEntero(o.tipo) ? o.tipo : LeerCSVComparacion.T_VARIANT);
                String tmp = nuevoTemporal(tipoU);
                bloque.append(lex).append(",").append(o.texto).append(",").append(tmp).append("\n");
                Operando ou = new Operando(tmp, tipoU);
                ou.vis = o.vis != null ? o.vis : o.texto; // el muestreo muestra el operando, no el temporal
                pila.push(ou);
            }
            // asignación: no genera temporal
            else if (esAsignacion(tc)) {
                Operando valor = sacar(pila);
                Operando destino = sacar(pila);
                bloque.append(lex).append(",").append(destino.texto).append(",")
                        .append(valor.texto).append("\n");

                // La inicialización del encabezado de un for (for (#Di = 0; ...)) no se cuenta
                // como asignación de Semántica 1; su compatibilidad de tipos sí se verifica.
                if (!initFor) {
                    stats[9]++; // asignación (no genera temporal)
                    // Formato exacto de asignación: #DX -> TBoolean3
                    String formatoAsig = destino.texto + " -> " + valor.texto;
                    asignacionesDetalle.computeIfAbsent(linea, k -> new ArrayList<>()).add(formatoAsig);
                }

                // Compatibilidad destino <- valor con los tipos de los propios operandos
                // (destino.tipo ya se calculó con el ámbito real del identificador).
                verificarAsignacion(t, destino, valor, linea);
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
                String tmpNat = temporalOperando.get(t); // llamada nativa: su resultado ya está en un temporal
                Operando opd = new Operando(tmpNat != null ? tmpNat : lex, tipo);
                if (tmpNat != null) {
                    opd.vis = lex;
                }
                IdInfo inf = idInfos.get(t);
                opd.proc = tokensProc.contains(t) || (inf != null && "Procedimiento".equals(inf.clase));
                pila.push(opd);
            }
        }

        estadisticaLineaActual = null;

        if (cuadruplasNativas.length() > 0) { // las nativas de la operación se calculan antes que ella
            bloque.insert(0, cuadruplasNativas);
            cuadruplasNativas.setLength(0);
        }
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