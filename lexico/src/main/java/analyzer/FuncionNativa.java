package analyzer;

import analyzer.SemanticRuleV3.Tipo;

/**
 * Semántica 3: funciones nativas del lenguaje con la regla de cada parámetro,
 * su regla de salida y el tipo que retornan.
 *
 * Cuando el retorno depende del tipo de entrada (Variant) se toma el tipo MÁXIMO:
 * pow, pred y succ -> exp; inc y dec -> dec.
 */
public enum FuncionNativa {
    //      nombre    token  salida                 retorno       parámetros (_p1, _p2, _p3)
    CLEAR ("CLEAR",   -75, SemanticRuleV3.R2011, Tipo.VOID),
    SQRT  ("sqrt",    -76, SemanticRuleV3.R2012, Tipo.EXP,     SemanticRuleV3.R2002),
    POW   ("pow",     -77, SemanticRuleV3.R2013, Tipo.EXP,     SemanticRuleV3.R2003, SemanticRuleV3.R2004),
    SQRTV ("sqrtv",   -78, SemanticRuleV3.R2012, Tipo.EXP,     SemanticRuleV3.R2002, SemanticRuleV3.R2004),
    STRLEN("strlen",  -79, SemanticRuleV3.R2014, Tipo.DEC,     SemanticRuleV3.R2005),
    CONCAT("concat",  -80, SemanticRuleV3.R2015, Tipo.CADENA,  SemanticRuleV3.R2005, SemanticRuleV3.R2005),
    COPY  ("copy",    -81, SemanticRuleV3.R2011, Tipo.VOID,    SemanticRuleV3.R2006, SemanticRuleV3.R2005),
    VAL   ("val",     -82, SemanticRuleV3.R2011, Tipo.VOID,
            SemanticRuleV3.R2007, SemanticRuleV3.R2007, SemanticRuleV3.R2004),
    STR   ("str",     -83, SemanticRuleV3.R2016, Tipo.BOOLEAN, SemanticRuleV3.R2008, SemanticRuleV3.R2006),
    SIN   ("sin",     -84, SemanticRuleV3.R2012, Tipo.EXP,     SemanticRuleV3.R2002),
    COS   ("cos",     -85, SemanticRuleV3.R2012, Tipo.EXP,     SemanticRuleV3.R2002),
    TAN   ("tan",     -86, SemanticRuleV3.R2012, Tipo.EXP,     SemanticRuleV3.R2002),
    CHR   ("chr",     -87, SemanticRuleV3.R2015, Tipo.CADENA,  SemanticRuleV3.R2009),
    PRED  ("pred",    -88, SemanticRuleV3.R2017, Tipo.EXP,     SemanticRuleV3.R2010),
    SUCC  ("succ",    -89, SemanticRuleV3.R2017, Tipo.EXP,     SemanticRuleV3.R2010),
    INC   ("inc",     -90, SemanticRuleV3.R2018, Tipo.DEC,     SemanticRuleV3.R2009),
    DEC   ("dec",     -91, SemanticRuleV3.R2018, Tipo.DEC,     SemanticRuleV3.R2009),
    SQR   ("sqr",     -92, SemanticRuleV3.R2012, Tipo.EXP,     SemanticRuleV3.R2002);

    /** Regla de entrada de una función sin parámetros (CLEAR). */
    public static final SemanticRuleV3 REGLA_SIN_PARAMETROS = SemanticRuleV3.R2001;

    public final String nombre;
    public final int token;
    public final SemanticRuleV3 reglaSalida;
    public final Tipo retorno;
    public final SemanticRuleV3[] parametros;

    FuncionNativa(String nombre, int token, SemanticRuleV3 reglaSalida, Tipo retorno,
            SemanticRuleV3... parametros) {
        this.nombre = nombre;
        this.token = token;
        this.reglaSalida = reglaSalida;
        this.retorno = retorno;
        this.parametros = parametros;
    }

    /** pow_p1, pow_p2... (i empieza en 1). */
    public String nombreParametro(int i) {
        return nombre + "_p" + i;
    }

    /** Función nativa del token (clase -75..-92), o null. */
    public static FuncionNativa deToken(int tokenClass) {
        for (FuncionNativa f : values()) {
            if (f.token == tokenClass) {
                return f;
            }
        }
        return null;
    }

    /** Por nombre, sin importar mayúsculas; "char" se identifica como chr. */
    public static FuncionNativa deNombre(String nombre) {
        if (nombre == null) {
            return null;
        }
        String n = nombre.trim();
        if (n.equalsIgnoreCase("char")) {
            return CHR;
        }
        for (FuncionNativa f : values()) {
            if (f.nombre.equalsIgnoreCase(n)) {
                return f;
            }
        }
        return null;
    }
}
