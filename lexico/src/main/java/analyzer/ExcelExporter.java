package analyzer;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

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
        File destino = elegirDestino(parent);
        if (destino == null) return;

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            poblarTokens(wb, tokens);
            poblarErrores(wb, errores);
            poblarContadores(wb, errores.size(), contador);
            poblarContadoresSintaxis(wb); // NUEVA HOJA

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
     *  Col  1–8      → Identificadores  (cadena, binario, decimal, octal, hex, real, exp, bool)
     *  Col  9        → Comentarios línea
     *  Col 10        → Comentarios multilínea
     *  Col 11        → Palabras reservadas
     *  Col 12–20     → Constantes       (cadena, bin, dec, oct, hex, real, exp, bool, null)
     *  Col 21        → Op. Postfix
     *  Col 22        → Op. Lógicos binarios
     *  Col 23        → Op. Control
     *  Col 24        → Op. Matemáticos
     *  Col 25        → Op. Exponente
     *  Col 26        → Op. Turno
     *  Col 27        → Op. Relacionales
     *  Col 28        → Op. Igualdad estricta
     *  Col 29        → Op. Lógicos
     *  Col 30        → Op. Ternario
     *  Col 31        → Op. Asignación
     *  Col 32        → Op. Agrupamiento
     */
    private static void poblarContadores(XSSFWorkbook wb,
                                          int            totalErrores,
                                          ContadorTokens c) {
        XSSFSheet ws = wb.createSheet("CONTADORES");

        // Anchos de columna
        ws.setColumnWidth(0, 10 * 256);   // Errores
        for (int i =  1; i <=  8; i++) ws.setColumnWidth(i, 16 * 256); // Identificadores
        ws.setColumnWidth(9,  16 * 256);  // Com. línea
        ws.setColumnWidth(10, 16 * 256);  // Com. multi
        ws.setColumnWidth(11, 18 * 256);  // Pal. reservadas
        for (int i = 12; i <= 20; i++) ws.setColumnWidth(i, 16 * 256); // Constantes
        for (int i = 21; i <= 32; i++) ws.setColumnWidth(i, 22 * 256); // Operadores

        // ── Fusiones ──────────────────────────────────────────────────────
        ws.addMergedRegion(new CellRangeAddress(0, 1,  0,  0));  // Errores
        ws.addMergedRegion(new CellRangeAddress(0, 0,  1,  8));  // Identificadores
        ws.addMergedRegion(new CellRangeAddress(0, 0,  9, 10));  // Comentarios
        ws.addMergedRegion(new CellRangeAddress(0, 1, 11, 11));  // Pal. reservadas
        ws.addMergedRegion(new CellRangeAddress(0, 0, 12, 20));  // Constantes
        // Operadores: cada subcategoría ocupa 1 columna sin subrow extra
        ws.addMergedRegion(new CellRangeAddress(0, 1, 21, 21));
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

        // ── Fila 0: categorías ────────────────────────────────────────────
        XSSFRow r0 = ws.createRow(0);
        r0.setHeightInPoints(22);

        celda(r0,  0, "Errores",             estiloCategoria(wb, null));
        celda(r0,  1, "IDENTIFICADORES",     estiloCategoria(wb, CAT_ID));
        celda(r0,  9, "COMENTARIOS",         estiloCategoria(wb, CAT_COM));
        celda(r0, 11, "PAL. RESERVADAS",     estiloCategoria(wb, CAT_KW));
        celda(r0, 12, "CONSTANTES",          estiloCategoria(wb, CAT_CST));
        celda(r0, 21, "Postfix",             estiloCategoria(wb, CAT_OP));
        celda(r0, 22, "Log. binarios",       estiloCategoria(wb, CAT_OP));
        celda(r0, 23, "Control",             estiloCategoria(wb, CAT_OP));
        celda(r0, 24, "Matemáticos",         estiloCategoria(wb, CAT_OP));
        celda(r0, 25, "Exponente",           estiloCategoria(wb, CAT_OP));
        celda(r0, 26, "Turno",               estiloCategoria(wb, CAT_OP));
        celda(r0, 27, "Relacionales",        estiloCategoria(wb, CAT_OP));
        celda(r0, 28, "Igualdad estricta",   estiloCategoria(wb, CAT_OP));
        celda(r0, 29, "Lógicos",             estiloCategoria(wb, CAT_OP));
        celda(r0, 30, "Ternario",            estiloCategoria(wb, CAT_OP));
        celda(r0, 31, "Asignación",          estiloCategoria(wb, CAT_OP));
        celda(r0, 32, "Agrupamiento",        estiloCategoria(wb, CAT_OP));

        // ── Fila 1: subcategorías ─────────────────────────────────────────
        XSSFRow r1 = ws.createRow(1);
        r1.setHeightInPoints(18);

        // Identificadores
        String[] subId = {"Cadena", "Binario", "Decimal", "Octal",
                          "Hexadecimal", "Real", "Exponencial", "Booleanas"};
        for (int i = 0; i < subId.length; i++)
            celda(r1, 1 + i, subId[i], estiloSubcat(wb, CAT_ID));

        // Comentarios
        celda(r1,  9, "Línea",      estiloSubcat(wb, CAT_COM));
        celda(r1, 10, "Multilínea", estiloSubcat(wb, CAT_COM));

        // Constantes
        String[] subCst = {"Cadena", "Binario", "Decimal", "Octal",
                           "Hexadecimal", "Real", "Exponencial", "Booleanas", "Null"};
        for (int i = 0; i < subCst.length; i++)
            celda(r1, 12 + i, subCst[i], estiloSubcat(wb, CAT_CST));

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

        // Comentarios
        // ContadorTokens.comentarios = total; no distingue línea vs multilínea.
        // Si en el futuro se añaden campos separados, cambiar aquí.
        celda(r2,  9, c.comentarios,      sv);  // línea (total provisionalmente)
        celda(r2, 10, 0,                  sv);  // multilínea (pendiente de desglose)

        // Palabras reservadas
        celda(r2, 11, c.reservadas,       sv);

        // Constantes
        celda(r2, 12, c.cteCadena,        sv);
        celda(r2, 13, c.cteBinario,       sv);
        celda(r2, 14, c.cteDecimal,       sv);
        celda(r2, 15, c.cteOctal,         sv);
        celda(r2, 16, c.cteHex,           sv);
        celda(r2, 17, c.cteReal,          sv);
        celda(r2, 18, c.cteExp,           sv);
        celda(r2, 19, c.cteBool,          sv);
        celda(r2, 20, c.cteNull,          sv);

        // Operadores
        celda(r2, 21, c.opPostfix,        sv);
        celda(r2, 22, c.opLogBin,         sv);
        celda(r2, 23, c.opControl,        sv);
        celda(r2, 24, c.opMat,            sv);
        celda(r2, 25, c.opExp,            sv);
        celda(r2, 26, c.opTurno,          sv);
        celda(r2, 27, c.opRel,            sv);
        celda(r2, 28, c.opIgualdad,       sv);
        celda(r2, 29, c.opLogicos,        sv);
        celda(r2, 30, c.opTernario,       sv);
        celda(r2, 31, c.opAsignacion,     sv);
        celda(r2, 32, c.opAgrup,          sv);
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
        fc.setSelectedFile(new File("analisis_lexico.xlsx"));
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