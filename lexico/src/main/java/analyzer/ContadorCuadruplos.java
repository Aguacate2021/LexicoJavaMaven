package analyzer;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import analyzer.TablasComparacion.LeerCSVComparacion;

/**
 * Tabla de contadores de los cuádruplos (hoja "Cont-CI").
 *
 * Por línea de código cuenta:
 *  - los temporales creados, en la columna de su tipo (TBin ... TVariant);
 *    el temporal de retorno de una función (TD#Dsuma) cuenta 1 en la columna
 *    del tipo de la función, una sola vez;
 *  - los operadores que aparecen en la columna A, agrupados en EXACTAMENTE
 *    las 4 categorías: Op-Rel, Ope-Log, Oper-Arit y op-turno.
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

    /** Encabezados de las columnas de temporales (los mismos de SEMANTICA 1). */
    public static final String[] COLUMNAS_TEMPORALES = {
        "TBin", "TDec", "TOct", "THex", "TReal", "Texp", "TCadena", "TBoolean", "TVariant"
    };

    /** Índice de cada categoría dentro del arreglo por línea (después de los 9 temporales). */
    private static final int BASE_CATEGORIAS = COLUMNAS_TEMPORALES.length;
    public static final int COLUMNAS = BASE_CATEGORIAS + Categoria.values().length;

    // línea -> [9 temporales][4 categorías]
    private final Map<Integer, int[]> porLinea = new TreeMap<>();
    // categoría -> símbolo -> cantidad (todos los símbolos aparecen, aunque valgan 0)
    private final Map<Categoria, Map<String, Integer>> porOperador = new EnumMap<>(Categoria.class);

    public ContadorCuadruplos() {
        reiniciar();
    }

    public final void reiniciar() {
        porLinea.clear();
        porOperador.clear();
        for (Categoria c : Categoria.values()) {
            Map<String, Integer> m = new LinkedHashMap<>();
            for (String s : c.simbolos) {
                m.put(s, 0);
            }
            porOperador.put(c, m);
        }
    }

    /** Un temporal nuevo del tipo indicado (1..10 de LeerCSVComparacion). */
    void temporal(int linea, int tipo) {
        int col = columnaTemporal(tipo);
        if (col >= 0) {
            fila(linea)[col]++;
        }
    }

    /** Un operador en la columna A; los que no son de las 4 categorías se ignoran. */
    void operador(int linea, String simbolo) {
        Categoria c = Categoria.de(simbolo);
        if (c == null) {
            return;
        }
        fila(linea)[BASE_CATEGORIAS + c.ordinal()]++;
        porOperador.get(c).merge(simbolo.trim(), 1, Integer::sum);
    }

    private int[] fila(int linea) {
        return porLinea.computeIfAbsent(linea, k -> new int[COLUMNAS]);
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

    /** línea -> {TBin..TVariant, Op-Rel, Ope-Log, Oper-Arit, op-turno}. */
    public Map<Integer, int[]> getPorLinea() {
        return Collections.unmodifiableMap(porLinea);
    }

    public Map<Categoria, Map<String, Integer>> getPorOperador() {
        return Collections.unmodifiableMap(porOperador);
    }

    public int[] totales() {
        int[] t = new int[COLUMNAS];
        for (int[] f : porLinea.values()) {
            for (int i = 0; i < COLUMNAS; i++) {
                t[i] += f[i];
            }
        }
        return t;
    }
}
