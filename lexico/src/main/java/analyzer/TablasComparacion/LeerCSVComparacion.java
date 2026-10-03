package analyzer.TablasComparacion;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
public class LeerCSVComparacion {
    private static final String ARCHIVO_CSV_SUMA = "lexico\\src\\main\\java\\TablasComparacion\\analyzer\\suma.csv"; // Ruta al archivo CSV suma
    private static final String ARCHIVO_CSV_RESTA = "lexico\\src\\main\\java\\TablasComparacion\\analyzer\\resta.csv"; // Ruta al archivo CSV resta
    private static final String ARCHIVO_CSV_MULTIPLICACION = "lexico\\src\\main\\java\\TablasComparacion\\analyzer\\multiplicacion.csv"; // Ruta al archivo CSV multiplicación
    private static final String ARCHIVO_CSV_DIVISION = "lexico\\src\\main\\java\\TablasComparacion\\analyzer\\division.csv"; // Ruta al archivo CSV división
    private static final String ARCHIVO_CSV_LOGICO = "lexico\\src\\main\\java\\TablasComparacion\\analyzer\\logico.csv"; // Ruta al archivo CSV lógico
    private static final String ARCHIVO_CSV_RELACIONAL = "lexico\\src\\main\\java\\TablasComparacion\\analyzer\\relacional.csv"; // Ruta al archivo CSV relacional
    private static final String ARCHIVO_CSV_MODULO = "lexico\\src\\main\\java\\TablasComparacion\\analyzer\\modulo.csv"; // Ruta al archivo CSV módulo

    private static String Valores[][]= new String[12][12]; // Matriz para almacenar los valores
    private static String ValoresSuma[][]= new String[12][12]; // Matriz para almacenar los valores
    private static String ValoresResta[][]= new String[12][12]; // Matriz para almacenar los valores
    private static String ValoresMultiplicacion[][]= new String[12][12]; // Matriz para almacenar los valores
    private static String ValoresDivision[][]= new String[12][12]; // Matriz para almacenar los valores
    private static String ValoresLogico[][]= new String[12][12]; // Matriz para almacenar los valores
    private static String ValoresRelacional[][]= new String[12][12]; // Matriz para almacenar los valores
    private static String ValoresModulo[][]= new String[12][12]; // Matriz para almacenar los valores
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
private void cargarTablas() {
    ValoresSuma = cargarTabla(ARCHIVO_CSV_SUMA);
    ValoresResta = cargarTabla(ARCHIVO_CSV_RESTA);
    ValoresMultiplicacion = cargarTabla(ARCHIVO_CSV_MULTIPLICACION);
    ValoresDivision = cargarTabla(ARCHIVO_CSV_DIVISION);
    ValoresLogico = cargarTabla(ARCHIVO_CSV_LOGICO);
    ValoresRelacional = cargarTabla(ARCHIVO_CSV_RELACIONAL);
    ValoresModulo = cargarTabla(ARCHIVO_CSV_MODULO);
}
private String[][] cargarTabla(String archivoCsvSuma) {
    String linea;
    int lineaNum = 0;
    int columnaNum = 0;
    boolean primeraLinea = true; // Para saltar la primera línea (encabezados)
    boolean primeraColumna = true; // Para saltar la primera columna (nombres de filas)
    try (BufferedReader br = new BufferedReader(new FileReader(archivoCsvSuma))) {
        while ((linea = br.readLine()) != null) {
            if (primeraLinea) {
                primeraLinea = false;
                continue;
            }
            String[] valores = linea.split(separador);
            columnaNum = 0;
            for (String valor : valores) {
                if (primeraColumna) {
                    primeraColumna = false;
                    continue;
                }
                Valores[lineaNum][columnaNum] = valor.trim().toLowerCase();
                columnaNum++;
            }
            lineaNum++;
        }
    } catch (IOException e) {
        e.printStackTrace();
    }
    return Valores;
}
private int obtenerFilasYColumnas(int c){
     if (mapaColumnasFilas.containsKey(c)) {
            return mapaColumnasFilas.get(c);
    }
    return 8;
}
public int ObtenerTipoDeTemporal(int fila, int columna, int tipoOperacion) {
        int numeroDeTabla = 0;
        int filaTabla = obtenerFilasYColumnas(fila);
        int columnaTabla = obtenerFilasYColumnas(columna);
        //fila  y columna son los id de los tokens que pueden ser identificadores o constantes
        //tipodeOperacion es el id del token operador que puede ser suma, resta, multiplicacion, division, logico o relacional
        numeroDeTabla = numeroobetnerTipoOperacion(tipoOperacion);
        
        if (numeroDeTabla == 0) {
            return obtenerNumeroDeString(ValoresSuma[filaTabla][columnaTabla]);
        }
        if (numeroDeTabla == 1) {
             return obtenerNumeroDeString(ValoresResta[filaTabla][columnaTabla]);
        }
        if (numeroDeTabla == 2) {
            return obtenerNumeroDeString(ValoresMultiplicacion[filaTabla][columnaTabla]);
        }
        if (numeroDeTabla == 3) {
            return obtenerNumeroDeString(ValoresDivision[filaTabla][columnaTabla]);
        }
        if (numeroDeTabla == 4) {
            return obtenerNumeroDeString(ValoresModulo[filaTabla][columnaTabla]);
        }
        if (numeroDeTabla == 5) {
            return obtenerNumeroDeString(ValoresLogico[filaTabla][columnaTabla]);
        }
        if (numeroDeTabla == 6) {
            return obtenerNumeroDeString(ValoresRelacional[filaTabla][columnaTabla]);
        }
        return -1;
}
private static int numeroobetnerTipoOperacion(int tipoOperacion) {
    if (tipoOperacion == -11) return 0; // Suma
    else if (tipoOperacion == -12) return 1; // Resta
    else if (tipoOperacion == -13) return 2; // Multiplicación
    else if (tipoOperacion == -14) return 3; // División
    else if (tipoOperacion == -15) return 4; // Modulo
    else if (tipoOperacion >= -26 && tipoOperacion <= -20) return 6; // Relacional
    else if (tipoOperacion >= -31 && tipoOperacion <= -29) return 5; // Lógico
    else return -1; // Operación desconocida
}
public static int obtenerNumeroDeString(String valor) {
    switch (valor.toLowerCase().trim()) {
        //El resultado de la celda en cada tabla puede ser uno de todos estos
        case "bin":
            return 1;
        case "dec":
            return 2;
        case "oct":
            return 3;
        case "hex":
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