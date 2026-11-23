package analyzer;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import analyzer.TablasComparacion.LeerCSVComparacion;

/**
 * Tabla de contadores de los cuádruplos (hoja "Cont-CI"), una fila por ÁMBITO
 * (0 = global / main principal; def y for abren uno nuevo, igual que en sintaxis).
 *
 * Columnas:
 *  - Temporales: TBin TDec TOct THex TReal Texp TCadena TBoolean TVariant. El temporal de
 *    retorno de una función (TD#Dsuma) cuenta 1 en la columna de su tipo.
 *  - Acciones: Arr, call, =, Op-Rel, Ope-Log, Oper-Arit, op-turno, JF, JMP (JT cuenta como salto en JMP).
 *  - Etiquetas: if-E, For-E, do-E, Whi-E, Switch-E, Ter-E (cada etiqueta una vez, por su fila en E).
 *  - DEF (1 por función declarada) y main (1 por método abierto, incluido el principal).
 */
public class ContadorCuadruplos {

    /** Las 4 categorías de operadores, con sus símbolos en el orden de la tabla. */
    public enum Categoria {
        OP_REL("Op-Rel", "<", "<=", ">", ">=", "!=", "==", "is", "is not", "in", "in not"),
        OPE_LOG("Ope-Log", "&&", "&", "|", "||", "!", "#", "~"),
        OPER_ARIT("Oper-Arit", "+", "-", "*", "/", "//", "**", "++", "--", "%", "^"),
        OP_TURNO("op-turno", "<<", ">>", ">>>", "===", "!==");

        public final String titulo;
        public final String[] simbolos;

        Categoria(String titulo, String... simbolos) {
            this.titulo = titulo;
            this.simbolos = simbolos;
        }

        /** Categoría del símbolo exacto (sin espacios sobrantes), o null si no es operador. */
        public static Categoria de(String simbolo) {
            if (simbolo == null) {
                return null;
            }
            String s = simbolo.trim().replaceAll("\\s+", " ");
            for (Categoria c : values()) {
                for (String x : c.simbolos) {
                    if (x.equals(s)) {
                        return c;
                    }
                }
            }
            return null;
        }
    }

    public static final String[] COLUMNAS_TEMPORALES = {
        "TBin", "TDec", "TOct", "THex", "TReal", "Texp", "TCadena", "TBoolean", "TVariant"
    };
    public static final String[] COLUMNAS_ACCIONES = {
        "Arr", "call", "=", "Op-Rel", "Ope-Log", "Oper-Arit", "op-turno", "JF", "JMP/JT"
    };
    /** Prefijos de etiqueta (los del generador) y su encabezado. */
    public static final String[] PREFIJOS_ETIQUETA = { "if", "For", "do", "Whi", "Switch", "Ter" };
    public static final String[] COLUMNAS_ETIQUETAS = { "if-E", "For-E", "do-E", "Whi-E", "Switch-E", "Ter-E" };
    public static final String[] COLUMNAS_FINALES = { "DEF", "main" };

    private static final int BASE_ACC = COLUMNAS_TEMPORALES.length;
    private static final int COL_ARR = BASE_ACC, COL_CALL = BASE_ACC + 1, COL_IGUAL = BASE_ACC + 2;
    private static final int BASE_CAT = BASE_ACC + 3;
    private static final int COL_JF = BASE_ACC + 7, COL_JMP = BASE_ACC + 8;
    private static final int BASE_ET = BASE_ACC + COLUMNAS_ACCIONES.length;
    private static final int COL_DEF = BASE_ET + COLUMNAS_ETIQUETAS.length, COL_MAIN = COL_DEF + 1;
    public static final int COLUMNAS = COL_MAIN + 1;

    // ámbito -> contadores
    private final Map<Integer, int[]> porAmbito = new TreeMap<>();
    // categoría -> símbolo -> cantidad (todos los símbolos aparecen, aunque valgan 0)
    private final Map<Categoria, Map<String, Integer>> porOperador = new EnumMap<>(Categoria.class);

