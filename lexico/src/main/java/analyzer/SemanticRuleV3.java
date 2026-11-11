package analyzer;

import java.util.EnumSet;
import java.util.Set;

import analyzer.TablasComparacion.LeerCSVComparacion;

/**
 * Semántica 3: catálogo de reglas 2001 a 2018 (TABLA_DE_FUNC).
 *
 * Reglas de ENTRADA (2001-2010): validan cada parámetro (_p1, _p2, _p3) de una
 * función nativa. Reglas de SALIDA (2011-2018): validan el retorno de la función.
 *
 * Columnas de la tabla del profesor: Bin, Dec, Oct, Hex, Real, exp, Cadena,
 * Boolean, void, ID CADENA, id. Aquí cada regla guarda:
 *  - los tipos aceptados (constantes o expresiones de ese tipo),
 *  - si acepta un identificador (columna "id") de alguno de esos tipos,
 *  - si SOLO acepta identificadores ("solo id" / "ID CADENA").
 */
public enum SemanticRuleV3 {

    // ---------------- ENTRADAS ----------------
    R2001(2001, false, "Void", false, false),                                      // CLEAR
    R2002(2002, false, "Dec/Real/Exp/Id", true, false, Tipo.DEC, Tipo.REAL, Tipo.EXP),
    R2003(2003, false, "Bin/Dec/Oct/Hex/Real/Exp/Id", true, false,
            Tipo.BIN, Tipo.DEC, Tipo.OCT, Tipo.HEX, Tipo.REAL, Tipo.EXP),
    R2004(2004, false, "Dec/Id", true, false, Tipo.DEC),
    R2005(2005, false, "Cadena/Id", true, false, Tipo.CADENA),
    R2006(2006, false, "Id Cadena", true, true, Tipo.CADENA),
    R2007(2007, false, "Id Bin/Dec/Oct/Hex", true, true, Tipo.BIN, Tipo.DEC, Tipo.OCT, Tipo.HEX),
    R2008(2008, false, "Bin/Dec/Oct/Hex/Real/Exp/Boolean/Id", true, false,
            Tipo.BIN, Tipo.DEC, Tipo.OCT, Tipo.HEX, Tipo.REAL, Tipo.EXP, Tipo.BOOLEAN),
    R2009(2009, false, "Bin/Dec/Oct/Hex/Id", true, false, Tipo.BIN, Tipo.DEC, Tipo.OCT, Tipo.HEX),
    R2010(2010, false, "Real/Exp/Id", true, false, Tipo.REAL, Tipo.EXP),

    // ---------------- SALIDAS ----------------
    R2011(2011, true, "Void", false, false, Tipo.VOID),
    R2012(2012, true, "Exp", false, false, Tipo.EXP),
    R2013(2013, true, "Bin/Dec/Oct/Hex/Real/Exp", false, false,
            Tipo.BIN, Tipo.DEC, Tipo.OCT, Tipo.HEX, Tipo.REAL, Tipo.EXP),
    R2014(2014, true, "Dec", false, false, Tipo.DEC),
    R2015(2015, true, "Cadena", false, false, Tipo.CADENA),
    R2016(2016, true, "Boolean", false, false, Tipo.BOOLEAN),
    R2017(2017, true, "Real/Exp", false, false, Tipo.REAL, Tipo.EXP),
    R2018(2018, true, "Bin/Dec/Oct/Hex", false, false, Tipo.BIN, Tipo.DEC, Tipo.OCT, Tipo.HEX);

    /** Tipos de la tabla de reglas ("Catálogo de Funciones y Tipos de Datos_2"). */
    public enum Tipo {
        BIN(LeerCSVComparacion.T_BIN, "Bin"),
        DEC(LeerCSVComparacion.T_DEC, "Dec"),
        OCT(LeerCSVComparacion.T_OCT, "Oct"),
        HEX(LeerCSVComparacion.T_HEX, "Hex"),
        REAL(LeerCSVComparacion.T_REAL, "Real"),
        EXP(LeerCSVComparacion.T_EXP, "Exp"),
        CADENA(LeerCSVComparacion.T_CADENA, "Cadena"),
        BOOLEAN(LeerCSVComparacion.T_BOOLEAN, "Boolean"),
        VOID(-1, "Void"),
        VARIANT(LeerCSVComparacion.T_VARIANT, "Variant");

        public final int codigo;
        public final String nombre;

        Tipo(int codigo, String nombre) {
            this.codigo = codigo;
            this.nombre = nombre;
        }

        /** Tipo semántico del proyecto (1..10 de LeerCSVComparacion) -> Tipo de la tabla. */
        public static Tipo deCodigo(int codigo) {
            for (Tipo t : values()) {
                if (t.codigo == codigo) {
                    return t;
                }
            }
            if (codigo == LeerCSVComparacion.T_CARACTER) {
                return CADENA;
            }
            return VARIANT;
        }
    }

    public final int codigo;
    public final boolean salida;
    public final String topePila;
    public final boolean aceptaId;
    public final boolean soloId;
    public final Set<Tipo> tipos;

    SemanticRuleV3(int codigo, boolean salida, String topePila, boolean aceptaId, boolean soloId,
            Tipo... tipos) {
        this.codigo = codigo;
        this.salida = salida;
        this.topePila = topePila;
        this.aceptaId = aceptaId;
        this.soloId = soloId;
        this.tipos = tipos.length == 0 ? EnumSet.noneOf(Tipo.class) : EnumSet.of(tipos[0], tipos);
    }

    public static SemanticRuleV3 deCodigo(int codigo) {
        for (SemanticRuleV3 r : values()) {
            if (r.codigo == codigo) {
                return r;
            }
        }
        throw new IllegalArgumentException("Regla de Semántica 3 inexistente: " + codigo);
    }

    /**
     * ¿El valor cumple la regla?
     *
     * @param tipo     tipo real del valor (si es id: el de la tabla de símbolos)
     * @param esId     el valor es un identificador solo
     * @param declarado si es id, si existe en la tabla de símbolos
     */
    public boolean acepta(Tipo tipo, boolean esId, boolean declarado) {
        if (esId) {
            return aceptaId && declarado && tipos.contains(tipo);
        }
        if (soloId) {
            return false;
        }
        return tipos.contains(tipo);
    }
}
