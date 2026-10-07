package analyzer.TablasComparacion;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
public class LeerCSVComparacion {
    private static final String ARCHIVO_CSV_SUMA = "lexico\\src\\main\\java\\analyzer\\TablasComparacion\\suma.csv"; // Ruta al archivo CSV suma
    private static final String ARCHIVO_CSV_RESTA = "lexico\\src\\main\\java\\\\analyzer\\TablasComparacion\\resta.csv"; // Ruta al archivo CSV resta
    private static final String ARCHIVO_CSV_MULTIPLICACION = "lexico\\src\\main\\java\\analyzer\\TablasComparacion\\multiplicacion.csv"; // Ruta al archivo CSV multiplicación
    private static final String ARCHIVO_CSV_DIVISION = "lexico\\src\\main\\java\\analyzer\\TablasComparacion\\division.csv"; // Ruta al archivo CSV división
    private static final String ARCHIVO_CSV_LOGICO = "lexico\\src\\main\\java\\analyzer\\TablasComparacion\\logico.csv"; // Ruta al archivo CSV lógico
    private static final String ARCHIVO_CSV_RELACIONAL = "lexico\\src\\main\\java\\analyzer\\TablasComparacion\\relacional.csv"; // Ruta al archivo CSV relacional
    private static final String ARCHIVO_CSV_RESTO = "lexico\\src\\main\\java\\analyzer\\TablasComparacion\\resto.csv"; // Ruta al archivo CSV resto
    private static final String ARCHIVO_CSV_RELACIONAL2 = "lexico\\src\\main\\java\\analyzer\\TablasComparacion\\relacional2.csv"; // Ruta al archivo CSV relacional2

