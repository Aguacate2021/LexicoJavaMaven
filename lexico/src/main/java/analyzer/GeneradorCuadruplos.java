package analyzer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import analyzer.Cuadruplo.Etiqueta;
import analyzer.TablasComparacion.LeerCSVComparacion;

/**
 * Etapa final: generación de cuádruplos (simulación de ensamblador).
 *
 * Recorre los tokens ya validados por el léxico, el sintáctico y las semánticas
 * siguiendo la MISMA gramática LL del proyecto (Producciones / ExcelPuro2.csv)
 * y traduce cada construcción a renglones E | A | V1 | V2 | R.
 *
 * Reglas que implementa:
 *  - Fail-fast: con cualquier error (léxico, sintáctico, ámbito, semántica 1/2/3)
 *    solo se generan los cuádruplos de las líneas ANTERIORES a la del primer error
 *    y ahí se detiene. Las estructuras que quedaron abiertas no se cierran.
 *  - Mnemónicos fijos: =, operadores tal cual (<, +, &&...), JF, JT, JMP, CALL,
 *    return, ARR, Metodo / fin Metodo. Nada de "asig" ni "J false".
 *  - Asignación compuesta (+=, -=, *=, /=, %=): primero la operación hacia un
 *    temporal y luego "=" hacia la variable destino.
 *  - Temporales con secuencia única por tipo (TDec1, TDec2, TBoolean1...).
 *  - Un solo temporal de retorno por función: "TD" + nombre (TD#Dsuma), lo usan
 *    todos sus return y todas las llamadas (CALL). Procedimientos: void.
 *  - Pila exclusiva de estructuras: if, while, do, for, foreach, switch, ternario
 *    y métodos se apilan al abrir y se desapilan al cerrar (LIFO estricto).
 *  - Etiquetas por estructura (if-E, Wil-E, Do-E, For-E, Sw-E, Ter-E), irrepetibles
 *    en todo el programa, numeradas sin huecos en el orden en que aparecen.
 */
public class GeneradorCuadruplos {

    // =========================================================
    // CÓDIGOS DE TOKEN (los del Lexer / Producciones)
    // =========================================================
    private static final int T_MAS_MAS = -1, T_MENOS_MENOS = -2, T_NOT_BIT = -3;
    private static final int T_COMA = -7, T_PUNTO_COMA = -9, T_DOS_PUNTOS = -10;
    private static final int T_MAS = -11, T_MENOS = -12, T_POTENCIA = -16;
    private static final int T_TERNARIO = -32, T_ASIGNA = -33;
    private static final int T_LLAVE_ABRE = -45, T_LLAVE_CIERRA = -46;
    private static final int T_CORCHETE_ABRE = -47, T_CORCHETE_CIERRA = -48;
    private static final int T_PAR_ABRE = -49, T_PAR_CIERRA = -50;
    private static final int T_NOT = -29;
    private static final int T_MAIN = -68, T_REG = -69, T_VAR = -70, T_DEF = -71;
    private static final int T_CONSOLE_READ = -93, T_CONSOLE_LOG = -94;
    private static final int T_WHILE = -95, T_DO = -96, T_RETURN = -97, T_FOR = -98, T_SWITCH = -99;
    private static final int T_ELSEIF = -100, T_ELSE = -101, T_CASE = -102, T_DEFAULT = -103;
    private static final int T_BREAK = -104, T_IF = -105;
    private static final int T_ID_PROCEDIMIENTO = -107;
    private static final int T_FIN = Integer.MIN_VALUE; // centinela de fin de tokens

    /** Prefijos de etiqueta por estructura. */
    private static final String ET_IF = "if", ET_WHILE = "Whi", ET_DO = "do", ET_FOR = "For",
            ET_SWITCH = "Switch", ET_TERNARIO = "Ter";

    /** Valor que se compara cuando la condición es una variable o constante sola. */
    private static final String VERDADERO = "true";
    private static final String VOID = "void";

    // =========================================================
    // RESULTADO
    // =========================================================
    private final List<Cuadruplo> cuadruplos = new ArrayList<>();
    private final ContadorCuadruplos contador = new ContadorCuadruplos();
    private int lineaDetencion = 0;        // 0 = se generó todo
    private String motivoDetencion = "";
    private final List<String> estructurasAbiertas = new ArrayList<>();

    // =========================================================
    // ESTADO DE LA GENERACIÓN
    // =========================================================
    private List<Token> toks = Collections.emptyList();
    private int pos = 0;
    private int lineaActual = 0;
    private final Map<Integer, Integer> contadorTemporales = new HashMap<>();
    /** nombre de la función -> su único temporal de retorno (TD#Dsuma). */
    private final Map<String, String> temporalDeFuncion = new LinkedHashMap<>();
    /** Pila exclusiva de estructuras de control (cierre LIFO). */
    private final Deque<Estructura> pilaEstructuras = new ArrayDeque<>();
    /** Destino de los return del método en curso: TD de la función o void. */
    private final Deque<String> pilaRetorno = new ArrayDeque<>();
    /** Ámbitos abiertos: 0 = global; def y for abren uno nuevo (marcadores 802/803). */
    private final Deque<Integer> pilaAmbitos = new ArrayDeque<>();
    private int contadorAmbitos = 0;
    private final LeerCSVComparacion comparacion = new LeerCSVComparacion();

    /** Elemento de la pila de estructuras. */
    private static final class Estructura {
        final String tipo;
        final String detalle;
        final int linea;

        Estructura(String tipo, String detalle, int linea) {
            this.tipo = tipo;
            this.detalle = detalle;
            this.linea = linea;
        }

