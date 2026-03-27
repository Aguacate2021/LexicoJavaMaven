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
 * ExcelExporter
 *
 * Exporta los resultados del análisis léxico a un archivo .xlsx
 * siguiendo la estructura de la Plantilla_de_lexico_2026.xlsx.
 *
 * ── Mapa de estados de aceptación (Ayuda.xlsx) ─────────────────────────────
 *
 *  OPERADORES
 *    Postfix             : -1  (++)    -2  (--)
 *    Lógicos binarios    : -3  (~)     -4  (|)     -5  (&)     -6  (^)
 *    Control             : -7  (,)     -8  (.)     -9  (;)     -10 (:)
 *    Matemáticos         : -11 (+)     -12 (-)     -13 (*)     -14 (/)     -15 (%)
 *    Exponente           : -16 (**)
 *    Turno               : -17 (<<)    -18 (>>)    -19 (>>>)
 *    Relacionales        : -20 (<)     -21 (>)     -22 (<=)    -23 (>=)
 *                          -24 (==)    -25 (!=)    -26 (<>)
 *    Sin igualdad tipo   : -27 (===)   -28 (!==)
 *    Lógicos             : -29 (!)     -30 (&&)    -31 (||)
 *    Ternario            : -32 (?)
 *    Asignación          : -33 (=) .. -44 (=>)
 *    Agrupamiento        : -45 ({) .. -50 ())
 *
 *  COMENTARIOS
 *    Grupal (/* *\/)     : -51
 *    Lineal (//)         : -52
 *
 *  CONSTANTES
 *    Cadena              : -53
 *    Numérica Binario    : -52  (mismo estado que comentario lineal; diferenciado por Tipo)
 *    Numérica Decimal    : -53
 *    Numérica Octal      : -54
 *    Numérica Hexadecimal: -55
 *    Real                : -56
 *    Exponencial         : -57
 *
 *  IDENTIFICADORES
 *    Cadena              : -58
 *    Numérica Binario    : -59
 *    Numérica Decimal    : -60
 *    Numérica Octal      : -61
 *    Numérica Hexadecimal: -62
 *    Real                : -63
 *    Exponencial         : -64
 *    Booleanas           : -65
 *
 *  PALABRAS RESERVADAS / true / false / null : -66
 *
 * Dependencia: Apache POI (poi-ooxml).
 * Uso desde AnalyzerIDE:
 *   ExcelExporter.exportar(parentFrame, tokens, errores);
 */
public class ExcelExporter {

    // ── Paleta ────────────────────────────────────────────────────────────────
    private static final XSSFColor COLOR_HDR_BG   = rgb(0x2D, 0x2D, 0x2D);
    private static final XSSFColor COLOR_HDR_FG   = rgb(0xD4, 0xD4, 0xD4);
    private static final XSSFColor COLOR_ROW_EVEN = rgb(0x2A, 0x2A, 0x2A);
    private static final XSSFColor COLOR_ROW_ODD  = rgb(0x22, 0x22, 0x22);
    private static final XSSFColor COLOR_KEYWORD  = rgb(0x56, 0x9C, 0xD6);
    private static final XSSFColor COLOR_ERROR    = rgb(0xF4, 0x47, 0x47);
    private static final XSSFColor COLOR_WARN     = rgb(0xE5, 0xC0, 0x7B);
    private static final XSSFColor COLOR_COUNT_H  = rgb(0x3A, 0x3A, 0x3A);
    private static final XSSFColor COLOR_COUNT_V  = rgb(0xB5, 0xCE, 0xA8);

    // ════════════════════════════════════════════════════════════════════════
    // PUNTO DE ENTRADA PÚBLICO
    // ════════════════════════════════════════════════════════════════════════

    public static void exportar(java.awt.Component parent,
                                List<Token>      tokens,
                                List<ErrorEntry> errores) {

        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Guardar análisis léxico como Excel");
        fc.setFileFilter(new FileNameExtensionFilter("Archivo Excel (*.xlsx)", "xlsx"));
        fc.setSelectedFile(new File("analisis_lexico.xlsx"));

        if (fc.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return;

        File destino = fc.getSelectedFile();
        if (!destino.getName().toLowerCase().endsWith(".xlsx"))
            destino = new File(destino.getAbsolutePath() + ".xlsx");

        try (XSSFWorkbook wb = new XSSFWorkbook()) {

            poblarTokens(wb, tokens);
            poblarErrores(wb, errores);
            poblarContadores(wb, tokens, errores);

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

    // ════════════════════════════════════════════════════════════════════════
    // HOJA 1 — TOKENS
    // ════════════════════════════════════════════════════════════════════════
    private static void poblarTokens(XSSFWorkbook wb, List<Token> tokens) {
        XSSFSheet ws = wb.createSheet("TOKENS");
        ws.setColumnWidth(0, 24 * 256);
        ws.setColumnWidth(1, 30 * 256);
        ws.setColumnWidth(2, 10 * 256);

        String[] hdrs = {"Estado", "Lexema", "linea"};
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
            celda(row, 1, t.getLexema(),                 estiloDato(wb, par, null));
            celda(row, 2, t.getLinea(),                  estiloDatoNum(wb, par));
            fila++;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // HOJA 2 — Errores
    // ════════════════════════════════════════════════════════════════════════
    private static void poblarErrores(XSSFWorkbook wb, List<ErrorEntry> errores) {
        XSSFSheet ws = wb.createSheet("Errores");
        ws.setColumnWidth(0, 20 * 256);
        ws.setColumnWidth(1, 40 * 256);
        ws.setColumnWidth(2, 25 * 256);
        ws.setColumnWidth(3, 18 * 256);
        ws.setColumnWidth(4, 10 * 256);

        String[] hdrs = {"Token", "Descripcion", "lexema", "tipo de error", "linea"};
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

    // ════════════════════════════════════════════════════════════════════════
    // HOJA 3 — CONTADORES
    // ════════════════════════════════════════════════════════════════════════
    private static void poblarContadores(XSSFWorkbook wb,
                                         List<Token>      tokens,
                                         List<ErrorEntry> errores) {
        XSSFSheet ws = wb.createSheet("CONTADORES");

        ws.setColumnWidth(0, 12 * 256);
        for (int c = 1;  c <= 9;  c++) ws.setColumnWidth(c, 18 * 256);
        ws.setColumnWidth(10, 18 * 256);
        for (int c = 11; c <= 19; c++) ws.setColumnWidth(c, 18 * 256);
        for (int c = 20; c <= 31; c++) ws.setColumnWidth(c, 22 * 256);

        // Fusiones (réplica exacta de la plantilla original)
        ws.addMergedRegion(new CellRangeAddress(0, 1, 0,  0));   // A     Errores
        ws.addMergedRegion(new CellRangeAddress(0, 0, 1,  8));   // B:I   Identificadores
        ws.addMergedRegion(new CellRangeAddress(0, 1, 9,  9));   // J     Comentarios
        ws.addMergedRegion(new CellRangeAddress(0, 1, 10, 10));  // K     Palabras reservadas
        ws.addMergedRegion(new CellRangeAddress(0, 0, 11, 19));  // L:T   Constantes
        for (int c = 20; c <= 31; c++)
            ws.addMergedRegion(new CellRangeAddress(0, 1, c, c));

        // ── Fila 0: categorías ────────────────────────────────────────────────
        XSSFRow r0 = ws.createRow(0);
        r0.setHeightInPoints(20);
        celda(r0, 0,  "Errores",                                      estiloCategoria(wb));
        celda(r0, 1,  "identificadores",                               estiloCategoria(wb));
        celda(r0, 9,  "comentarios",                                   estiloCategoria(wb));
        celda(r0, 10, "palabras reservada",                            estiloCategoria(wb));
        celda(r0, 11, "Constantes",                                    estiloCategoria(wb));
        celda(r0, 20, "operadores de postfix",                         estiloCategoria(wb));
        celda(r0, 21, "Operadores lógicos binarios",                   estiloCategoria(wb));
        celda(r0, 22, "Operadores de control",                         estiloCategoria(wb));
        celda(r0, 23, "Operadores matemáticos",                        estiloCategoria(wb));
        celda(r0, 24, "Operador exponente",                            estiloCategoria(wb));
        celda(r0, 25, "Operadores de turno",                           estiloCategoria(wb));
        celda(r0, 26, "Operadores relacionales",                       estiloCategoria(wb));
        celda(r0, 27, "Operadores sin igualdad de conversión de tipo", estiloCategoria(wb));
        celda(r0, 28, "Operadores lógicos",                            estiloCategoria(wb));
        celda(r0, 29, "Operador ternario",                             estiloCategoria(wb));
        celda(r0, 30, "Operadores de Asignación",                      estiloCategoria(wb));
        celda(r0, 31, "Operadores de agrupamiento",                    estiloCategoria(wb));

        // ── Fila 1: subcategorías ─────────────────────────────────────────────
        XSSFRow r1 = ws.createRow(1);
        r1.setHeightInPoints(18);

        String[] subId = {"cadena", "Numérica Binario", "Numérica Decimal",
                          "Numérica Octal", "Numérica Hexadecimal",
                          "Real", "Exponencial", "Booleanas"};
        for (int i = 0; i < subId.length; i++)
            celda(r1, 1 + i, subId[i], estiloSubcat(wb));

        String[] subConst = {"cadena", "Numérica Binario", "Numérica Decimal",
                             "Numérica Octal", "Numérica Hexadecimal",
                             "Real", "Exponencial", "Booleanas", "nula"};
        for (int i = 0; i < subConst.length; i++)
            celda(r1, 11 + i, subConst[i], estiloSubcat(wb));

        // ── Fila 2: conteos usando estados reales del autómata (Ayuda.xlsx) ───
        int cntIdCad=0, cntIdBin=0, cntIdDec=0, cntIdOct=0;
        int cntIdHex=0, cntIdReal=0, cntIdExp=0, cntIdBool=0;

        int cntCstCad=0, cntCstBin=0, cntCstDec=0, cntCstOct=0;
        int cntCstHex=0, cntCstReal=0, cntCstExp=0, cntCstBool=0, cntCstNull=0;

        int cntComGrupal=0, cntComLineal=0;
        int cntKw=0;

        int cntPostfix=0, cntLogBin=0, cntCtrl=0, cntMath=0, cntPow=0;
        int cntShift=0, cntRel=0, cntSinIg=0, cntLog=0, cntTern=0;
        int cntAsign=0, cntAgrup=0;

        for (Token t : tokens) {
            int    st  = (int) t.getEstado();
            String lex = t.getLexema();

            // ── Identificadores ───────────────────────────────────────────────
            switch (st) {
                case -58 -> cntIdCad++;
                case -59 -> cntIdBin++;
                case -60 -> cntIdDec++;
                case -61 -> cntIdOct++;
                case -62 -> cntIdHex++;
                case -63 -> cntIdReal++;
                case -64 -> cntIdExp++;
                case -65 -> cntIdBool++;
            }

            // ── Palabras reservadas / true / false / null ─────────────────────
            if (st == -66) {
                if      (lex.equals("true") || lex.equals("false")) cntCstBool++;
                else if (lex.equals("null"))                         cntCstNull++;
                else                                                  cntKw++;
            }

            // ── Comentarios ───────────────────────────────────────────────────
            if (st == -51) cntComGrupal++;
            if (st == -52 && t.getTipo() == Token.Tipo.COMMENT) cntComLineal++;

            // ── Constantes numéricas / cadena ─────────────────────────────────
            // Estado -52 puede ser tanto comentario lineal como binario numérico;
            // se diferencia por el Tipo del token.
            if (st == -52 && t.getTipo() != Token.Tipo.COMMENT) cntCstBin++;
            if (st == -53 && t.getTipo() == Token.Tipo.STRING)  cntCstCad++;
            if (st == -53 && t.getTipo() == Token.Tipo.NUMBER)  cntCstDec++;
            if (st == -54) cntCstOct++;
            if (st == -55) cntCstHex++;
            if (st == -56) cntCstReal++;
            if (st == -57) cntCstExp++;

            // ── Operadores ────────────────────────────────────────────────────
            if (st == -1  || st == -2)    cntPostfix++;
            if (st >= -6  && st <= -3)    cntLogBin++;
            if (st >= -10 && st <= -7)    cntCtrl++;
            if (st >= -15 && st <= -11)   cntMath++;
            if (st == -16)                cntPow++;
            if (st >= -19 && st <= -17)   cntShift++;
            if (st >= -26 && st <= -20)   cntRel++;
            if (st == -27 || st == -28)   cntSinIg++;
            if (st >= -31 && st <= -29)   cntLog++;
            if (st == -32)                cntTern++;
            if (st >= -44 && st <= -33)   cntAsign++;
            if (st >= -50 && st <= -45)   cntAgrup++;
        }

        XSSFRow r2 = ws.createRow(2);
        r2.setHeightInPoints(16);
        CellStyle sv = estiloValor(wb);

        // A — Errores
        r2.createCell(0).setCellValue(errores.size());
        r2.getCell(0).setCellStyle(sv);

        // B-I — Identificadores
        int[] vId = {cntIdCad, cntIdBin, cntIdDec, cntIdOct,
                     cntIdHex, cntIdReal, cntIdExp, cntIdBool};
        for (int i = 0; i < vId.length; i++) {
            r2.createCell(1 + i).setCellValue(vId[i]);
            r2.getCell(1 + i).setCellStyle(sv);
        }

        // J — Comentarios (grupal + lineal)
        r2.createCell(9).setCellValue(cntComGrupal + cntComLineal);
        r2.getCell(9).setCellStyle(sv);

        // K — Palabras reservadas
        r2.createCell(10).setCellValue(cntKw);
        r2.getCell(10).setCellStyle(sv);

        // L-T — Constantes
        int[] vCst = {cntCstCad, cntCstBin, cntCstDec, cntCstOct,
                      cntCstHex, cntCstReal, cntCstExp, cntCstBool, cntCstNull};
        for (int i = 0; i < vCst.length; i++) {
            r2.createCell(11 + i).setCellValue(vCst[i]);
            r2.getCell(11 + i).setCellStyle(sv);
        }

        // U-AF — Operadores (12 categorías)
        int[] vOp = {cntPostfix, cntLogBin, cntCtrl, cntMath, cntPow,
                     cntShift, cntRel, cntSinIg, cntLog, cntTern, cntAsign, cntAgrup};
        for (int i = 0; i < vOp.length; i++) {
            r2.createCell(20 + i).setCellValue(vOp[i]);
            r2.getCell(20 + i).setCellStyle(sv);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // ESTILOS
    // ════════════════════════════════════════════════════════════════════════

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

    private static XSSFCellStyle estiloDato(XSSFWorkbook wb, boolean par, XSSFColor fgColor) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(par ? COLOR_ROW_EVEN : COLOR_ROW_ODD);
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.LEFT);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont f = wb.createFont();
        f.setColor(fgColor != null ? fgColor : COLOR_HDR_FG);
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

    private static XSSFCellStyle estiloCategoria(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(COLOR_COUNT_H);
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setWrapText(true);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
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

    private static XSSFCellStyle estiloSubcat(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(COLOR_HDR_BG);
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setWrapText(true);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBottomBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
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
        s.setTopBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
        XSSFFont f = wb.createFont();
        f.setColor(COLOR_COUNT_V);
        f.setBold(true);
        f.setFontHeightInPoints((short) 11);
        f.setFontName("Consolas");
        s.setFont(f);
        return s;
    }

    // ════════════════════════════════════════════════════════════════════════
    // UTILIDADES
    // ════════════════════════════════════════════════════════════════════════

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
}