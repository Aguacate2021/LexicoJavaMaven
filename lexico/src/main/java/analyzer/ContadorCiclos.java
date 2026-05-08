package analyzer;

public class ContadorCiclos {

    public static int ERRORES             = 0;
    public static int PROGRAMA            = 0;
    public static int LISTA_DE_PARAMETROS = 0;
    public static int EXP_PAS             = 0;
    public static int CONSTANTESSIGNO     = 0;
    public static int CONSTNUMERICA       = 0;
    public static int OR                  = 0;
    public static int AND                 = 0;
    public static int DECLARACIONCONSTANTES = 0;
    public static int FACTOR              = 0;
    public static int ELEVACION           = 0;
    public static int TERMINOPASCAL       = 0;
    public static int SimpleExpPascal     = 0;
    public static int FUNCION             = 0;
    public static int ASIG                = 0;
    public static int ARR                 = 0;
    public static int STATU               = 0;

    public static void aumentarContador(int noTerminal) {
        switch (noTerminal) {
            case 0  -> PROGRAMA++;
            case 9  -> CONSTNUMERICA++;
            case 10 -> CONSTANTESSIGNO++;
            case 11 -> DECLARACIONCONSTANTES++;
            case 12 -> LISTA_DE_PARAMETROS++;
            case 14 -> ASIG++;
            case 15 -> FACTOR++;
            case 21 -> FUNCION++;
            case 22 -> ARR++;
            case 24 -> STATU++;
            case 35 -> OR++;
            case 37 -> AND++;
            case 39 -> EXP_PAS++;
            case 41 -> SimpleExpPascal++;
            case 43 -> TERMINOPASCAL++;
            case 45 -> ELEVACION++;
        }
    }

    public static void resetearContadores() {
        ERRORES              = 0;
        PROGRAMA             = 0;
        LISTA_DE_PARAMETROS  = 0;
        EXP_PAS              = 0;
        CONSTANTESSIGNO      = 0;
        CONSTNUMERICA        = 0;
        OR                   = 0;
        AND                  = 0;
        DECLARACIONCONSTANTES = 0;
        FACTOR               = 0;
        ELEVACION            = 0;
        TERMINOPASCAL        = 0;
        SimpleExpPascal      = 0;
        FUNCION              = 0;
        ASIG                 = 0;
        ARR                  = 0;
        STATU                = 0;
    }
}
