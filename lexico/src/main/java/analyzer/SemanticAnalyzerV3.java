package analyzer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import analyzer.SemanticRuleV3.Tipo;

/**
 * Semántica 3 (ampliación de Semántica 2): reglas 2001-2018 de los parámetros y
 * retornos de las funciones nativas.
 *
 * sintaxis.java detecta cada llamada nativa, resuelve el tipo real de cada argumento
 * (si es un id, desde la tabla de símbolos) y llama a {@link #validarLlamada}. Esta clase:
 *  1. evalúa cada parámetro contra su regla (_p1, _p2, _p3) -> fila A / ERROR + contador Entradas;
 *  2. evalúa la salida con su regla (2011-2018) -> fila + contador Salida. Si algún
 *     parámetro falló, la salida también queda en ERROR (no hay rechazo parcial);
 *  3. nunca detiene el análisis: los errores solo se registran.
 *
 * Tabla principal: Regla | Función | Tope Pila | Valor Real | Fila | Edo | Ambito
 * Contadores:      Función | Entradas | Salida | Aceptados | Errores
 */
public class SemanticAnalyzerV3 {

    public static final String[] ENCABEZADOS = {
        "Regla", "Función", "Tope Pila", "Valor Real", "Fila", "Edo", "Ambito"
    };
    public static final String ACEPTADO = "A";
    public static final String ERROR = "ERROR";
    public static final String CODIGO_ERROR = "Semántica 3";

    /** Un argumento ya resuelto por el analizador sintáctico. */
    public static class Argumento {
        public final String valorReal; // lexema de la constante / id, o temporal de la expresión
        public final Tipo tipo;        // tipo real (el de la tabla de símbolos si es id)
        public final boolean esId;     // el argumento es un identificador solo
        public final boolean declarado;
        public final int linea;

        public Argumento(String valorReal, Tipo tipo, boolean esId, boolean declarado, int linea) {
            this.valorReal = valorReal;
            this.tipo = tipo;
            this.esId = esId;
            this.declarado = declarado;
            this.linea = linea;
        }
    }

    private final List<String[]> filas = new ArrayList<>();
    private final List<ErrorEntry> errores = new ArrayList<>();
    private final CountersMap contadores = new CountersMap();

    public void limpiar() {
        filas.clear();
        errores.clear();
        contadores.limpiar();
    }

    /**
     * Valida una llamada a función nativa.
     *
     * @return true si todos los parámetros y la salida quedaron aceptados
     */
    public boolean validarLlamada(FuncionNativa f, List<Argumento> args, int linea, int ambito) {
        boolean todosOk = true;

        if (f.parametros.length == 0) {
            // 2001: CLEAR no recibe parámetros (void).
            boolean ok = args == null || args.isEmpty();
            registrar(FuncionNativa.REGLA_SIN_PARAMETROS, f.nombre,
                    ok ? Tipo.VOID.nombre : args.get(0).valorReal, linea, ok, ambito,
                    f.nombre + " no recibe parámetros");
            contadores.contarEntrada(f.nombre, ok);
            todosOk = ok;
        } else {
            int n = Math.max(f.parametros.length, args == null ? 0 : args.size());
            for (int i = 0; i < n; i++) {
                Argumento a = (args != null && i < args.size()) ? args.get(i) : null;
                int lineaArg = a != null ? a.linea : linea;
                if (i >= f.parametros.length) {
                    // argumento sobrante: no hay regla para él (la sintaxis ya lo rechaza)
                    String clave = f.nombreParametro(i + 1);
                    SemanticRuleV3 ultima = f.parametros[f.parametros.length - 1];
                    registrar(ultima, clave, a.valorReal, lineaArg, false, ambito,
                            f.nombre + " solo recibe " + f.parametros.length + " parámetro(s)");
                    contadores.contarEntrada(clave, false);
                    todosOk = false;
                    continue;
                }
                SemanticRuleV3 regla = f.parametros[i];
                String clave = f.nombreParametro(i + 1);
                boolean ok;
                String motivo;
                if (a == null) {
                    ok = false;
                    motivo = "falta el parámetro " + (i + 1) + " de " + f.nombre;
                } else {
                    ok = regla.acepta(a.tipo, a.esId, a.declarado);
                    motivo = motivoRechazo(regla, a);
                }
                registrar(regla, clave, a == null ? "-" : a.valorReal, lineaArg, ok, ambito,
                        clave + ": " + motivo);
                contadores.contarEntrada(clave, ok);
                todosOk &= ok;
            }
        }

        // Salida: tipo máximo de retorno; si algún parámetro falló, la función completa falla.
        SemanticRuleV3 rs = f.reglaSalida;
        boolean okSalida = todosOk && rs.tipos.contains(f.retorno);
        registrar(rs, f.nombre, f.retorno.nombre, linea, okSalida, ambito,
                f.nombre + ": la función se rechaza porque uno de sus parámetros no es válido");
        contadores.contarSalida(f.nombre, okSalida);
        return okSalida;
    }

