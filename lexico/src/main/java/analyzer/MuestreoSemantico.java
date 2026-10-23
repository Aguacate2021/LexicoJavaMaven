package analyzer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Avance 1 de Semántica 2: archivo de muestreo de las reglas 1 a 5.
 *
 * Esta clase SOLO almacena y escribe información de muestreo. No valida
 * nada: quien la usa (sintaxis.java) registra un renglón cada vez que el
 * análisis pasa por el punto donde se activa una regla.
 *
 * Claves de regla:
 *   1010 IF | 1011 ELSEIF | 1012 WHILE | 1013 DO-WHILE
 *   1020 asignación básica | 1021 asignación += | 1022 asignaciones compuestas
 *   1030 AR-DIM | 1040 AR-ENT | 1050 AR-RANG
 *
 * Columnas: Línea | Regla | Descripción | Situación de la regla | Lexema | Ámbito
 */
public class MuestreoSemantico {

    public static final String[] ENCABEZADOS = {
        "Línea", "Regla", "Descripción", "Situación de la regla", "Lexema", "Ámbito"
    };

    private static final String SEPARADOR = " | ";

    private static class Registro {
        final int linea;
        final int regla;
        final String descripcion;
        final String situacion;
        final String lexema;
        final int ambito;

        Registro(int linea, int regla, String descripcion,
                 String situacion, String lexema, int ambito) {
            this.linea = linea;
            this.regla = regla;
            this.descripcion = descripcion;
            this.situacion = situacion;
            this.lexema = lexema;
            this.ambito = ambito;
        }

        String[] columnas() {
            return new String[] {
                String.valueOf(linea), String.valueOf(regla), descripcion,
                situacion, lexema, String.valueOf(ambito)
            };
        }
    }

    private final List<Registro> registros = new ArrayList<>();

    // Evita registrar dos veces el mismo evento del análisis.
    private final Set<String> clavesRegistradas = new HashSet<>();

    public void limpiar() {
        registros.clear();
        clavesRegistradas.clear();
    }

    public int cantidad() {
        return registros.size();
    }

    /**
     * Agrega un renglón de muestreo.
     *
     * @param posicion identifica el evento dentro de la línea (columna del
     *                 token que lo originó, y el número de índice cuando
     *                 aplica). Dos eventos distintos con el mismo lexema en
     *                 la misma línea no se consideran duplicados; el mismo
     *                 evento procesado dos veces sí.
     * @return true si se agregó, false si era un duplicado
     */
    public boolean registrar(int linea, int regla, String descripcion,
                             String situacion, String lexema,
                             int ambito, int posicion) {

        String lex = (lexema == null || lexema.isBlank()) ? "(vacío)" : lexema.trim();
        String clave = linea + "|" + regla + "|" + lex + "|" + ambito + "|" + posicion;

        if (!clavesRegistradas.add(clave)) {
            return false;
        }

        registros.add(new Registro(linea, regla, descripcion, situacion, lex, ambito));
        return true;
    }

    /** Tabla de texto con columnas alineadas, ordenada por línea (orden estable). */
    public String generarTabla() {

        List<Registro> ordenados = new ArrayList<>(registros);
        ordenados.sort((a, b) -> Integer.compare(a.linea, b.linea));

        int[] anchos = new int[ENCABEZADOS.length];
        for (int i = 0; i < anchos.length; i++) {
            anchos[i] = ENCABEZADOS[i].length();
        }
        for (Registro r : ordenados) {
            String[] c = r.columnas();
            for (int i = 0; i < c.length; i++) {
                anchos[i] = Math.max(anchos[i], c[i].length());
            }
        }

        StringBuilder sb = new StringBuilder();
        agregarFila(sb, ENCABEZADOS, anchos);
        for (Registro r : ordenados) {
            agregarFila(sb, r.columnas(), anchos);
        }
        return sb.toString();
    }

    private static void agregarFila(StringBuilder sb, String[] celdas, int[] anchos) {
        for (int i = 0; i < celdas.length; i++) {
            sb.append(celdas[i]);
            if (i < celdas.length - 1) {
                for (int k = celdas[i].length(); k < anchos[i]; k++) {
                    sb.append(' ');
                }
                sb.append(SEPARADOR);
            }
        }
        sb.append(System.lineSeparator());
    }

    /** Escribe el TXT (UTF-8). Devuelve la ruta absoluta, o null si falló. */
    public String guardarArchivo(String ruta) {
        try {
            Path destino = Paths.get(ruta).toAbsolutePath();
            Files.writeString(destino, generarTabla(), StandardCharsets.UTF_8);
            return destino.toString();
        } catch (IOException e) {
            System.err.println("[MUESTREO] No se pudo escribir " + ruta);
            e.printStackTrace();
            return null;
        }
    }
}