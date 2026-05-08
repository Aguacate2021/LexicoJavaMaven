package analyzer;


public class ContadorCiclos {
       public static int ERRORES = 0;
       public static int PROGRAMA = 20;
       public static int LISTA_DE_PARAMETROS = 0;
       public static int EXP_PAS = 0;
       public static int CONSTANTESSIGNO = 0;
       public static int CONSTNUMERICA = 0;
       public static int OR = 0;
       public static int AND = 0;
       public static int DECLARACIONCONSTANTES = 0;
       public static int FACTOR = 0;
       public static int ELEVACION = 0;
       public static int TERMINOPASCAL = 0;
       public static int SimpleExpPascal = 0;
       public static int FUNCION = 0;
       public static int ASIG = 0;
       public static int ARR = 0;
       public static int STATU = 0;
       

      public static void aumentarContador(int produccion) {

        switch (produccion) {
            case 0:
                PROGRAMA++;
                break;
            case 24:
                STATU++;
                break;
            case 9:
                CONSTNUMERICA++;
                break;

            case 10:
                CONSTANTESSIGNO++;
                break;

            case 11:
                DECLARACIONCONSTANTES++;
                break;

            case 12:
                LISTA_DE_PARAMETROS++;
                break;

            case 14:
                ASIG++;
                break;

            case 15:
                FACTOR++;
                break;

            case 21:
                FUNCION++;
                break;

            case 22:
                ARR++;
                break;

            case 35:
                OR++;
                break;

            case 37:
                AND++;
                break;

            case 39:
                EXP_PAS++;
                break;

            case 41:
                SimpleExpPascal++;
                break;

            case 43:
                TERMINOPASCAL++;
                break;

            case 45:
                ELEVACION++;
                break;
        }

        
    }


      public static void resetearContadores() {
            ERRORES = 0;
            PROGRAMA = 20;
            LISTA_DE_PARAMETROS = 0;
            EXP_PAS = 0;
            CONSTANTESSIGNO = 0;
            CONSTNUMERICA = 0;
            OR = 0;
            AND = 0;
            DECLARACIONCONSTANTES = 0;
            FACTOR = 0;
            ELEVACION = 0;
            TERMINOPASCAL = 0;
            SimpleExpPascal = 0;
            FUNCION = 0;
            ASIG = 0;
            ARR = 0;
            STATU = 0;
      }
      

}