    private static String motivoRechazo(SemanticRuleV3 regla, Argumento a) {
        if (a.esId && !a.declarado) {
            return "el identificador " + a.valorReal + " no está declarado (se esperaba " + regla.topePila + ")";
        }
        if (!a.esId && regla.soloId) {
            return "solo se acepta un identificador (" + regla.topePila + "), se recibió " + a.valorReal
                    + " (" + a.tipo.nombre + ")";
        }
        return "se esperaba " + regla.topePila + " y se recibió " + a.valorReal
                + (a.esId ? " (id " + a.tipo.nombre + ")" : " (" + a.tipo.nombre + ")");
    }

    private void registrar(SemanticRuleV3 regla, String funcion, String valorReal, int linea,
            boolean ok, int ambito, String motivo) {
        filas.add(new String[] {
            String.valueOf(regla.codigo), funcion, regla.topePila, valorReal,
            String.valueOf(linea), ok ? ACEPTADO : ERROR, String.valueOf(ambito)
        });
        if (!ok) {
            errores.add(new ErrorEntry(CODIGO_ERROR,
                    "Semántica 3 (" + regla.codigo + "): " + motivo,
                    linea, "parser", ErrorEntry.Tipo.SEMANTICA, valorReal, ambito));
            System.out.println("[SEM3-ERR] ln=" + linea + " | " + regla.codigo + " | " + motivo);
        }
    }

    // ---------------- Consultas ----------------

    /** Filas de la tabla principal: {Regla, Función, Tope Pila, Valor Real, Fila, Edo, Ambito}. */
    public List<String[]> filas() {
        return new ArrayList<>(filas);
    }

    public List<ErrorEntry> errores() {
        return new ArrayList<>(errores);
    }

    public CountersMap contadores() {
        return contadores;
    }

    public int cantidad() {
        return filas.size();
    }

    /** Ambas tablas en texto, columnas separadas por " | ". */
    public String generarTabla() {
        StringBuilder sb = new StringBuilder();
        sb.append("TABLA SEMÁNTICA 3\n");
        tabla(sb, ENCABEZADOS, filas);
        sb.append("\nCONTADORES SEMÁNTICA 3\n");
        tabla(sb, CountersMap.ENCABEZADOS, contadores.filas());
        return sb.toString();
    }

    private static void tabla(StringBuilder sb, String[] enc, List<String[]> datos) {
        int[] ancho = new int[enc.length];
        for (int i = 0; i < enc.length; i++) ancho[i] = enc[i].length();
        for (String[] f : datos) {
            for (int i = 0; i < enc.length; i++) ancho[i] = Math.max(ancho[i], f[i].length());
        }
        linea(sb, enc, ancho);
        for (String[] f : datos) linea(sb, f, ancho);
    }

    private static void linea(StringBuilder sb, String[] cols, int[] ancho) {
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) sb.append(" | ");
            sb.append(i == cols.length - 1 ? cols[i] : String.format("%-" + ancho[i] + "s", cols[i]));
        }
        sb.append('\n');
    }

    /** Escribe generarTabla() en un TXT. Devuelve la ruta absoluta o null si falló. */
    public String guardarArchivo(String nombre) {
        try {
            Path p = Paths.get(nombre);
            Files.writeString(p, generarTabla(), StandardCharsets.UTF_8);
            return p.toAbsolutePath().toString();
        } catch (IOException e) {
            System.err.println("[SEM3] No se pudo escribir " + nombre + ": " + e.getMessage());
            return null;
        }
    }
}
