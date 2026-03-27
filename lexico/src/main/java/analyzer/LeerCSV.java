package analyzer;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class LeerCSV {
    private static final String ARCHIVO_CSV = "lexico\\src\\main\\java\\analyzer\\ExcelPuro1.csv"; // Ruta al archivo CSV
    private static int Valores[][]= new int[100][100]; // Matriz para almacenar los valores (ajustar tamaño según necesidad)
    private static String separador = ";"; // o ";"
    private static final Map<Character, Integer> mapa = new HashMap<>();

    static {
        mapa.put('+', 1);
        mapa.put('-', 2);
        mapa.put('*', 3);
        mapa.put('/', 4);
        mapa.put('%', 5);
        mapa.put('=', 6);
        mapa.put('!', 7);
        mapa.put('<', 8);
        mapa.put('>', 9);
        mapa.put('&', 10);
        mapa.put('|', 11);
        mapa.put('^', 12);
        mapa.put('~', 13);
        mapa.put('?', 14);
        mapa.put(':', 15);
        mapa.put('.', 16);
        mapa.put(',', 17);
        mapa.put(';', 18);
        mapa.put('(', 19);
        mapa.put(')', 20);
        mapa.put('{', 21);
        mapa.put('}', 22);
        mapa.put('[', 23);
        mapa.put(']', 24);
        mapa.put('@', 25);
        mapa.put('#', 26);
        mapa.put('$', 27);
        mapa.put('¿', 28);
        mapa.put('¡', 29);
        mapa.put('"', 30);
        mapa.put('\'', 31);
        mapa.put('_', 32);
    }
    
    public static void LeerCSV() {
        String archivoCSV = ARCHIVO_CSV;
        String linea;
        int lineaNum = 0;
        int columnaNum = 0;
        boolean primeraLinea = true; // Para saltar la primera línea (encabezados)
        boolean primeraColumna = true; // Para controlar el salto de línea después de cada fila
        try (BufferedReader br = new BufferedReader(new FileReader(archivoCSV))) {
            while ((linea = br.readLine()) != null) {
                if (primeraLinea) {
                    primeraLinea = false;
                    continue;
                }
                columnaNum = 0; // Reiniciar el número de columna para cada línea
                String[] datos = linea.split(separador);
                for (String dato : datos) {
                    if (primeraColumna) {
                        primeraColumna = false;
                    } else {
                        agregarValor(Integer.parseInt(dato), lineaNum, columnaNum); // Almacenar el valor en la matriz
                        columnaNum++;
                    }
                }
                primeraColumna = true; // Reiniciar para la siguiente línea
                lineaNum++;
                System.out.println();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
    }
    public static void agregarValor(int valor, int fila, int columna) {
        Valores[fila][columna] = valor;
    }

    public static int clasificar(char c) {

        
        if (mapa.containsKey(c)) {
            return mapa.get(c);
        }

        
        if (c == '0' || c == '1') return 33;
        if (c >= '2' && c <= '7') return 34;
        if (c == '8' || c == '9') return 35;

        
        if (c == 'X') return 36;
        if (c == 'x') return 37;
        if (c == 'B') return 38;
        if (c == 'b') return 39;
        if (c == 'O') return 40;
        if (c == 'o') return 41;
        if (c == 'L') return 42;
        if (c == 'l') return 43;
        if (c == 'D') return 44;
        if (c == 'd') return 45;

        
        if (c == 'A' || c == 'a') return 46;
        if (c == 'C' || c == 'c') return 47;
        if ((c >= 'E' && c <= 'F') || (c >= 'e' && c <= 'f')) return 48;
        if ((c >= 'G' && c <= 'K') || (c >= 'g' && c <= 'k')) return 49;
        if ((c >= 'M' && c <= 'N') || (c >= 'm' && c <= 'n')) return 50;
        if ((c >= 'P' && c <= 'W') || (c >= 'p' && c <= 'w')) return 51;
        if ((c >= 'Y' && c <= 'Z') || (c >= 'y' && c <= 'z')) return 52;

        
        if (c == '\n') return 53;
        if (c == ' ') return 54;
        if (c == '\t') return 55;
        

        
        return 0;
    }
    
    public static int getValor(int fila, int columna) {
        return Valores[fila][columna];
    }
}
