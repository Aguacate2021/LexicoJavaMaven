package analyzer;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class LeerCSV2 {

    private static final String ARCHIVO_CSV = "lexico\\src\\main\\java\\analyzer\\ExcelPuro2.csv";
    private static int Valores[][] = new int[100][100];
    private static String separador = ";";
    private static final Map<Integer, Integer> map = new HashMap<>();

    static {
        map.put(-68,  0);
        map.put(-69,  1);
        map.put(-70,  2);
        map.put(-71,  3);
        // id = 4 (rango -67..-60, manejado en clasificarTransicion)
        map.put(-9,   5);
        map.put(-7,   6);
        map.put(-47,  7);
        map.put(-48,  8);
        map.put(-54,  9);
        map.put(-55, 10);
        map.put(-56, 11);
        map.put(-57, 12);
        map.put(-58, 13);
        map.put(-53, 14);
        map.put(-72, 15);
        map.put(-73, 16);
        map.put(-59, 17);
        map.put(-74, 18);
        map.put(-11, 19);
        map.put(-12, 20);
        map.put(-49, 21);
        map.put(-50, 22);
        map.put(-33, 23);
        map.put(-34, 24);
        map.put(-35, 25);
        map.put(-37, 26);
        map.put(-36, 27);
        map.put(-1,  28);
        map.put(-2,  29);
        map.put(-29, 30);
        map.put(-3,  31);
        map.put(-75, 32);
        map.put(-76, 33);
        map.put(-77, 34);
        map.put(-78, 35);
        map.put(-79, 36);
        map.put(-80, 37);
        map.put(-81, 38);
        map.put(-82, 39);
        map.put(-83, 40);
        map.put(-84, 41);
        map.put(-85, 42);
        map.put(-86, 43);
        map.put(-87, 44);
        map.put(-88, 45);
        map.put(-89, 46);
        map.put(-90, 47);
        map.put(-91, 48);
        map.put(-92, 49);
        map.put(-32, 50);
        map.put(-93, 51);
        map.put(-94, 52);
        map.put(-105, 53);
        map.put(-45, 54);
        map.put(-95, 55);
        map.put(-96, 56);
        map.put(-97, 57);
        map.put(-98, 58);
        map.put(-99, 59);
        map.put(-46, 60);
        map.put(-100, 61);
        map.put(-101, 62);
        map.put(-10, 63);
        map.put(-102, 64);
        map.put(-103, 65);
        map.put(-4,  66);
        map.put(-31, 67);
        map.put(-5,  68);
        map.put(-30, 69);
        map.put(-20, 70);
        map.put(-23, 71);
        map.put(-22, 72);
        map.put(-25, 73);
        map.put(-24, 74);
        map.put(-21, 75);
        map.put(-17, 76);
        map.put(-18, 77);
        map.put(-19, 78);
        map.put(-13, 79);
        map.put(-14, 80);
        map.put(-106, 81);
        map.put(-15, 82);
        map.put(-6,  83);
        map.put(-104, 84);
    }

    public static void LeerCSV() {
        String linea;
        int lineaNum = 0;
        boolean primeraLinea = true;
        boolean primeraColumna = true;
        try (BufferedReader br = new BufferedReader(new FileReader(ARCHIVO_CSV))) {
            while ((linea = br.readLine()) != null) {
                if (primeraLinea) {
                    primeraLinea = false;
                    continue;
                }
                int columnaNum = 0;
                String[] datos = linea.split(separador);
                for (String dato : datos) {
                    if (primeraColumna) {
                        primeraColumna = false;
                    } else {
                        try {
                            Valores[lineaNum][columnaNum] = Integer.parseInt(dato.trim());
                        } catch (NumberFormatException e) {
                            Valores[lineaNum][columnaNum] = 512;
                        }
                        columnaNum++;
                    }
                }
                primeraColumna = true;
                lineaNum++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void agregarValor(int valor, int fila, int columna) {
        Valores[fila][columna] = valor;
    }

    /**
     * Devuelve la columna de la tabla para un tokenClass dado.
     * Retorna -1 si el token no está mapeado (centinela de error).
     */
    public static int clasificarTransicion(int c) {
        if (map.containsKey(c)) {
            return map.get(c);
        }
        if (c >= -67 && c <= -60) {
            return 4;
        }
        return -1;
    }

    public static int getValor(int fila, int columna) {
        if (fila < 0 || fila >= 100 || columna < 0 || columna >= 100) {
            return 512;
        }
        return Valores[fila][columna];
    }
}
