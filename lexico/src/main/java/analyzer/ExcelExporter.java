package analyzer;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * ExcelExporter — Exporta el análisis léxico a un archivo .xlsx.
 *
 * Hojas generadas:
 *   1. TOKENS      — Estado | Lexema | Línea
 *   2. ERRORES     — Token  | Descripción | Lexema | Tipo | Línea
 *   3. CONTADORES  — Tabla de doble encabezado (categoría + subcategoría)
 *                    alimentada directamente por ContadorTokens.
 *
 * Uso desde AnalyzerIDE:
 *   ContadorTokens ct = new ContadorTokens();
 *   ct.contar(tokens);
 *   ExcelExporter.exportar(frame, tokens, errores, ct);
 *
 *
 * Dependencia: Apache POI (poi-ooxml).
 */
public class ExcelExporter {

    // ── Paleta ────────────────────────────────────────────────────────────
    private static final XSSFColor COLOR_HDR_BG   = rgb(0x2D, 0x2D, 0x2D);
    private static final XSSFColor COLOR_HDR_FG   = rgb(0xD4, 0xD4, 0xD4);
    private static final XSSFColor COLOR_ROW_EVEN = rgb(0x2A, 0x2A, 0x2A);
    private static final XSSFColor COLOR_ROW_ODD  = rgb(0x22, 0x22, 0x22);
    private static final XSSFColor COLOR_KEYWORD  = rgb(0x56, 0x9C, 0xD6);
    private static final XSSFColor COLOR_ERROR    = rgb(0xF4, 0x47, 0x47);
    private static final XSSFColor COLOR_WARN     = rgb(0xE5, 0xC0, 0x7B);
    private static final XSSFColor COLOR_COUNT_V  = rgb(0xB5, 0xCE, 0xA8);
    private static final XSSFColor COLOR_COUNT_H  = rgb(0x3A, 0x3A, 0x3A);

    // Colores de categoría (encabezado superior de CONTADORES)
    private static final XSSFColor CAT_ID  = rgb(0x26, 0x40, 0x6E);
    private static final XSSFColor CAT_COM = rgb(0x1D, 0x4D, 0x2E);
    private static final XSSFColor CAT_KW  = rgb(0x4A, 0x2D, 0x6A);
    private static final XSSFColor CAT_CST = rgb(0x5C, 0x3A, 0x1E);
    private static final XSSFColor CAT_OP  = rgb(0x5C, 0x1E, 0x1E);

    // ═══════════════════════════════════════════════════════════════════════
    // PUNTOS DE ENTRADA PÚBLICOS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Exporta usando un ContadorTokens ya calculado (recomendado).
     */
    public static void exportar(java.awt.Component parent,
                                List<Token>       tokens,
                                List<ErrorEntry>  errores,
                                ContadorTokens    contador) {
        exportar(parent, tokens, errores, contador, null);
    }