    public ContadorCuadruplos() {
        reiniciar();
    }

    public final void reiniciar() {
        porAmbito.clear();
        porOperador.clear();
        for (Categoria c : Categoria.values()) {
            Map<String, Integer> m = new LinkedHashMap<>();
            for (String s : c.simbolos) {
                m.put(s, 0);
            }
            porOperador.put(c, m);
        }
    }

    /** Asegura la fila del ámbito (aunque quede en ceros). */
    void ambito(int ambito) {
        fila(ambito);
    }

    /** Un temporal nuevo del tipo indicado (1..10 de LeerCSVComparacion). */
    void temporal(int ambito, int tipo) {
        int col = columnaTemporal(tipo);
        if (col >= 0) {
            fila(ambito)[col]++;
        }
    }

    /** Una función declarada (def). */
    void definicion(int ambito) {
        fila(ambito)[COL_DEF]++;
    }

    /** Cuenta la acción, el operador o la etiqueta de un renglón. */
    void cuadruplo(Cuadruplo c) {
        int[] f = fila(c.getAmbito());
        if (c.esEtiqueta()) {
            String e = c.getE();
            int guion = e.lastIndexOf("-E");
            String prefijo = guion > 0 ? e.substring(0, guion) : e;
            for (int i = 0; i < PREFIJOS_ETIQUETA.length; i++) {
                if (PREFIJOS_ETIQUETA[i].equals(prefijo)) {
                    f[BASE_ET + i]++;
                }
            }
            return;
        }
        String a = c.getA();
        switch (a) {
            case "ARR" -> f[COL_ARR]++;
            case "CALL" -> f[COL_CALL]++;
            case "=" -> f[COL_IGUAL]++;
            case "JF" -> f[COL_JF]++;
            case "JMP", "JT" -> f[COL_JMP]++;
            case "Metodo" -> f[COL_MAIN]++;
            default -> {
                String simbolo = "inc".equals(a) ? "++" : "dec".equals(a) ? "--" : a; // inc/dec = ++/--
                Categoria cat = Categoria.de(simbolo);
                if (cat != null) {
                    f[BASE_CAT + cat.ordinal()]++;
                    porOperador.get(cat).merge(simbolo.trim(), 1, Integer::sum);
                }
            }
        }
    }

    private int[] fila(int ambito) {
        return porAmbito.computeIfAbsent(ambito, k -> new int[COLUMNAS]);
    }

    /** tipo 1..10 -> columna 0..8 (Carácter se cuenta como Cadena). */
    static int columnaTemporal(int tipo) {
        switch (tipo) {
            case LeerCSVComparacion.T_BIN: return 0;
            case LeerCSVComparacion.T_DEC: return 1;
            case LeerCSVComparacion.T_OCT: return 2;
            case LeerCSVComparacion.T_HEX: return 3;
            case LeerCSVComparacion.T_REAL: return 4;
            case LeerCSVComparacion.T_EXP: return 5;
            case LeerCSVComparacion.T_CADENA:
            case LeerCSVComparacion.T_CARACTER: return 6;
            case LeerCSVComparacion.T_BOOLEAN: return 7;
            case LeerCSVComparacion.T_VARIANT: return 8;
            default: return -1;
        }
    }

    /** ámbito -> {temporales, acciones, etiquetas, DEF, main}. */
    public Map<Integer, int[]> getPorAmbito() {
        return Collections.unmodifiableMap(porAmbito);
    }

    public Map<Categoria, Map<String, Integer>> getPorOperador() {
        return Collections.unmodifiableMap(porOperador);
    }

    public int[] totales() {
        int[] t = new int[COLUMNAS];
        for (int[] f : porAmbito.values()) {
            for (int i = 0; i < COLUMNAS; i++) {
                t[i] += f[i];
            }
        }
        return t;
    }
}
