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
 *  2. registra la salida con su regla (2011-2018) y el tipo que retorna ESTA llamada
 *     (pow(#Dx,2) -> Dec, pred($r) -> Real; el máximo si p1 es inválido) -> fila +
 *     contador Salida. La salida siempre se registra con su tipo aunque un parámetro
 *     haya fallado: el error queda en la fila del parámetro (como la referencia y la prueba);
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

    /** Igual que {@link #validarLlamada(FuncionNativa, List, Tipo, int, int)} con el retorno máximo. */
    public boolean validarLlamada(FuncionNativa f, List<Argumento> args, int linea, int ambito) {
        return validarLlamada(f, args, f.retorno, linea, ambito);
    }

    /**
     * Valida una llamada a función nativa.
     *
     * @param retorno tipo que retorna esta llamada (depende de p1 en pow, pred, succ, inc, dec)
     * @return true si todos los parámetros quedaron aceptados
     */
    public boolean validarLlamada(FuncionNativa f, List<Argumento> args, Tipo retorno, int linea, int ambito) {
        boolean todosOk = true;
        if (f.parametros.length == 0) {
            todosOk = validarSinParametros(f, args, linea, ambito);
        } else {
            int recibidos = args == null ? 0 : args.size();
            for (int i = 0; i < recibidos; i++) {
                todosOk &= validarParametro(f, i, args.get(i), linea, ambito);
            }
            todosOk &= validarFaltantes(f, recibidos, linea, ambito);
        }
        registrarSalida(f, retorno, linea, ambito);
        return todosOk;
    }

    /** 2001: CLEAR no recibe parámetros. En los contadores CLEAR solo cuenta su salida. */
    public boolean validarSinParametros(FuncionNativa f, List<Argumento> args, int linea, int ambito) {
        boolean ok = args == null || args.isEmpty();
        registrar(FuncionNativa.REGLA_SIN_PARAMETROS, f.nombre,
                ok ? "-" : args.get(0).valorReal, linea, ok, ambito,
                f.nombre + " no recibe parámetros");
        if (!ok) {
            contadores.contarEntrada(f.nombre, false);
        }
        return ok;
    }

    /**
     * Parámetro i (desde 0) de la llamada: fila de su regla (_p1, _p2...) + contador Entradas.
     * Se llama en cuanto el argumento se completa (en su coma), en el orden del código fuente.
     */
    public boolean validarParametro(FuncionNativa f, int i, Argumento a, int linea, int ambito) {
        int lineaArg = a != null ? a.linea : linea;
        String clave = f.nombreParametro(i + 1);        // contadores T2: pow_p1
        String fila = f.nombre + "_P" + (i + 1);          // tabla T1: pow_P1 (como la plantilla del profesor)
        if (i >= f.parametros.length) {
            // argumento sobrante: no hay regla para él (la sintaxis ya lo rechaza)
            SemanticRuleV3 ultima = f.parametros[f.parametros.length - 1];
            registrar(ultima, fila, a == null ? "-" : a.valorReal, lineaArg, false, ambito,
                    f.nombre + " solo recibe " + f.parametros.length + " parámetro(s)");
            contadores.contarEntrada(clave, false);
            return false;
        }
        SemanticRuleV3 regla = f.parametros[i];
        boolean ok = a != null && regla.acepta(a.tipo, a.esId, a.declarado);
        String motivo = a == null ? "falta el parámetro " + (i + 1) + " de " + f.nombre : motivoRechazo(regla, a);
        registrar(regla, fila, a == null ? "-" : a.valorReal, lineaArg, ok, ambito, clave + ": " + motivo);
        contadores.contarEntrada(clave, ok);
        return ok;
    }

    /** Parámetros declarados que no recibieron argumento (la sintaxis normalmente lo impide). */
    public boolean validarFaltantes(FuncionNativa f, int recibidos, int linea, int ambito) {
        boolean ok = true;
        for (int i = recibidos; i < f.parametros.length; i++) {
            ok &= validarParametro(f, i, null, linea, ambito);
        }
        return ok;
    }

    /**
     * Salida: siempre se registra con el tipo que retorna la llamada, aunque un parámetro haya
     * fallado. Solo falla si ese tipo no está en la tabla de su regla (no ocurre con el catálogo).
     */
    public void registrarSalida(FuncionNativa f, Tipo retorno, int linea, int ambito) {
        SemanticRuleV3 rs = f.reglaSalida;
        Tipo tipoSalida = retorno != null ? retorno : f.retorno;
        boolean okSalida = rs.tipos.contains(tipoSalida);
        registrar(rs, f.nombre, tipoSalida.nombre, linea, okSalida, ambito,
                f.nombre + ": el tipo de retorno " + tipoSalida.nombre + " no está en la regla " + rs.codigo);
        contadores.contarSalida(f.nombre, okSalida);
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