    /**
     * Igual que el anterior, pero además agrega la hoja "SEMANTICA 1" con las
     * estadísticas que produjo sintaxis (getEstadisticasSemantica()).
     */
    public static void exportar(java.awt.Component parent,
                                List<Token>       tokens,
                                List<ErrorEntry>  errores,
                                ContadorTokens    contador,
                                Map<Integer, int[]> estadisticasSem) {
        File destino = elegirDestino(parent);
        if (destino == null) return;

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            poblarTokens(wb, tokens);
            poblarErrores(wb, errores);
            poblarContadores(wb, errores.size(), contador);
            poblarContadoresSintaxis(wb);
            poblarAmbitos(wb, errores);
            poblarTablaSimbolos(wb);
            poblarSemantica1(wb, estadisticasSem, errores);

            try (FileOutputStream fos = new FileOutputStream(destino)) {
                wb.write(fos);
            }
            JOptionPane.showMessageDialog(parent,
                    "Exportado correctamente:\n" + destino.getAbsolutePath(),
                    "Excel exportado", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(parent,
                    "Error al exportar:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    
    public static void exportar(java.awt.Component parent,
                                List<Token>       tokens,
                                List<ErrorEntry>  errores) {
        ContadorTokens ct = new ContadorTokens();
        ct.contar(tokens);
        exportar(parent, tokens, errores, ct);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HOJA 1 — TOKENS
    // ═══════════════════════════════════════════════════════════════════════
    private static void poblarTokens(XSSFWorkbook wb, List<Token> tokens) {
        XSSFSheet ws = wb.createSheet("TOKENS");
        ws.setColumnWidth(0, 24 * 256);
        ws.setColumnWidth(1, 30 * 256);
        ws.setColumnWidth(2, 10 * 256);

        String[] hdrs = {"Estado", "Lexema", "Línea"};
        XSSFRow hdrRow = ws.createRow(0);
        hdrRow.setHeightInPoints(18);
        for (int c = 0; c < hdrs.length; c++) {
            XSSFCell cell = hdrRow.createCell(c);
            cell.setCellValue(hdrs[c]);
            cell.setCellStyle(estiloEncabezado(wb));
        }

        int fila = 1;
        for (Token t : tokens) {
            XSSFRow row = ws.createRow(fila);
            row.setHeightInPoints(16);
            boolean par = (fila % 2 == 0);
            celda(row, 0, String.valueOf(t.getEstado()), estiloDato(wb, par, COLOR_KEYWORD));
            celda(row, 1, t.getLexema(),                  estiloDato(wb, par, null));
            celda(row, 2, t.getLinea(),                   estiloDatoNum(wb, par));
            fila++;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HOJA 2 — ERRORES
    // ═══════════════════════════════════════════════════════════════════════
    private static void poblarErrores(XSSFWorkbook wb, List<ErrorEntry> errores) {
        XSSFSheet ws = wb.createSheet("ERRORES");
        ws.setColumnWidth(0, 20 * 256);
        ws.setColumnWidth(1, 40 * 256);
        ws.setColumnWidth(2, 25 * 256);
        ws.setColumnWidth(3, 18 * 256);
        ws.setColumnWidth(4, 10 * 256);

        String[] hdrs = {"Token", "Descripción", "Lexema", "Tipo de error", "Línea"};
        XSSFRow hdrRow = ws.createRow(0);
        hdrRow.setHeightInPoints(18);
        for (int c = 0; c < hdrs.length; c++) {
            XSSFCell cell = hdrRow.createCell(c);
            cell.setCellValue(hdrs[c]);
            cell.setCellStyle(estiloEncabezado(wb));
        }

        int fila = 1;
        for (ErrorEntry e : errores) {
            XSSFRow row = ws.createRow(fila);
            row.setHeightInPoints(16);
            boolean par    = (fila % 2 == 0);
            boolean esWarn = e.getTipo() == ErrorEntry.Tipo.SINTAXIS;
            String  codigo = esWarn ? e.getCodigo().replace("ERR", "WARN") : e.getCodigo();
            XSSFColor colorErr = esWarn ? COLOR_WARN : COLOR_ERROR;

            celda(row, 0, codigo,             estiloDato(wb, par, colorErr));
            celda(row, 1, e.getDescripcion(), estiloDato(wb, par, colorErr));
            celda(row, 2, e.getLexema(),      estiloDato(wb, par, null));
            celda(row, 3, e.getTipo().name(), estiloDato(wb, par, null));
            celda(row, 4, e.getLinea(),       estiloDatoNum(wb, par));
            fila++;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HOJA 3 — CONTADORES  (alimentada por ContadorTokens)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Estructura de columnas en la hoja CONTADORES.
     *
     *  Col  0        → Errores
     *  Col  1–9      → Identificadores  (cadena, binario, decimal, octal, hex, real, exp, bool,registro)
     *  Col 10        → Comentarios línea
     *  Col 11        → Comentarios multilínea
     *  Col 12        → Palabras reservadas
     *  Col 13–21     → Constantes       (cadena, bin, dec, oct, hex, real, exp, bool, null)
     *  Col 22        → Op. Postfix
     *  Col 23        → Op. Lógicos binarios
     *  Col 24        → Op. Control
     *  Col 25        → Op. Matemáticos
     *  Col 26        → Op. Exponente
     *  Col 27        → Op. Turno
     *  Col 28        → Op. Relacionales
     *  Col 29        → Op. Igualdad estricta
     *  Col 30        → Op. Lógicos
     *  Col 31        → Op. Ternario
     *  Col 32        → Op. Asignación
     *  Col 33        → Op. Agrupamiento
     */
    private static void poblarContadores(XSSFWorkbook wb,
                                          int            totalErrores,
                                          ContadorTokens c) {
        XSSFSheet ws = wb.createSheet("CONTADORES");

        // Anchos de columna
        ws.setColumnWidth(0, 10 * 256);   // Errores
        for (int i =  1; i <=  9; i++) ws.setColumnWidth(i, 16 * 256); // Identificadores
        ws.setColumnWidth(10, 16 * 256);  // Com. línea
        ws.setColumnWidth(11, 16 * 256);  // Com. multi
        ws.setColumnWidth(12, 18 * 256);  // Pal. reservadas
        for (int i = 13; i <= 21; i++) ws.setColumnWidth(i, 16 * 256); // Constantes
        for (int i = 22; i <= 33; i++) ws.setColumnWidth(i, 22 * 256); // Operadores

        // ── Fusiones ──────────────────────────────────────────────────────
        ws.addMergedRegion(new CellRangeAddress(0, 1,  0,  0));  // Errores
        ws.addMergedRegion(new CellRangeAddress(0, 0,  1,  9));  // Identificadores
        ws.addMergedRegion(new CellRangeAddress(0, 0,  10, 11));  // Comentarios
        ws.addMergedRegion(new CellRangeAddress(0, 1, 12, 12));  // Pal. reservadas
        ws.addMergedRegion(new CellRangeAddress(0, 0, 13, 21));  // Constantes
        // Operadores: cada subcategoría ocupa 1 columna sin subrow extra
        ws.addMergedRegion(new CellRangeAddress(0, 1, 22, 22));
        ws.addMergedRegion(new CellRangeAddress(0, 1, 23, 23));
        ws.addMergedRegion(new CellRangeAddress(0, 1, 24, 24));
        ws.addMergedRegion(new CellRangeAddress(0, 1, 25, 25));
        ws.addMergedRegion(new CellRangeAddress(0, 1, 26, 26));
        ws.addMergedRegion(new CellRangeAddress(0, 1, 27, 27));
        ws.addMergedRegion(new CellRangeAddress(0, 1, 28, 28));
        ws.addMergedRegion(new CellRangeAddress(0, 1, 29, 29));
        ws.addMergedRegion(new CellRangeAddress(0, 1, 30, 30));
        ws.addMergedRegion(new CellRangeAddress(0, 1, 31, 31));
        ws.addMergedRegion(new CellRangeAddress(0, 1, 32, 32));
        ws.addMergedRegion(new CellRangeAddress(0, 1, 33, 33));

        // ── Fila 0: categorías ────────────────────────────────────────────
        XSSFRow r0 = ws.createRow(0);
        r0.setHeightInPoints(22);

        celda(r0,  0, "Errores",             estiloCategoria(wb, null));
        celda(r0,  1, "IDENTIFICADORES",     estiloCategoria(wb, CAT_ID));
        celda(r0,  10, "COMENTARIOS",         estiloCategoria(wb, CAT_COM));
        celda(r0, 12, "PAL. RESERVADAS",     estiloCategoria(wb, CAT_KW));
        celda(r0, 13, "CONSTANTES",          estiloCategoria(wb, CAT_CST));
        celda(r0, 22, "Postfix",             estiloCategoria(wb, CAT_OP));
        celda(r0, 23, "Log. binarios",       estiloCategoria(wb, CAT_OP));
        celda(r0, 24, "Control",             estiloCategoria(wb, CAT_OP));
        celda(r0, 25, "Matemáticos",         estiloCategoria(wb, CAT_OP));
        celda(r0, 26, "Exponente",           estiloCategoria(wb, CAT_OP));
        celda(r0, 27, "Turno",               estiloCategoria(wb, CAT_OP));
        celda(r0, 28, "Relacionales",        estiloCategoria(wb, CAT_OP));
        celda(r0, 29, "Igualdad estricta",   estiloCategoria(wb, CAT_OP));
        celda(r0, 30, "Lógicos",             estiloCategoria(wb, CAT_OP));
        celda(r0, 31, "Ternario",            estiloCategoria(wb, CAT_OP));
        celda(r0, 32, "Asignación",          estiloCategoria(wb, CAT_OP));
        celda(r0, 33, "Agrupamiento",        estiloCategoria(wb, CAT_OP));

        // ── Fila 1: subcategorías ─────────────────────────────────────────
        XSSFRow r1 = ws.createRow(1);
        r1.setHeightInPoints(18);

        // Identificadores
        String[] subId = {"Cadena", "Binario", "Decimal", "Octal",
                          "Hexadecimal", "Real", "Exponencial", "Booleanas","Registro"};
        for (int i = 0; i < subId.length; i++)
            celda(r1, 1 + i, subId[i], estiloSubcat(wb, CAT_ID));

        // Comentarios
        celda(r1,  10, "Línea",      estiloSubcat(wb, CAT_COM));
        celda(r1, 11, "Multilínea", estiloSubcat(wb, CAT_COM));

        // Constantes
        String[] subCst = {"Cadena", "Binario", "Decimal", "Octal",
                           "Hexadecimal", "Real", "Exponencial", "Booleanas", "Null"};
        for (int i = 0; i < subCst.length; i++)
            celda(r1, 13 + i, subCst[i], estiloSubcat(wb, CAT_CST));

        // ── Fila 2: valores (de ContadorTokens) ──────────────────────────
        XSSFRow r2 = ws.createRow(2);
        r2.setHeightInPoints(18);
        CellStyle sv = estiloValor(wb);

        // Errores
        celda(r2,  0, totalErrores,       sv);

        // Identificadores
        celda(r2,  1, c.idCadena,         sv);
        celda(r2,  2, c.idBinario,        sv);
        celda(r2,  3, c.idDecimal,        sv);
        celda(r2,  4, c.idOctal,          sv);
        celda(r2,  5, c.idHex,            sv);
        celda(r2,  6, c.idReal,           sv);
        celda(r2,  7, c.idExp,            sv);
        celda(r2,  8, c.idBool,           sv);
        celda(r2,  9, c.idRegistro,       sv);

        // Comentarios
        // ContadorTokens.comentarios = total; no distingue línea vs multilínea.
        // Si en el futuro se añaden campos separados, cambiar aquí.
        celda(r2, 10, c.comentarios,      sv);  // línea (total provisionalmente)
        celda(r2, 11, 0,                  sv);  // multilínea (pendiente de desglose)

        // Palabras reservadas
        celda(r2, 12, c.reservadas,       sv);

        // Constantes
        celda(r2, 13, c.cteCadena,        sv);
        celda(r2, 14, c.cteBinario,       sv);
        celda(r2, 15, c.cteDecimal,       sv);
        celda(r2, 16, c.cteOctal,         sv);
        celda(r2, 17, c.cteHex,           sv);
        celda(r2, 18, c.cteReal,          sv);
        celda(r2, 19, c.cteExp,           sv);
        celda(r2, 20, c.cteBool,          sv);
        celda(r2, 21, c.cteNull,          sv);

        // Operadores
        celda(r2, 22, c.opPostfix,        sv);
        celda(r2, 23, c.opLogBin,         sv);
        celda(r2, 24, c.opControl,        sv);
        celda(r2, 25, c.opMat,            sv);
        celda(r2, 26, c.opExp,            sv);
        celda(r2, 27, c.opTurno,          sv);
        celda(r2, 28, c.opRel,            sv);
        celda(r2, 29, c.opIgualdad,       sv);
        celda(r2, 30, c.opLogicos,        sv);
        celda(r2, 31, c.opTernario,       sv);
        celda(r2, 32, c.opAsignacion,     sv);
        celda(r2, 33, c.opAgrup,          sv);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HOJA 5 — AMBITO
    // ═══════════════════════════════════════════════════════════════════════
    private static void poblarAmbitos(XSSFWorkbook wb, List<ErrorEntry> errores) {

        XSSFSheet ws = wb.createSheet("Ambito");

        String[] encabezados = {
            "Ambito", "Bin", "Dec", "Oct", "Hex",
            "Real", "exp", "Cadena", "Boolean", "Errores", "total"
        };

        for (int i = 0; i < encabezados.length; i++) {
            ws.setColumnWidth(i, (i == 0 ? 12 : 14) * 256);
        }

        // Encabezado
        XSSFRow encabezado = ws.createRow(0);
        CellStyle estiloEnc = estiloEncabezado(wb);

        for (int i = 0; i < encabezados.length; i++) {
            celda(encabezado, i, encabezados[i], estiloEnc);
        }

        // Mapa: ambito -> [Bin, Dec, Oct, Hex, Real, exp, Cadena, Boolean, Errores]
        Map<Integer, int[]> porAmbito = new LinkedHashMap<>();

        String sql = """
            SELECT amb, tipo
            FROM tabla_simbolos
            ORDER BY amb, id
            """;

        try (Connection conexion = ConexionBD.conectar();
             PreparedStatement ps = conexion.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int ambito = rs.getInt("amb");
                String tipo = rs.getString("tipo");

                int[] contadores = porAmbito.computeIfAbsent(
                    ambito, k -> new int[9]
                );

                int posicion = posicionTipoAmbito(tipo);

                if (posicion >= 0) {
                    contadores[posicion]++;
                }
            }

        } catch (SQLException e) {
            System.err.println("Error al obtener datos por ámbito.");
            e.printStackTrace();

            JOptionPane.showMessageDialog(
                null,
                "No se pudo generar la hoja Ambito:\n" + e.getMessage(),
                "Error de base de datos",
                JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        // Contabilizar errores reales por ámbito (índice 8 del arreglo)
        if (errores != null) {
            for (ErrorEntry error : errores) {
                int ambitoError = error.getAmbito();

                int[] contadores = porAmbito.computeIfAbsent(
                    ambitoError, k -> new int[9]
                );

                contadores[8]++;
            }
        }

        // Total de ámbitos
        XSSFRow filaInfo = ws.createRow(1);
        celda(filaInfo, 0, "Total de ámbitos", estiloSubcat(wb, CAT_ID));
        celda(filaInfo, 1, porAmbito.size(), estiloValor(wb));

        // Encabezado de la tabla de datos
        XSSFRow filaCabeceraDatos = ws.createRow(3);

        for (int i = 0; i < encabezados.length; i++) {
            celda(
                filaCabeceraDatos,
                i,
                encabezados[i],
                estiloCategoria(wb, CAT_ID)
            );
        }

        int fila = 4;
        int[] totales = new int[9];

        // Orden ascendente por número de ámbito (no necesariamente consecutivo),
        // aun si algún ámbito solo tiene errores y no identificadores.
        java.util.List<Map.Entry<Integer, int[]>> entradasOrdenadas =
            new java.util.ArrayList<>(porAmbito.entrySet());
        entradasOrdenadas.sort(Map.Entry.comparingByKey());

        for (Map.Entry<Integer, int[]> entrada : entradasOrdenadas) {

            int ambito = entrada.getKey();
            int[] c = entrada.getValue();

            XSSFRow row = ws.createRow(fila);
            CellStyle estilo = estiloDatoNum(wb, fila % 2 == 0);

            // Ámbito
            celda(row, 0, ambito, estilo);

            // Bin, Dec, Oct, Hex, Real, exp, Cadena, Boolean
            for (int i = 0; i < 8; i++) {
                celda(row, i + 1, c[i], estilo);
                totales[i] += c[i];
            }

            // Errores
            // Actualmente no se pueden distribuir por ámbito porque
            // ErrorEntry no contiene el ámbito.
            celda(row, 9, c[8], estilo);
            totales[8] += c[8];

            // Total del ámbito
            int total = 0;
            for (int i = 0; i < 8; i++) {
                total += c[i];
            }

            celda(row, 10, total, estilo);

            fila++;
        }

        // Fila Total
        XSSFRow rowTotal = ws.createRow(fila);
        CellStyle estiloTotal = estiloValor(wb);

        celda(rowTotal, 0, "Total", estiloTotal);

        for (int i = 0; i < 9; i++) {
            celda(rowTotal, i + 1, totales[i], estiloTotal);
        }

        int totalGeneral = 0;
        for (int i = 0; i < 9; i++) {
            totalGeneral += totales[i];
        }

        celda(rowTotal, 10, totalGeneral, estiloTotal);

        ws.createFreezePane(0, 4);
    }

    private static int posicionTipoAmbito(String tipo) {

        if (tipo == null) {
            return -1;
        }

        return switch (tipo.trim().toLowerCase()) {
            case "binario", "bin" -> 0;
            case "decimal", "dec" -> 1;
            case "octal", "oct" -> 2;
            case "hexadecimal", "hex" -> 3;
            case "real" -> 4;
            case "exponencial", "exp" -> 5;
            case "cadena" -> 6;
            case "booleanas", "boolean", "bool" -> 7;
            default -> -1;
        };
    }


    // ═══════════════════════════════════════════════════════════════════════
    // HOJA 6 — TABLA DE SIMBOLOS
    // ═══════════════════════════════════════════════════════════════════════
    private static void poblarTablaSimbolos(XSSFWorkbook wb) {

        XSSFSheet ws = wb.createSheet("Tabla de simbolos");

        String[] encabezados = {
            "id", "tipo", "clase", "amb",
            "tarr", "dimarr", "nopar", "tpar"
        };

        int[] anchos = {18, 18, 18, 10, 18, 12, 12, 24};

        for (int i = 0; i < encabezados.length; i++) {
            ws.setColumnWidth(i, anchos[i] * 256);
        }

        XSSFRow encabezado = ws.createRow(0);
        CellStyle estiloEnc = estiloEncabezado(wb);

        for (int i = 0; i < encabezados.length; i++) {
            celda(encabezado, i, encabezados[i], estiloEnc);
        }

        String sql = """
            SELECT id, tipo, clase, amb, tarr, dimarr, nopar, tpar
            FROM tabla_simbolos
            ORDER BY amb, id
            """;

        int fila = 1;

        try (Connection conexion = ConexionBD.conectar();
             PreparedStatement ps = conexion.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {

                XSSFRow row = ws.createRow(fila);
                boolean par = (fila % 2 == 0);

                celda(row, 0, rs.getString("id"),
                        estiloDato(wb, par, null));

                celda(row, 1, rs.getString("tipo"),
                        estiloDato(wb, par, null));

                celda(row, 2, rs.getString("clase"),
                        estiloDato(wb, par, null));

                celda(row, 3, rs.getInt("amb"),
                        estiloDatoNum(wb, par));

                celda(row, 4, rs.getString("tarr"),
                        estiloDato(wb, par, null));

                // dimarr puede ser NULL
                Object dimarr = rs.getObject("dimarr");
                if (dimarr == null) {
                    celda(row, 5, "", estiloDato(wb, par, null));
                } else {
                    celda(row, 5, rs.getInt("dimarr"),
                            estiloDatoNum(wb, par));
                }

                // nopar puede ser NULL
                Object nopar = rs.getObject("nopar");
                if (nopar == null) {
                    celda(row, 6, "", estiloDato(wb, par, null));
                } else {
                    celda(row, 6, rs.getInt("nopar"),
                            estiloDatoNum(wb, par));
                }

                celda(row, 7, rs.getString("tpar"),
                        estiloDato(wb, par, null));

                fila++;
            }

        } catch (SQLException e) {
            System.err.println("Error al obtener la tabla de símbolos.");
            e.printStackTrace();

            JOptionPane.showMessageDialog(
                null,
                "No se pudo generar la hoja Tabla de simbolos:\n"
                    + e.getMessage(),
                "Error de base de datos",
                JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        ws.createFreezePane(0, 1);

        if (fila > 1) {
            ws.setAutoFilter(
                new CellRangeAddress(
                    0, fila - 1, 0, encabezados.length - 1
                )
            );
        }
    }



    // ═══════════════════════════════════════════════════════════════════════
    // HOJA 7 — SEMANTICA 1
    // Solo contabiliza lo que ya produjo sintaxis (temporales generados y
    // asignaciones por línea) y los ErrorEntry de tipo SEMANTICA.
    // ═══════════════════════════════════════════════════════════════════════
    private static void poblarSemantica1(XSSFWorkbook wb,
                                         Map<Integer, int[]> estadisticas,
                                         List<ErrorEntry> errores) {

        XSSFSheet ws = wb.createSheet("SEMANTICA 1");

        String[] encabezados = {
            "Linea", "TBin", "TDec", "TOct", "THex", "TReal", "Texp",
            "TCadena", "TBoolean", "TVariant", "Asignaciones", "Errores"
        };

        for (int i = 0; i < encabezados.length; i++) {
            ws.setColumnWidth(i, (i == 10 ? 16 : 12) * 256);
        }

        XSSFRow encabezado = ws.createRow(0);
        encabezado.setHeightInPoints(18);
        CellStyle estiloEnc = estiloEncabezado(wb);
        for (int i = 0; i < encabezados.length; i++) {
            celda(encabezado, i, encabezados[i], estiloEnc);
        }

        // Errores Semántica 1 por línea (los de ErrorEntry, sin recalcular nada)
        Map<Integer, Integer> erroresPorLinea = new LinkedHashMap<>();
        if (errores != null) {
            for (ErrorEntry e : errores) {
                if (e.getTipo() == ErrorEntry.Tipo.SEMANTICA) {
                    erroresPorLinea.merge(e.getLinea(), 1, Integer::sum);
                }
            }
        }

        int[] totales = new int[11]; // 9 temporales + asignaciones + errores
        int fila = 1;

        if (estadisticas != null) {
            for (Map.Entry<Integer, int[]> entrada : estadisticas.entrySet()) {

                int linea = entrada.getKey();
                int[] c = entrada.getValue();
                int errs = erroresPorLinea.getOrDefault(linea, 0);

                XSSFRow row = ws.createRow(fila);
                row.setHeightInPoints(16);
                CellStyle estilo = estiloDatoNum(wb, fila % 2 == 0);

                celda(row, 0, linea, estilo);
                for (int i = 0; i < 10; i++) {        // 9 temporales + asignaciones
                    celda(row, i + 1, c[i], estilo);
                    totales[i] += c[i];
                }
                celda(row, 11, errs, estilo);
                totales[10] += errs;
                fila++;
            }
        }

        XSSFRow rowTotal = ws.createRow(fila);
        rowTotal.setHeightInPoints(18);
        CellStyle estiloTotal = estiloValor(wb);
        celda(rowTotal, 0, "Totales", estiloTotal);
        for (int i = 0; i < 11; i++) {
            celda(rowTotal, i + 1, totales[i], estiloTotal);
        }

        ws.createFreezePane(0, 1);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ESTILOS
    // ═══════════════════════════════════════════════════════════════════════

    private static XSSFCellStyle estiloEncabezado(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(COLOR_HDR_BG);
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBottomBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setColor(COLOR_HDR_FG);
        f.setFontHeightInPoints((short) 11);
        f.setFontName("Segoe UI");
        s.setFont(f);
        return s;
    }

    private static XSSFCellStyle estiloDato(XSSFWorkbook wb, boolean par, XSSFColor fg) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(par ? COLOR_ROW_EVEN : COLOR_ROW_ODD);
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.LEFT);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont f = wb.createFont();
        f.setColor(fg != null ? fg : COLOR_HDR_FG);
        f.setFontHeightInPoints((short) 11);
        f.setFontName("Consolas");
        s.setFont(f);
        return s;
    }

    private static XSSFCellStyle estiloDatoNum(XSSFWorkbook wb, boolean par) {
        XSSFCellStyle s = estiloDato(wb, par, null);
        s.setAlignment(HorizontalAlignment.RIGHT);
        return s;
    }

    private static XSSFCellStyle estiloCategoria(XSSFWorkbook wb, XSSFColor bgColor) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(bgColor != null ? bgColor : COLOR_COUNT_H);
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setWrapText(true);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.MEDIUM);
        s.setBottomBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
        s.setRightBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setColor(COLOR_HDR_FG);
        f.setFontHeightInPoints((short) 10);
        f.setFontName("Segoe UI");
        s.setFont(f);
        return s;
    }

    private static XSSFCellStyle estiloSubcat(XSSFWorkbook wb, XSSFColor catColor) {
        XSSFCellStyle s = wb.createCellStyle();
        // Usar una variante más oscura del color de categoría
        s.setFillForegroundColor(catColor != null ? catColor : COLOR_HDR_BG);
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setWrapText(true);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        s.setBottomBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
        s.setRightBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
        XSSFFont f = wb.createFont();
        f.setColor(COLOR_HDR_FG);
        f.setItalic(true);
        f.setFontHeightInPoints((short) 9);
        f.setFontName("Segoe UI");
        s.setFont(f);
        return s;
    }

    private static XSSFCellStyle estiloValor(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(COLOR_ROW_EVEN);
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        s.setTopBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
        s.setRightBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
        XSSFFont f = wb.createFont();
        f.setColor(COLOR_COUNT_V);
        f.setBold(true);
        f.setFontHeightInPoints((short) 11);
        f.setFontName("Consolas");
        s.setFont(f);
        return s;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UTILIDADES
    // ═══════════════════════════════════════════════════════════════════════

    private static File elegirDestino(java.awt.Component parent) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Guardar análisis léxico como Excel");
        fc.setFileFilter(new FileNameExtensionFilter("Archivo Excel (*.xlsx)", "xlsx"));
        fc.setSelectedFile(new File("Ambito-DavidAlatorre.xlsx"));
        if (fc.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return null;
        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith(".xlsx"))
            f = new File(f.getAbsolutePath() + ".xlsx");
        return f;
    }

    private static void celda(XSSFRow row, int col, String val, CellStyle style) {
        XSSFCell c = row.createCell(col);
        c.setCellValue(val != null ? val : "");
        c.setCellStyle(style);
    }

    private static void celda(XSSFRow row, int col, int val, CellStyle style) {
        XSSFCell c = row.createCell(col);
        c.setCellValue(val);
        c.setCellStyle(style);
    }

    private static XSSFColor rgb(int r, int g, int b) {
        return new XSSFColor(new Color(r, g, b), null);
    }


    // ═══════════════════════════════════════════════════════════════════════
// HOJA 4 — CONTADORES SINTAXIS
// ═══════════════════════════════════════════════════════════════════════
private static void poblarContadoresSintaxis(XSSFWorkbook wb) {

    XSSFSheet ws = wb.createSheet("CONT_SINTAXIS");

    String[] nombres = {
        "ERRORES",
        "PROGRAMA",
        "LISTA_DE_PARAMETROS",
        "EXP_PAS",
        "CONSTANTESSIGNO",
        "CONSTNUMERICA",
        "OR",
        "AND",
        "DECLARACIONCONSTANTES",
        "FACTOR",
        "ELEVACION",
        "TERMINOPASCAL",
        "SimpleExpPascal",
        "STATU",
        "FUNCION",
        "ASIG",
        "ARR"
    };

    int[] valores = {
        ContadorCiclos.ERRORES,
        ContadorCiclos.PROGRAMA,
        ContadorCiclos.LISTA_DE_PARAMETROS,
        ContadorCiclos.EXP_PAS,
        ContadorCiclos.CONSTANTESSIGNO,
        ContadorCiclos.CONSTNUMERICA,
        ContadorCiclos.OR,
        ContadorCiclos.AND,
        ContadorCiclos.DECLARACIONCONSTANTES,
        ContadorCiclos.FACTOR,
        ContadorCiclos.ELEVACION,
        ContadorCiclos.TERMINOPASCAL,
        ContadorCiclos.SimpleExpPascal,
        ContadorCiclos.STATU,
        ContadorCiclos.FUNCION,
        ContadorCiclos.ASIG,
        ContadorCiclos.ARR
    };

    // Ajustar ancho de columnas
    for (int i = 0; i < nombres.length; i++) {
        ws.setColumnWidth(i, 24 * 256);
    }

    // ── FILA 1 → NOMBRES ───────────────────────────────
    XSSFRow fila1 = ws.createRow(0);
    fila1.setHeightInPoints(22);

    for (int i = 0; i < nombres.length; i++) {
        XSSFCell cell = fila1.createCell(i);
        cell.setCellValue(nombres[i]);
        cell.setCellStyle(estiloCategoria(wb, CAT_OP));
    }

    // ── FILA 2 → CONTADORES ───────────────────────────
    XSSFRow fila2 = ws.createRow(1);
    fila2.setHeightInPoints(20);

    CellStyle estiloValores = estiloValor(wb);

    for (int i = 0; i < valores.length; i++) {
        XSSFCell cell = fila2.createCell(i);
        cell.setCellValue(valores[i]);
        cell.setCellStyle(estiloValores);
    }
}
}