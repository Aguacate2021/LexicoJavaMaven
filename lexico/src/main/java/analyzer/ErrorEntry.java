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
            case 512 -> "Comentario sin cerrar";
            default -> "Error léxico desconocido";
        };
        return desc;
    }
     public static String definirDescripcionSintaxis(int numError) {
    String desc = switch (numError) {

        case 512 -> "Error en la estructura principal del programa";
        case 513 -> "Se esperaba inicio válido en la producción Y1";
        case 514 -> "Sintaxis inválida en la producción Y2";
        case 515 -> "Falta elemento requerido en Y3";
        case 516 -> "Expresión incorrecta en Y4";
        case 517 -> "Se encontró token inesperado en Y5";
        case 518 -> "Error de sintaxis en la producción Y6";
        case 519 -> "Estructura inválida en Y7";
        case 520 -> "Finalización incorrecta de la producción Y8";

        case 521 -> "Constante numérica inválida o mal formada";
        case 522 -> "Se esperaba signo o constante válida";
        case 523 -> "Error en la declaración de constantes";
        case 524 -> "Lista de parámetros mal definida";

        case 525 -> "Error sintáctico en la producción B1";
        case 526 -> "Asignación inválida o incompleta";
        case 527 -> "Factor inválido dentro de la expresión";

        case 528 -> "Error de sintaxis en X1";
        case 529 -> "Token inesperado en X2";
        case 530 -> "Expresión inválida en X3";
        case 531 -> "Error de estructura en X4";
        case 532 -> "Producción X5 incompleta o inválida";

        case 533 -> "Declaración o llamada de función incorrecta";
        case 534 -> "Error en acceso o declaración de arreglo";
        case 535 -> "Error sintáctico en A1";

        case 536 -> "Sentencia inválida en STATU";

        case 537 -> "Error de sintaxis en N";
        case 538 -> "Error de sintaxis en O";
        case 539 -> "Error de sintaxis en P";
        case 540 -> "Error de sintaxis en Q";
        case 541 -> "Error de sintaxis en R";
        case 542 -> "Error de sintaxis en S";
        case 543 -> "Error de sintaxis en T";
        case 544 -> "Error de sintaxis en U";
        case 545 -> "Error de sintaxis en V";
        case 546 -> "Error de sintaxis en W";

        case 547 -> "Operador lógico OR mal utilizado";
        case 548 -> "Error sintáctico en D1";
        case 549 -> "Operador lógico AND mal utilizado";
        case 550 -> "Error sintáctico en E1";

        case 551 -> "Expresión Pascal inválida";
        case 552 -> "Error de sintaxis en F1";
        case 553 -> "Expresión simple Pascal incorrecta";
        case 554 -> "Error sintáctico en L1";
        case 555 -> "Término Pascal inválido o incompleto";
        case 556 -> "Error sintáctico en K1";
        case 557 -> "Error en operación de elevación o potencia";
        case 558 -> "Error sintáctico en C1";

        default -> "Error sintáctico desconocido";
    };

    return desc;
}
}