    private static String ValoresSuma[][]= new String[12][12]; // Matriz para almacenar los valores
    private static String ValoresResta[][]= new String[12][12]; // Matriz para almacenar los valores
    private static String ValoresMultiplicacion[][]= new String[12][12]; // Matriz para almacenar los valores
    private static String ValoresDivision[][]= new String[12][12]; // Matriz para almacenar los valores
    private static String ValoresLogico[][]= new String[12][12]; // Matriz para almacenar los valores
    private static String ValoresRelacional[][]= new String[12][12]; // Matriz para almacenar los valores
    private static String ValoresResto[][]= new String[12][12]; // Matriz para almacenar los valores
    private static String ValoresRelacional2[][]= new String[12][12]; // Matriz para almacenar los valores
    private static String separador = ";"; // o ";"
    private static final Map<Integer, Integer> mapaColumnasFilas = new HashMap<>();
    static {
         //Identificadores y constantes que son booleanos
        mapaColumnasFilas.put(-54, 0);
        mapaColumnasFilas.put(-61, 0);
         //Identificadores y constantes que son booleanos
        mapaColumnasFilas.put(-55, 1);
        mapaColumnasFilas.put(-62, 1);
         //Identificadores y constantes que son booleanos
        mapaColumnasFilas.put(-56, 2);
        mapaColumnasFilas.put(-63, 2);
         //Identificadores y constantes que son booleanos
        mapaColumnasFilas.put(-57, 3);
        mapaColumnasFilas.put(-64, 3);
         //Identificadores y constantes que son booleanos
        mapaColumnasFilas.put(-58, 4);
        mapaColumnasFilas.put(-65, 4);
        //Identificadores y constantes que son booleanos
        mapaColumnasFilas.put(-59, 5);
        mapaColumnasFilas.put(-66, 5);
        //Identificadores y constantes que son booleanos
        mapaColumnasFilas.put(-53, 6);
        mapaColumnasFilas.put(-60, 6);
        //Identificadores y constantes que son booleanos
        mapaColumnasFilas.put(-72, 7);
        mapaColumnasFilas.put(-73, 7);
        mapaColumnasFilas.put(-67, 7);
    }
private static boolean tablasCargadas = false;

/** Carga las 7 tablas (se llama al iniciar cada compilación; también de forma perezosa). */
public void cargarTablas() {
    ValoresSuma = cargarTabla(ARCHIVO_CSV_SUMA);
    ValoresResta = cargarTabla(ARCHIVO_CSV_RESTA);
    ValoresMultiplicacion = cargarTabla(ARCHIVO_CSV_MULTIPLICACION);
    ValoresDivision = cargarTabla(ARCHIVO_CSV_DIVISION);
    ValoresLogico = cargarTabla(ARCHIVO_CSV_LOGICO);
    ValoresRelacional = cargarTabla(ARCHIVO_CSV_RELACIONAL);
    ValoresResto = cargarTabla(ARCHIVO_CSV_RESTO);
    if (ValoresResto[0][0] == null) {
        // Fallback a ValoresDivision si resto.csv no está presente o falla
        ValoresResto = ValoresDivision;
    }
    ValoresRelacional2 = cargarTabla(ARCHIVO_CSV_RELACIONAL2);
    tablasCargadas = true;
}
// CORRECCIÓN: cada CSV tiene su propia matriz (antes todos escribían en "Valores")
// y la etiqueta de la primera columna se salta en TODAS las filas (antes solo en la primera).
private String[][] cargarTabla(String archivoCsv) {
    String[][] tabla = new String[12][12];
    String linea;
    int lineaNum = 0;
    boolean primeraLinea = true; // encabezados
    try (BufferedReader br = new BufferedReader(new FileReader(archivoCsv))) {
        while ((linea = br.readLine()) != null && lineaNum < 12) {
            if (primeraLinea) {
                primeraLinea = false;
                continue;
            }
            if (linea.isBlank()) continue;
            String[] valores = linea.split(separador);
            for (int c = 1; c < valores.length && c - 1 < 12; c++) { // c=0 es la etiqueta de fila
                tabla[lineaNum][c - 1] = valores[c].trim().toLowerCase();
            }
            lineaNum++;
        }
    } catch (IOException e) {
        System.err.println("[TABLAS] No se pudo leer: " + archivoCsv);
        e.printStackTrace();
    }
    return tabla;
}
private int obtenerFilasYColumnas(int c){
     if (mapaColumnasFilas.containsKey(c)) {
            return mapaColumnasFilas.get(c);
    }
    return 8;
}
// Números de tipo (los mismos de obtenerNumeroDeString)
public static final int T_BIN = 1, T_DEC = 2, T_OCT = 3, T_HEX = 4, T_REAL = 5,
        T_EXP = 6, T_CADENA = 7, T_CARACTER = 8, T_BOOLEAN = 9, T_VARIANT = 10;

/** Versión por códigos de token (firma original). */
public int ObtenerTipoDeTemporal(int fila, int columna, int tipoOperacion) {
    return ObtenerTipoPorTipos(tipoDeToken(fila), tipoDeToken(columna), tipoOperacion);
}

/**
 * Versión por TIPOS (1..10). Sirve para operandos que no son tokens (temporales).
 * Retorna el tipo resultante (1..10), -3000 si la tabla dice Error,
 * o -1 si el operador no tiene tabla.
 */
public int ObtenerTipoPorTipos(int tipoIzq, int tipoDer, int tipoOperacion) {
    if (!tablasCargadas) cargarTablas();
    String[][] tabla;
    switch (numeroobetnerTipoOperacion(tipoOperacion)) {
        case 0: tabla = ValoresSuma; break;
        case 1: tabla = ValoresResta; break;
        case 2: tabla = ValoresMultiplicacion; break;
        case 3: tabla = ValoresDivision; break;
        case 4: tabla = ValoresResto; break;
        case 5: tabla = ValoresLogico; break;
        case 6: tabla = ValoresRelacional; break;
        case 7: tabla = ValoresRelacional2; break;
        default: return -1;
    }
    return obtenerNumeroDeString(tabla[indiceDeTipo(tipoIzq)][indiceDeTipo(tipoDer)]);
}

public static boolean tieneTabla(int tipoOperacion) {
    return numeroobetnerTipoOperacion(tipoOperacion) >= 0;
}

/** Código de token -> tipo (1..10). Lo que no está en el mapa es Variant. */
public static int tipoDeToken(int tc) {
    int idx = obtenerFilasYColumnasEstatico(tc);
    if (idx <= 6) return idx + 1;
    return idx == 7 ? T_BOOLEAN : T_VARIANT;
}

/** Tipo (1..10) -> fila/columna de las tablas (0..8). */
private static int indiceDeTipo(int tipo) {
    if (tipo >= T_BIN && tipo <= T_CADENA) return tipo - 1;
    if (tipo == T_CARACTER) return 6;   // no tiene fila propia: se trata como Cadena
    if (tipo == T_BOOLEAN) return 7;
    return 8;                           // Variant
}

private static int obtenerFilasYColumnasEstatico(int c) {
    Integer i = mapaColumnasFilas.get(c);
    return i != null ? i : 8;
}

/** Prefijo del nombre del temporal según el tipo. */
public static String prefijoTemporal(int tipo) {
    switch (tipo) {
        case T_BIN: return "TBin";
        case T_DEC: return "TDec";
        case T_OCT: return "TOct";
        case T_HEX: return "THex";
        case T_REAL: return "TReal";
        case T_EXP: return "TExp";
        case T_CADENA: return "TCadena";
        case T_CARACTER: return "TCaracter";
        case T_BOOLEAN: return "TBoolean";
        default: return "TVariant";
    }
}
private static int numeroobetnerTipoOperacion(int tipoOperacion) {
    if (tipoOperacion == -11) return 0; // Suma
    else if (tipoOperacion == -12) return 1; // Resta
    else if (tipoOperacion == -13) return 2; // Multiplicación
    else if (tipoOperacion == -14) return 3; // División
    else if (tipoOperacion == -15||tipoOperacion == -17||tipoOperacion == -18||tipoOperacion == -19||tipoOperacion == -6) return 4; // Resto
    else if (tipoOperacion == -31 || tipoOperacion == -30 || tipoOperacion == -4||tipoOperacion == -5||tipoOperacion == -106) return 5; // Lógico
    else if (tipoOperacion<=-20 && tipoOperacion>=-23) return 6; // Relacional
    else if (tipoOperacion<=-24 && tipoOperacion>=-25) return 7; // Relacional2

    else return -1; // Operación desconocida
}
public static int obtenerNumeroDeString(String valor) {
    if (valor == null) return -3000; // celda vacía / tabla no cargada
    switch (valor.toLowerCase().trim()) {
        //El resultado de la celda en cada tabla puede ser uno de todos estos
        case "bin":
            return 1;
        case "dec":
            return 2;
        case "oct":
            return 3;
        case "hex":
        case "hexa": // CORRECCIÓN: los CSV escriben "Hexa" en varias celdas
            return 4;
        case "real":
            return 5;
        case "exp":
            return 6;
        case "cadena":
            return 7;
        case "caracter":
            return 8;
        case "boolean":
            return 9;
        case "variant":
            return 10;
        case "error":
            return -3000;
        default:
            return -3000; // Valor por defecto si no coincide con ninguno
    }
}
}