        @Override
        public String toString() {
            return tipo + (detalle.isEmpty() ? "" : " " + detalle) + " (línea " + linea + ")";
        }
    }

    /** Qué produjo un valor: decide cómo se arma la condición de un JF. */
    private enum Clase { VARIABLE, CONSTANTE, OPERACION, LLAMADA }

    /** Valor de una expresión: texto que va en V1/V2/R, su tipo (1..10) y su clase. */
    private static final class Operando {
        final String texto;
        final int tipo;
        final Clase clase;

        Operando(String texto, int tipo, Clase clase) {
            this.texto = texto;
            this.tipo = tipo;
            this.clase = clase;
        }
    }

    /** Se lanza para detener la generación (fin del código válido o construcción inesperada). */
    private static final class Detencion extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final int linea;

        Detencion(String mensaje, int linea) {
            super(mensaje, null, false, false);
            this.linea = linea;
        }
    }

    // =========================================================
    // API
    // =========================================================

    /**
     * Genera los cuádruplos.
     *
     * @param tokens  tokens del programa (los comentarios se ignoran)
     * @param errores TODOS los errores de la compilación (léxicos, sintácticos,
     *                ámbito y semántica 1, 2 y 3). Si hay alguno, la generación
     *                llega solo hasta la línea anterior al primero.
     */
    public void generar(List<Token> tokens, List<ErrorEntry> errores) {
        reiniciar();

        ErrorEntry primero = null;
        if (errores != null) {
            for (ErrorEntry e : errores) {
                if (primero == null || e.getLinea() < primero.getLinea()) {
                    primero = e;
                }
            }
        }
        int limite = primero == null ? Integer.MAX_VALUE : primero.getLinea();

        List<Token> validos = new ArrayList<>();
        if (tokens != null) {
            for (Token t : tokens) {
                int tc = t.getTokenClass();
                if (tc == -51 || tc == -52) {
                    continue; // comentarios
                }
                if (t.getLinea() >= limite) {
                    break; // fail-fast: nada de la línea del error en adelante
                }
                validos.add(t);
            }
        }
        toks = validos;

        try {
            programa("main", VOID, true);
            if (pos < toks.size()) {
                Token t = toks.get(pos);
                throw new Detencion("Código después del cierre de main: '" + lex(t) + "'", t.getLinea());
            }
        } catch (Detencion d) {
            if (primero == null) {
                lineaDetencion = d.linea;
                motivoDetencion = d.getMessage();
            }
        }
        if (primero != null) {
            lineaDetencion = primero.getLinea();
            motivoDetencion = "Primer error en la línea " + primero.getLinea() + " ["
                    + primero.getCodigo() + "] " + primero.getDescripcion();
        }
        if (lineaDetencion != 0) {
            // de afuera hacia adentro, como quedaron en la pila
            Iterator<Estructura> it = pilaEstructuras.descendingIterator();
            while (it.hasNext()) {
                estructurasAbiertas.add(it.next().toString());
            }
        }
        nombrarEtiquetas();
        for (Cuadruplo c : cuadruplos) {
            contador.cuadruplo(c);
        }
    }

    public List<Cuadruplo> getCuadruplos() {
        return Collections.unmodifiableList(cuadruplos);
    }

    public ContadorCuadruplos getContador() {
        return contador;
    }

    /** true si la generación no llegó al cierre de main (por un error o algo inesperado). */
    public boolean seDetuvo() {
        return lineaDetencion != 0;
    }

    public int getLineaDetencion() {
        return lineaDetencion;
    }

    public String getMotivoDetencion() {
        return motivoDetencion;
    }

    /** Estructuras que quedaron abiertas al detenerse (de afuera hacia adentro). */
    public List<String> getEstructurasAbiertas() {
        return Collections.unmodifiableList(estructurasAbiertas);
    }

    /** Aviso único para la lista de errores de la IDE (null si no se detuvo). */
    public ErrorEntry avisoDetencion() {
        if (!seDetuvo()) {
            return null;
        }
        return new ErrorEntry("Cuádruplos",
                "Generación de cuádruplos detenida en la línea " + lineaDetencion + ". " + motivoDetencion,
                lineaDetencion, "cuadruplos", ErrorEntry.Tipo.SEMANTICA, "-",
                ErrorEntry.AMBITO_DESCONOCIDO);
    }

    /** Texto plano de la tabla (para depurar o guardar como .txt). */
    public String comoTexto() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-10s| %-12s| %-14s| %-14s| %s%n", "E", "A", "V1", "V2", "R"));
        for (Cuadruplo c : cuadruplos) {
            sb.append(c).append(System.lineSeparator());
        }
        if (seDetuvo()) {
            sb.append(System.lineSeparator()).append("Detenido en la línea ").append(lineaDetencion)
                    .append(": ").append(motivoDetencion).append(System.lineSeparator());
        }
        return sb.toString();
    }

    private void reiniciar() {
        cuadruplos.clear();
        contador.reiniciar();
        lineaDetencion = 0;
        motivoDetencion = "";
        estructurasAbiertas.clear();
        toks = Collections.emptyList();
        pos = 0;
        lineaActual = 0;
        contadorTemporales.clear();
        temporalDeFuncion.clear();
        pilaEstructuras.clear();
        pilaRetorno.clear();
        reservadas.clear();
        pilaAmbitos.clear();
        pilaAmbitos.push(0);
        contadorAmbitos = 0;
        contador.ambito(0);
    }

    // =========================================================
    // PROGRAMA, DECLARACIONES Y MÉTODOS
    // =========================================================

    /**
     * PROGRAMA -> Y1 main ( ) { STATU Y2 }   (producción 1).
     * El cuerpo de un def también es un PROGRAMA (producción 7): su main es el
     * método de la función y se abre/cierra con el nombre de la función.
     */
    private void programa(String nombreMetodo, String destinoReturn, boolean principal) {
        declaraciones();
        Token m = esperar(T_MAIN, "main");
        esperar(T_PAR_ABRE, "(");
        esperar(T_PAR_CIERRA, ")");
        Estructura met = abrir("Metodo", nombreMetodo, m.getLinea());
        emitir("Metodo", nombreMetodo, "", "");
        pilaRetorno.push(destinoReturn);
        esperar(T_LLAVE_ABRE, "{");
        sentencias();
        esperar(T_LLAVE_CIERRA, "}");
        pilaRetorno.pop();
        emitir("FINMET", nombreMetodo, "", "");
        cerrar(met);
    }

    /** Y1: reg / var / def / constantes. Las declaraciones no generan cuádruplos. */
    private void declaraciones() {
        while (true) {
            int tc = tcActual();
            if (tc == T_REG) {
                // reg id { id , id ... }
                avanzar();
                avanzar();
                esperar(T_LLAVE_ABRE, "{");
                saltarHasta(T_LLAVE_CIERRA);
                avanzar();
            } else if (tc == T_VAR) {
                saltarHasta(T_PUNTO_COMA);
                avanzar();
            } else if (tc == T_DEF) {
                funcion();
            } else if (esId(tc)) {
                // constantes: id = const , id = const ;
                saltarHasta(T_PUNTO_COMA);
                avanzar();
            } else {
                return;
            }
        }
    }

    /** def id ( parámetros ) PROGRAMA ;   (producción 7) */
    private void funcion() {
        avanzar(); // def
        Token nombre = avanzar();
        if (!esId(nombre.getTokenClass())) {
            throw inesperado(nombre);
        }
        String id = lex(nombre);
        abrirAmbito(); // 802: después del nombre de la función
        contador.definicion(ambitoActual());
        String destino = VOID;
        if (nombre.getTokenClass() != T_ID_PROCEDIMIENTO) {
            // Un único temporal de retorno por función; cuenta 1 en la columna de su tipo.
            destino = temporalDeFuncion.computeIfAbsent(id, k -> {
                contador.temporal(ambitoActual(), LeerCSVComparacion.tipoDeToken(nombre.getTokenClass()));
                return "TD" + k;
            });
        }
        esperar(T_PAR_ABRE, "(");
        saltarHasta(T_PAR_CIERRA); // los parámetros ya se verificaron en semántica
        avanzar();
        programa(id, destino, false);
        cerrarAmbito(); // 803
        esperar(T_PUNTO_COMA, ";");
    }

    // =========================================================
    // SENTENCIAS
    // =========================================================

    /** STATU { ; STATU } */
    private void sentencias() {
        sentencia();
        while (tcActual() == T_PUNTO_COMA) {
            avanzar();
            sentencia();
        }
    }

    /** STATU (producciones 84-94 y 88 = expresión). Puede ser vacía. */
    private void sentencia() {
        int tc = tcActual();
        switch (tc) {
            case T_IF -> sentenciaIf();
            case T_LLAVE_ABRE -> {
                avanzar();
                sentencias();
                esperar(T_LLAVE_CIERRA, "}");
            }
            case T_WHILE -> sentenciaWhile();
            case T_DO -> sentenciaDo();
            case T_RETURN -> sentenciaReturn();
            case T_FOR -> sentenciaFor();
            case T_SWITCH -> sentenciaSwitch();
            case T_CONSOLE_READ, T_CONSOLE_LOG -> sentenciaConsole();
            default -> {
                if (iniciaExpresion(tc)) {
                    expresion();
                }
                // si no, STATU -> ε
            }
        }
    }

    /**
     * if ( c ) S { elseif ( c ) S } [ else S ]
     *
     * El if no tiene etiqueta de entrada, solo de salida. Antes de cada elseif /
     * else va el JMP obligatorio a la salida. El JF del último elseif sin else
     * (o del if solo) brinca directo a la salida: su etiqueta se une con ella.
     */
    private void sentenciaIf() {
        Token t = avanzar();
        Estructura est = abrir("if", "", t.getLinea());
        Etiqueta salida = new Etiqueta(ET_IF);

        Etiqueta siguiente = condicionConSalto(salida);
        sentencia();
        while (true) {
            int tc = tcActual();
            if (tc == T_FIN) {
                throw finDeCodigo(); // no se sabe si seguía un elseif/else: no se cierra
            }
            if (tc == T_ELSEIF) {
                saltoIncondicional(salida);
                ponerEtiqueta(siguiente);
                avanzar();
                siguiente = condicionConSalto(salida);
                sentencia();
            } else if (tc == T_ELSE) {
                saltoIncondicional(salida);
                ponerEtiqueta(siguiente);
                avanzar();
                siguiente = null;
                sentencia();
                break;
            } else {
                break;
            }
        }
        if (siguiente != null) {
            siguiente.unirCon(salida); // último JF -> salida directa (sin etiqueta extra)
        }
        ponerEtiqueta(salida);
        cerrar(est);
    }

    /** ( c ) y su JF hacia una etiqueta nueva (que luego puede unirse a la salida). */
    private Etiqueta condicionConSalto(Etiqueta salida) {
        esperar(T_PAR_ABRE, "(");
        Operando c = expresion();
        esperar(T_PAR_CIERRA, ")");
        Etiqueta destino = new Etiqueta(salida.prefijo);
        saltoFalso(c, destino);
        return destino;
    }

    /** Wil-E1 | cond | JF -> Wil-E2 | cuerpo | JMP Wil-E1 | Wil-E2 */
    private void sentenciaWhile() {
        Token t = avanzar();
        Estructura est = abrir("while", "", t.getLinea());
        Etiqueta inicio = new Etiqueta(ET_WHILE);
        Etiqueta fin = new Etiqueta(ET_WHILE);
        ponerEtiqueta(inicio);
        esperar(T_PAR_ABRE, "(");
        Operando c = expresion();
        esperar(T_PAR_CIERRA, ")");
        saltoFalso(c, fin);
        sentencia();
        saltoIncondicional(inicio);
        ponerEtiqueta(fin);
        cerrar(est);
    }

    /** Do-E1 | cuerpo | cond | JT -> Do-E1   (do S while ( c )) */
    private void sentenciaDo() {
        Token t = avanzar();
        Estructura est = abrir("do while", "", t.getLinea());
        Etiqueta inicio = new Etiqueta(ET_DO);
        ponerEtiqueta(inicio);
        sentencia();
        esperar(T_WHILE, "while");
        esperar(T_PAR_ABRE, "(");
        Operando c = expresion();
        esperar(T_PAR_CIERRA, ")");
        String v = valorCondicion(c);
        agregar(Cuadruplo.salto("JT", v, inicio, lineaActual));
        cerrar(est);
    }

    /** return v  ->  CALL | return  /  | v | | TD<función> (o void). */
    private void sentenciaReturn() {
        avanzar();
        Operando v = expresion();
        String destino = pilaRetorno.isEmpty() ? VOID : pilaRetorno.peek();
        llamada("return", List.of(v.texto), destino);
    }

    /**
     * for ( ini ; cond ; inc ) S   o   for ( x : arreglo ) S   (producciones 93, 103, 104).
     */
    private void sentenciaFor() {
        Token t = avanzar();
        abrirAmbito(); // 802: el for abre ámbito
        esperar(T_PAR_ABRE, "(");
        Estructura est = abrir("for", "", t.getLinea());
        Operando primero = expresion();
        while (tcActual() == T_COMA) {
            avanzar();
            expresion();
        }
        if (tcActual() == T_DOS_PUNTOS) {
            avanzar();
            foreach(primero);
        } else {
            esperar(T_PUNTO_COMA, ";");
            forClasico();
        }
        cerrar(est);
        cerrarAmbito(); // 803
    }

    /**
     * For-E1 | cond | JF -> For-E2 | JMP For-E3 | For-E4 | incremento | JMP For-E1 |
     * For-E3 | cuerpo | JMP For-E4 | For-E2
     */
    private void forClasico() {
        Etiqueta cond = new Etiqueta(ET_FOR);
        Etiqueta fin = new Etiqueta(ET_FOR);
        Etiqueta cuerpo = new Etiqueta(ET_FOR);
        Etiqueta inc = new Etiqueta(ET_FOR);
        ponerEtiqueta(cond);
        if (tcActual() != T_PUNTO_COMA) {
            Operando c = expresion();
            saltoFalso(c, fin);
        }
        esperar(T_PUNTO_COMA, ";");
        saltoIncondicional(cuerpo);
        ponerEtiqueta(inc);
        expresion();
        while (tcActual() == T_COMA) {
            avanzar();
            expresion();
        }
        esperar(T_PAR_CIERRA, ")");
        saltoIncondicional(cond);
        ponerEtiqueta(cuerpo);
        sentencia();
        saltoIncondicional(inc);
        ponerEtiqueta(fin);
    }

    /**
     * foreach: el contador es un temporal TDec, CALL len da el tamaño y el elemento
     * se asigna a la variable con  = | x | | temporal.
     */
    private void foreach(Operando variable) {
        Operando arreglo = expresion();
        esperar(T_PAR_CIERRA, ")");

        String i = nuevoTemporal(LeerCSVComparacion.T_DEC);
        emitir("=", i, "", "0");

        Etiqueta cond = new Etiqueta(ET_FOR);
        Etiqueta fin = new Etiqueta(ET_FOR);
        Etiqueta cuerpo = new Etiqueta(ET_FOR);
        Etiqueta inc = new Etiqueta(ET_FOR);
        ponerEtiqueta(cond);
        String tam = nuevoTemporal(LeerCSVComparacion.T_DEC);
        llamada("len", List.of(arreglo.texto), tam);
        String b = nuevoTemporal(LeerCSVComparacion.T_BOOLEAN);
        emitir("<", i, tam, b);
        agregar(Cuadruplo.salto("JF", b, fin, lineaActual));
        saltoIncondicional(cuerpo);
        ponerEtiqueta(inc);
        emitir("inc", i, "", "1");
        saltoIncondicional(cond);
        ponerEtiqueta(cuerpo);
        String elemento = nuevoTemporal(arreglo.tipo);
        llamada("ARR", arreglo.texto, List.of(i), elemento);
        emitir("=", variable.texto, "", elemento);
        sentencia();
        saltoIncondicional(inc);
        ponerEtiqueta(fin);
    }

    /**
     * switch ( s ) { case v : S... break  ...  [default : S] }
     * Cada case: == | s | v | TBoolean, JF a la siguiente; el break es JMP a la
     * salida. El último case sin default brinca directo a la salida.
     */
    private void sentenciaSwitch() {
        Token t = avanzar();
        Estructura est = abrir("switch", "", t.getLinea());
        esperar(T_PAR_ABRE, "(");
        Operando selector = expresion();
        esperar(T_PAR_CIERRA, ")");
        esperar(T_LLAVE_ABRE, "{");
        Etiqueta salida = new Etiqueta(ET_SWITCH);
        reservarNombre(salida); // la salida del switch toma el primer número
        while (true) {
            esperar(T_CASE, "case");
            Operando v = expresion();
            esperar(T_DOS_PUNTOS, ":");
            String b = nuevoTemporal(LeerCSVComparacion.T_BOOLEAN);
            emitir("==", selector.texto, v.texto, b);
            Etiqueta siguiente = new Etiqueta(ET_SWITCH);
            agregar(Cuadruplo.salto("JF", b, siguiente, lineaActual));
            sentencias();
            esperar(T_BREAK, "break");
            int tc = tcActual();
            if (tc == T_FIN) {
                throw finDeCodigo();
            }
            if (tc == T_CASE || tc == T_DEFAULT) {
                saltoIncondicional(salida); // break
                ponerEtiqueta(siguiente);
            } else {
                siguiente.unirCon(salida); // último case sin default: directo a la salida
            }
            if (tc == T_DEFAULT) {
                avanzar();
                esperar(T_DOS_PUNTOS, ":");
                sentencias();
                break;
            }
            if (tc != T_CASE) {
                break;
            }
        }
        esperar(T_LLAVE_CIERRA, "}");
        ponerEtiqueta(salida);
        cerrar(est);
    }

    /** Console.read ( a , b ... )  /  Console.log ( a )  -> CALL con resultado void. */
    private void sentenciaConsole() {
        Token f = avanzar();
        esperar(T_PAR_ABRE, "(");
        List<String> args = argumentos(T_PAR_CIERRA);
        esperar(T_PAR_CIERRA, ")");
        llamada(lex(f), args, VOID);
    }

    // =========================================================
    // CONDICIONES Y SALTOS
    // =========================================================

    /** JF hacia {@code destino} con la condición ya normalizada. */
    private void saltoFalso(Operando c, Etiqueta destino) {
        String v = valorCondicion(c);
        agregar(Cuadruplo.salto("JF", v, destino, lineaActual));
    }

    /**
     * Variable o constante sola: == | x | true | TBoolean.
     * Llamada a función: su resultado va directo al JF.
     * Operación: su temporal va directo al JF.
     */
    private String valorCondicion(Operando c) {
        if (c.clase == Clase.VARIABLE || c.clase == Clase.CONSTANTE) {
            String b = nuevoTemporal(LeerCSVComparacion.T_BOOLEAN);
            emitir("==", c.texto, VERDADERO, b);
            return b;
        }
        return c.texto;
    }

    private void saltoIncondicional(Etiqueta destino) {
        agregar(Cuadruplo.salto("JMP", "", destino, lineaActual));
    }

    private void ponerEtiqueta(Etiqueta e) {
        agregar(Cuadruplo.etiqueta(e, lineaActual));
    }

    // =========================================================
    // EXPRESIONES (misma precedencia que la gramática)
    // =========================================================

    /** OR -> AND { (|| | |) AND } */
    private Operando expresion() {
        Operando a = and();
        while (tcActual() == -31 || tcActual() == -4) {
            Token op = avanzar();
            a = binaria(op, a, and());
        }
        return a;
    }

    /** AND -> EXP_PAS { (&& | &) EXP_PAS } */
    private Operando and() {
        Operando a = relacional();
        while (tcActual() == -30 || tcActual() == -5) {
            Token op = avanzar();
            a = binaria(op, a, relacional());
        }
        return a;
    }

    /** EXP_PAS -> SimpleExp { (< > <= >= == != === !==) SimpleExp } */
    private Operando relacional() {
        Operando a = simple();
        while (tcActual() <= -20 && tcActual() >= -28) {
            Token op = avanzar();
            a = binaria(op, a, simple());
        }
        return a;
    }

    /** SimpleExpPascal -> TERMINO { (+ - << >> >>>) TERMINO } */
    private Operando simple() {
        Operando a = termino();
        while (tcActual() == T_MAS || tcActual() == T_MENOS
                || (tcActual() <= -17 && tcActual() >= -19)) {
            Token op = avanzar();
            a = binaria(op, a, termino());
        }
        return a;
    }

    /** TERMINO -> ELEVACION { (* / # %) ELEVACION } */
    private Operando termino() {
        Operando a = elevacion();
        while (tcActual() == -13 || tcActual() == -14 || tcActual() == -15 || tcActual() == -106) {
            Token op = avanzar();
            a = binaria(op, a, elevacion());
        }
        return a;
    }

    /** ELEVACION -> FACTOR { (^ **) FACTOR } */
    private Operando elevacion() {
        Operando a = factor();
        while (tcActual() == -6 || tcActual() == T_POTENCIA) {
            Token op = avanzar();
            a = binaria(op, a, factor());
        }
        return a;
    }

    /** FACTOR (producciones 43-50). */
    private Operando factor() {
        Token t = actual();
        int tc = t.getTokenClass();
        if (tc == T_FIN) {
            throw finDeCodigo();
        }
        if (esId(tc)) {
            avanzar();
            return factorId(t);
        }
        if (esConstante(tc)) {
            avanzar();
            return new Operando(lex(t), LeerCSVComparacion.tipoDeToken(tc), Clase.CONSTANTE);
        }
        if ((tc == T_MAS || tc == T_MENOS) && esConstante(tcSiguiente())) {
            // constante con signo: -5, +7
            avanzar();
            Token c = avanzar();
            String signo = tc == T_MENOS ? "-" : "";
            return new Operando(signo + lex(c), LeerCSVComparacion.tipoDeToken(c.getTokenClass()),
                    Clase.CONSTANTE);
        }
        if (tc == T_PAR_ABRE) {
            avanzar();
            Operando o = expresion();
            esperar(T_PAR_CIERRA, ")");
            return o;
        }
        if (tc == T_MAS_MAS || tc == T_MENOS_MENOS) {
            // ++id / --id : se incrementa la variable y el valor es la variable
            Token op = avanzar();
            Token id = avanzar();
            if (!esId(id.getTokenClass())) {
                throw inesperado(id);
            }
            Operando v = factorId(id);
            emitir(tc == T_MAS_MAS ? "inc" : "dec", v.texto, "", "1");
            return new Operando(v.texto, v.tipo, Clase.VARIABLE);
        }
        if (tc == T_NOT || tc == T_NOT_BIT) {
            // ! ( OR )  /  ~ ( OR )
            Token op = avanzar();
            esperar(T_PAR_ABRE, "(");
            Operando o = expresion();
            esperar(T_PAR_CIERRA, ")");
            int tipo = tc == T_NOT ? LeerCSVComparacion.T_BOOLEAN
                    : (esEntero(o.tipo) ? o.tipo : LeerCSVComparacion.T_VARIANT);
            String r = nuevoTemporal(tipo);
            emitir(lex(op), o.texto, "", r);
            return new Operando(r, tipo, Clase.OPERACION);
        }
        if (FuncionNativa.deToken(tc) != null) {
            return llamadaNativa();
        }
        throw inesperado(t);
    }

    /** id X1: arreglo, llamada, asignación o variable sola. */
    private Operando factorId(Token id) {
        String nombre = lex(id);
        int tipo = LeerCSVComparacion.tipoDeToken(id.getTokenClass());
        int tc = tcActual();

        if (tc == T_PAR_ABRE) { // llamada a función / procedimiento del usuario
            avanzar();
            List<String> args = argumentos(T_PAR_CIERRA);
            esperar(T_PAR_CIERRA, ")");
            if (id.getTokenClass() == T_ID_PROCEDIMIENTO) {
                llamada(nombre, args, VOID);
                return new Operando(VOID, LeerCSVComparacion.T_VARIANT, Clase.LLAMADA);
            }
            String td = temporalDeFuncion.computeIfAbsent(nombre, k -> "TD" + k);
            llamada(nombre, args, td);
            return new Operando(td, tipo, Clase.LLAMADA);
        }

        Operando destino = new Operando(nombre, tipo, Clase.VARIABLE);
        if (tc == T_CORCHETE_ABRE) { // id [ i , j ]
            avanzar();
            List<String> indices = argumentos(T_CORCHETE_CIERRA);
            esperar(T_CORCHETE_CIERRA, "]");
            String r = nuevoTemporal(tipo);
            llamada("ARR", nombre, indices, r);
            destino = new Operando(r, tipo, Clase.VARIABLE);
            if (!esAsignacion(tcActual())) {
                return new Operando(r, tipo, Clase.LLAMADA); // valor ya en temporal: directo al JF
            }
        }
        if (esAsignacion(tcActual())) {
            return asignacion(destino);
        }
        return destino;
    }

    /**
     * =        ->  = | destino | | valor
     * += -= ...->  op | destino | valor | temporal   y luego   = | destino | | temporal
     */
    private Operando asignacion(Operando destino) {
        Token op = avanzar();
        Operando valor = expresion();
        if (tcActual() == T_TERNARIO) {
            valor = ternario(valor);
        }
        if (op.getTokenClass() == T_ASIGNA) {
            emitir("=", destino.texto, "", valor.texto);
        } else {
            String simbolo = lex(op);
            simbolo = simbolo.substring(0, simbolo.length() - 1); // "+=" -> "+"
            int tipo = tipoResultado(operadorDeCompuesta(op.getTokenClass()), destino.tipo, valor.tipo);
            String r = nuevoTemporal(tipo);
            emitir(simbolo, destino.texto, valor.texto, r);
            emitir("=", destino.texto, "", r);
        }
        return new Operando(destino.texto, destino.tipo, Clase.VARIABLE);
    }

    /** c ? a : b  ->  JF c Ter-E1 | = t a | JMP Ter-E2 | Ter-E1 | = t b | Ter-E2 */
    private Operando ternario(Operando c) {
        avanzar(); // ?
        Estructura est = abrir("ternario", "", lineaActual);
        Etiqueta falso = new Etiqueta(ET_TERNARIO);
        Etiqueta fin = new Etiqueta(ET_TERNARIO);
        saltoFalso(c, falso);
        Operando a = expresion();
        String r = nuevoTemporal(a.tipo);
        emitir("=", r, "", a.texto);
        saltoIncondicional(fin);
        ponerEtiqueta(falso);
        esperar(T_DOS_PUNTOS, ":");
        Operando b = expresion();
        emitir("=", r, "", b.texto);
        ponerEtiqueta(fin);
        cerrar(est);
        return new Operando(r, a.tipo, Clase.OPERACION);
    }

    /** Función nativa (producciones 63-80): CALL | nombre, argumentos y temporal del tipo que regresa. */
    private Operando llamadaNativa() {
        Token f = avanzar();
        FuncionNativa fn = FuncionNativa.deToken(f.getTokenClass());
        List<Operando> args = new ArrayList<>();
        if (tcActual() == T_PAR_ABRE) { // CLEAR va sin paréntesis
            avanzar();
            if (tcActual() != T_PAR_CIERRA) {
                args.add(expresion());
                while (tcActual() == T_COMA) {
                    avanzar();
                    args.add(expresion());
                }
            }
            esperar(T_PAR_CIERRA, ")");
        }
        SemanticRuleV3.Tipo p1 = args.isEmpty() ? null : SemanticRuleV3.Tipo.deCodigo(args.get(0).tipo);
        SemanticRuleV3.Tipo ret = fn.retornoPara(p1, p1 != null);
        List<String> textos = new ArrayList<>();
        for (Operando o : args) {
            textos.add(o.texto);
        }
        if (ret == SemanticRuleV3.Tipo.VOID) {
            llamada(lex(f), textos, VOID);
            return new Operando(VOID, LeerCSVComparacion.T_VARIANT, Clase.LLAMADA);
        }
        String r = nuevoTemporal(ret.codigo);
        llamada(lex(f), textos, r);
        return new Operando(r, ret.codigo, Clase.LLAMADA);
    }

    /** Lista de expresiones separadas por coma hasta {@code cierre} (sin consumirlo). */
    private List<String> argumentos(int cierre) {
        List<String> args = new ArrayList<>();
        if (tcActual() == cierre) {
            return args;
        }
        args.add(expresion().texto);
        while (tcActual() == T_COMA) {
            avanzar();
            args.add(expresion().texto);
        }
        return args;
    }

    /** Operación binaria: op | izq | der | temporal (del tipo que regresa la operación). */
    private Operando binaria(Token op, Operando izq, Operando der) {
        int tipo = tipoResultado(op.getTokenClass(), izq.tipo, der.tipo);
        String r = nuevoTemporal(tipo);
        emitir(lex(op), izq.texto, der.texto, r);
        return new Operando(r, tipo, Clase.OPERACION);
    }

    // =========================================================
    // LLAMADAS: CALL / ARR con argumentos de 2 en 2
    // =========================================================

    private void llamada(String nombre, List<String> args, String resultado) {
        llamada("CALL", nombre, args, resultado);
    }

    /**
     * A | nombre                  (primer renglón)
     *   | arg1 | arg2 |           (argumentos de 2 en 2)
     *   | arg3 |      | resultado (el resultado va en el último renglón)
     */
    private void llamada(String accion, String nombre, List<String> args, String resultado) {
        if (args.isEmpty()) {
            emitir(accion, nombre, "", resultado); // sin argumentos: el resultado va en el renglón del CALL
            return;
        }
        emitir(accion, nombre, "", "");
        for (int i = 0; i < args.size(); i += 2) {
            String a1 = args.get(i);
            String a2 = i + 1 < args.size() ? args.get(i + 1) : "";
            boolean ultimo = i + 2 >= args.size();
            emitir("", a1, a2, ultimo ? resultado : "");
        }
    }

    // =========================================================
    // TEMPORALES Y TIPOS
    // =========================================================

    /** Temporal nuevo con secuencia única por tipo: TDec1, TDec2, TBoolean1... */
    private String nuevoTemporal(int tipo) {
        int n = contadorTemporales.merge(tipo, 1, Integer::sum);
        contador.temporal(ambitoActual(), tipo);
        return LeerCSVComparacion.prefijoTemporal(tipo) + n;
    }

    /**
     * Tipo del temporal = tipo que regresa la operación. Primero las tablas CSV
     * del proyecto (las mismas de Semántica 1); si no hay tabla para el operador
     * se usa la regla general.
     */
    private int tipoResultado(int tc, int izq, int der) {
        if (LeerCSVComparacion.tieneTabla(tc)) {
            int r = comparacion.ObtenerTipoPorTipos(izq, der, tc);
            if (r > 0) {
                return r;
            }
        }
        if ((tc <= -20 && tc >= -28) || tc == -30 || tc == -31) {
            return LeerCSVComparacion.T_BOOLEAN; // relacionales, igualdad, && ||
        }
        if (tc == -4 || tc == -5 || tc == -106) {
            return esEntero(izq) && esEntero(der) ? mayorNumerico(izq, der) : LeerCSVComparacion.T_BOOLEAN;
        }
        if (tc <= -17 && tc >= -19) {
            return esEntero(izq) ? izq : LeerCSVComparacion.T_VARIANT; // corrimientos
        }
        if (izq == LeerCSVComparacion.T_VARIANT || der == LeerCSVComparacion.T_VARIANT) {
            return LeerCSVComparacion.T_VARIANT;
        }
        if (tc == T_MAS && (izq == LeerCSVComparacion.T_CADENA || der == LeerCSVComparacion.T_CADENA)) {
            return LeerCSVComparacion.T_CADENA;
        }
        if (esNumerico(izq) && esNumerico(der)) {
            return mayorNumerico(izq, der);
        }
        return LeerCSVComparacion.T_VARIANT;
    }

    private static final int[] ORDEN_NUMERICO = {
        LeerCSVComparacion.T_BIN, LeerCSVComparacion.T_OCT, LeerCSVComparacion.T_HEX,
        LeerCSVComparacion.T_DEC, LeerCSVComparacion.T_REAL, LeerCSVComparacion.T_EXP
    };

    private static int rango(int tipo) {
        for (int i = 0; i < ORDEN_NUMERICO.length; i++) {
            if (ORDEN_NUMERICO[i] == tipo) {
                return i;
            }
        }
        return -1;
    }

    private static boolean esNumerico(int tipo) {
        return rango(tipo) >= 0;
    }

    private static int mayorNumerico(int a, int b) {
        return rango(a) >= rango(b) ? a : b;
    }

    private static boolean esEntero(int tipo) {
        return tipo >= LeerCSVComparacion.T_BIN && tipo <= LeerCSVComparacion.T_HEX;
    }

    /** += -> +, -= -> -, *= -> *, /= -> /, %= -> %. */
    private static int operadorDeCompuesta(int tc) {
        return switch (tc) {
            case -34 -> -11;
            case -35 -> -12;
            case -36 -> -13;
            case -37 -> -14;
            case -38 -> -15;
            default -> tc;
        };
    }

    // =========================================================
    // PILA DE ESTRUCTURAS
    // =========================================================

    private Estructura abrir(String tipo, String detalle, int linea) {
        Estructura e = new Estructura(tipo, detalle, linea);
        pilaEstructuras.push(e);
        return e;
    }

    /** Cierre estrictamente LIFO: solo se cierra la estructura del tope. */
    private void cerrar(Estructura e) {
        if (pilaEstructuras.peek() != e) {
            throw new IllegalStateException("Cierre fuera de orden: se intentó cerrar " + e
                    + " pero el tope de la pila es " + pilaEstructuras.peek());
        }
        pilaEstructuras.pop();
    }

    // =========================================================
    // EMISIÓN Y NOMBRES DE ETIQUETAS
    // =========================================================

    /** Etiquetas que se numeran al llegar a cierto renglón aunque aún no aparezcan. */
    private final Map<Integer, List<Etiqueta>> reservadas = new HashMap<>();

    private void reservarNombre(Etiqueta e) {
        reservadas.computeIfAbsent(cuadruplos.size(), k -> new ArrayList<>()).add(e);
    }

    private void emitir(String a, String v1, String v2, String r) {
        agregar(Cuadruplo.operacion(a, v1, v2, r, lineaActual));
    }

    private void agregar(Cuadruplo c) {
        c.setAmbito(ambitoActual());
        cuadruplos.add(c);
    }

    private void abrirAmbito() {
        pilaAmbitos.push(++contadorAmbitos);
        contador.ambito(contadorAmbitos);
    }

    private void cerrarAmbito() {
        pilaAmbitos.pop();
    }

    private int ambitoActual() {
        return pilaAmbitos.isEmpty() ? 0 : pilaAmbitos.peek();
    }

    /**
     * Pone nombre a las etiquetas en el orden en que aparecen (en E o en R), con un
     * contador por estructura que nunca se reinicia: if-E1, if-E2..., Wil-E1...
     * Así los números van seguidos, sin huecos, y nunca se repiten.
     */
    private void nombrarEtiquetas() {
        Map<String, Integer> siguiente = new HashMap<>();
        for (int i = 0; i < cuadruplos.size(); i++) {
            Cuadruplo c = cuadruplos.get(i);
            List<Etiqueta> orden = new ArrayList<>(reservadas.getOrDefault(i, List.of()));
            orden.add(c.getEtiquetaE());
            orden.add(c.getEtiquetaR());
            for (Etiqueta e : orden) {
                if (e != null && e.nombre() == null) {
                    int n = siguiente.merge(e.raiz().prefijo, 1, Integer::sum);
                    e.ponerNombre(e.raiz().prefijo + "-E" + n);
                }
            }
        }
    }

    // =========================================================
    // TOKENS
    // =========================================================

    private Token actual() {
        if (pos < toks.size()) {
            return toks.get(pos);
        }
        return new Token("<fin>", Token.Tipo.UNKNOWN, lineaActual, 0, T_FIN);
    }

    private int tcActual() {
        return actual().getTokenClass();
    }

    private int tcSiguiente() {
        return pos + 1 < toks.size() ? toks.get(pos + 1).getTokenClass() : T_FIN;
    }

    private Token avanzar() {
        if (pos >= toks.size()) {
            throw finDeCodigo();
        }
        Token t = toks.get(pos++);
        lineaActual = t.getLinea();
        return t;
    }

    private Token esperar(int tc, String descripcion) {
        Token t = actual();
        if (t.getTokenClass() == T_FIN) {
            throw finDeCodigo();
        }
        if (t.getTokenClass() != tc) {
            throw new Detencion("Se esperaba '" + descripcion + "' y llegó '" + lex(t) + "'", t.getLinea());
        }
        return avanzar();
    }

    /** Avanza (respetando paréntesis/corchetes/llaves) hasta dejar {@code tc} como token actual. */
    private void saltarHasta(int tc) {
        int prof = 0;
        while (true) {
            int t = tcActual();
            if (t == T_FIN) {
                throw finDeCodigo();
            }
            if (prof == 0 && t == tc) {
                return;
            }
            if (t == T_PAR_ABRE || t == T_CORCHETE_ABRE || t == T_LLAVE_ABRE) {
                prof++;
            } else if (t == T_PAR_CIERRA || t == T_CORCHETE_CIERRA || t == T_LLAVE_CIERRA) {
                prof--;
            }
            avanzar();
        }
    }

    private Detencion finDeCodigo() {
        return new Detencion("Fin del código válido", lineaActual);
    }

    private Detencion inesperado(Token t) {
        return new Detencion("Construcción no esperada en cuádruplos: '" + lex(t) + "'", t.getLinea());
    }

    private static String lex(Token t) {
        return t.getLexema() == null ? "" : t.getLexema().trim();
    }

    /** Identificadores: @ #B #D #O #X $ ¿ ¡ (-60..-67) y nombres sin tipo (-107). */
    private static boolean esId(int tc) {
        return (tc <= -60 && tc >= -67) || tc == T_ID_PROCEDIMIENTO;
    }

    /** Constantes: cadena, bin, dec, oct, hex, real, exp, true, false, null. */
    private static boolean esConstante(int tc) {
        return (tc <= -53 && tc >= -59) || tc == -72 || tc == -73 || tc == -74;
    }

    private static boolean esAsignacion(int tc) {
        return tc <= -33 && tc >= -44;
    }

    /** Tokens con los que empieza una expresión (FIRST de OR). */
    private static boolean iniciaExpresion(int tc) {
        return esId(tc) || esConstante(tc) || tc == T_MAS || tc == T_MENOS || tc == T_PAR_ABRE
                || tc == T_MAS_MAS || tc == T_MENOS_MENOS || tc == T_NOT || tc == T_NOT_BIT
                || FuncionNativa.deToken(tc) != null;
    }
}
