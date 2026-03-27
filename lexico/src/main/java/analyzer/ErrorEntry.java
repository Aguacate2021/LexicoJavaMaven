package analyzer;

/**
 * Modelo de datos: representa un error detectado durante el análisis léxico.
 */
public class ErrorEntry {

    public enum Tipo { LEXICO, SINTAXIS }

    private final String    codigo;
    private final String    descripcion;
    private final int       linea;
    private final String    archivo;
    private final Tipo      tipo;
    private final String    lexema;

    public ErrorEntry(String codigo, String descripcion, int linea,
                      String archivo, Tipo tipo, String lexema) {
        this.codigo      = codigo;
        this.descripcion = descripcion;
        this.linea       = linea;
        this.archivo     = archivo;
        this.tipo          = tipo;
        this.lexema         = lexema;
    }

    /** Constructor con severidad ERROR por defecto. */
    public ErrorEntry(String codigo, String descripcion, int linea, String archivo,String lexema) {
        this(codigo, descripcion, linea, archivo, Tipo.LEXICO, lexema);
    }

    public String    getCodigo()      { return codigo;      }
    public String    getDescripcion() { return descripcion; }
    public int       getLinea()       { return linea;       }
    public String    getArchivo()     { return archivo;     }
    public Tipo      getTipo()        { return tipo;        }
    public String    getLexema()      { return lexema;      }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s (ln %d, %s)", tipo, codigo, descripcion, linea, archivo);
    }

    public static String definirDescripcion(int numError) {
        String desc = switch (numError) {
            case 500 -> "Se esperaba un número";
            case 501 -> "Se esperaba un numero o un + o -";
            case 502 -> "Se esperaba un numero binario";
            case 503 -> "Se esperaba un numero octal";
            case 504 -> "Se esperaba un numero Hexadecimal";
            case 505 -> "Se esperaba un numero Letra o guion bajo";
            case 506 -> "Se esperaba una B D O X";
            case 507 -> "Salto de linea en cadena";
            case 508 -> "Carácter inseperado";
            case 509 -> "Identificador inválido";
            case 510 -> "Se esperaba una letra despues del punto";
            case 511 -> "Palabra reservada no reconocida";
            default -> "Error léxico desconocido";
        };
        return desc;
    }
}