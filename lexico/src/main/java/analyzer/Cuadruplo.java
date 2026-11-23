package analyzer;

/**
 * Un renglón de la tabla de cuádruplos (simulación de ensamblador).
 *
 * Columnas fijas: E (etiqueta), A (acción / operación), V1, V2, R (resultado).
 *
 * Las etiquetas se guardan como referencias ({@link Etiqueta}) y se les pone
 * nombre al terminar la generación (backpatching), para que:
 *  - el JF del último elseif / case pueda apuntar directo a la salida, y
 *  - los números salgan seguidos, sin huecos, en el orden en que aparecen.
 */
public class Cuadruplo {

    /**
     * Etiqueta de salto (if-E1, Wil-E2, For-E3...). Puede volverse "alias" de otra
     * (el último JF de un if/elseif sin else se une a la etiqueta de salida), así
     * nunca quedan dos etiquetas pegadas que signifiquen lo mismo.
     */
    public static final class Etiqueta {
        final String prefijo;
        private Etiqueta alias;
        private String nombre;

        Etiqueta(String prefijo) {
            this.prefijo = prefijo;
        }

        /** Hace que esta etiqueta sea la misma que {@code otra}. */
        void unirCon(Etiqueta otra) {
            Etiqueta a = raiz(), b = otra.raiz();
            if (a != b) {
                a.alias = b;
            }
        }

        Etiqueta raiz() {
            Etiqueta e = this;
            while (e.alias != null) {
                e = e.alias;
            }
            return e;
        }

        String nombre() {
            return raiz().nombre;
        }

        void ponerNombre(String n) {
            raiz().nombre = n;
        }
    }

    private final String accion;
    private final String v1;
    private final String v2;
    private final String resultado;
    private final Etiqueta etiquetaE;   // etiqueta sola en su fila (columna E)
    private final Etiqueta etiquetaR;   // destino de JF / JT / JMP (columna R)
    private final int linea;            // línea del código fuente que lo generó
    private int ambito;                 // ámbito (0 = global; def y for abren uno nuevo)

    private Cuadruplo(Etiqueta etiquetaE, String accion, String v1, String v2,
                      String resultado, Etiqueta etiquetaR, int linea) {
        this.etiquetaE = etiquetaE;
        this.accion = nz(accion);
        this.v1 = nz(v1);
        this.v2 = nz(v2);
        this.resultado = nz(resultado);
        this.etiquetaR = etiquetaR;
        this.linea = linea;
    }

    /** Operación normal: A | V1 | V2 | R. */
    static Cuadruplo operacion(String accion, String v1, String v2, String r, int linea) {
        return new Cuadruplo(null, accion, v1, v2, r, null, linea);
    }

    /** Salto (JF / JT / JMP) cuyo destino va en R. */
    static Cuadruplo salto(String accion, String v1, Etiqueta destino, int linea) {
        return new Cuadruplo(null, accion, v1, "", "", destino, linea);
    }

    /** Fila de etiqueta: solo E, con A, V1, V2 y R vacías. */
    static Cuadruplo etiqueta(Etiqueta e, int linea) {
        return new Cuadruplo(e, "", "", "", "", null, linea);
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    public boolean esEtiqueta() {
        return etiquetaE != null;
    }

    public boolean esSalto() {
        return etiquetaR != null;
    }

    Etiqueta getEtiquetaE() {
        return etiquetaE;
    }

    Etiqueta getEtiquetaR() {
        return etiquetaR;
    }

    public String getE() {
        return etiquetaE == null ? "" : String.valueOf(etiquetaE.nombre());
    }

    public String getA() {
        return accion;
    }

    public String getV1() {
        return v1;
    }

    public String getV2() {
        return v2;
    }

    public String getR() {
        return etiquetaR == null ? resultado : String.valueOf(etiquetaR.nombre());
    }

    public int getLinea() {
        return linea;
    }

    public int getAmbito() {
        return ambito;
    }

    void setAmbito(int ambito) {
        this.ambito = ambito;
    }

    /** {E, A, V1, V2, R}. */
    public String[] comoFila() {
        return new String[] { getE(), getA(), getV1(), getV2(), getR() };
    }

    @Override
    public String toString() {
        return String.format("%-10s| %-12s| %-14s| %-14s| %s", getE(), getA(), getV1(), getV2(), getR());
    }
}
