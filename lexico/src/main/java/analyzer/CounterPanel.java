package analyzer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.MatteBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

public class CounterPanel extends JPanel {

    // ── Paleta ─────────────────────────────────────────────────────────────
    private static final Color BG_DARK     = new Color(0x1E, 0x1E, 0x1E);
    private static final Color BG_PANEL    = new Color(0x2D, 0x2D, 0x2D);
    private static final Color BG_HDR      = new Color(0x3A, 0x3A, 0x3A);
    private static final Color BG_SUB      = new Color(0x2A, 0x2A, 0x2A);
    private static final Color BG_VAL      = new Color(0x22, 0x22, 0x22);
    private static final Color BORDER      = new Color(0x3C, 0x3C, 0x3C);
    private static final Color TEXT_MAIN   = new Color(0xD4, 0xD4, 0xD4);
    private static final Color TEXT_DIM    = new Color(0x85, 0x85, 0x85);
    private static final Color TEXT_ZERO   = new Color(0x50, 0x50, 0x50);
    private static final Color TEXT_NUMBER = new Color(0xB5, 0xCE, 0xA8);
    private static final Color TEXT_ERR    = new Color(0xF4, 0x47, 0x47);
    private static final Color TEXT_WARN   = new Color(0xE5, 0xC0, 0x7B);
    private static final Color TEXT_TOTAL  = new Color(0xFF, 0x60, 0x60);

    // Colores de categoría
    private static final Color CAT_ID  = new Color(0x1E, 0x3A, 0x5F);
    private static final Color CAT_COM = new Color(0x16, 0x3B, 0x26);
    private static final Color CAT_KW  = new Color(0x3B, 0x1F, 0x5C);
    private static final Color CAT_CST = new Color(0x4A, 0x2B, 0x12);
    private static final Color CAT_OP  = new Color(0x4A, 0x12, 0x12);
    private static final Color CAT_ERR = new Color(0x5A, 0x10, 0x10);
    private static final Color[] CAT_COLORS = {
        CAT_ID, CAT_COM, CAT_KW, CAT_CST, CAT_OP, CAT_ERR
    };

    // Fuentes
    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 11);
    private static final Font FONT_CAT   = new Font("Segoe UI", Font.BOLD, 10);
    private static final Font FONT_SUB   = new Font("Segoe UI", Font.ITALIC, 9);
    private static final Font FONT_VAL   = new Font("Consolas", Font.BOLD, 12);

    // ── Definición de categorías ────────────────────────────────────────────
    private static final Object[][] CATS = {
        { "IDENTIFICADORES", CAT_ID,
          new String[]{"Cadena","Binario","Decimal","Octal","Hex","Real","Exp","Bool"} },
        { "COMENTARIOS", CAT_COM,
          new String[]{"Línea","Multilínea"} },
        { "PAL. RESERVADAS", CAT_KW,
          new String[]{"Total"} },
        { "CONSTANTES", CAT_CST,
          new String[]{"Cadena","Binario","Decimal","Octal","Hex","Real","Exp","Bool","Null"} },
        { "OPERADORES", CAT_OP,
          new String[]{"Postfix","Log bin","Control","Matemát.","Exponente",
                       "Turno","Relac.","Igualdad","Lógicos","Ternario","Asignac.","Agrupam."} },
        { "ERRORES", CAT_ERR,
          new String[]{"Léxicos","Sintácticos","Total"} },
    };
    private static final int ERR_COL_START;
    private static final int TOTAL_COLS;
     // índice donde empieza la cat. ERRORES

    static {
        int n = 0, errStart = 0;
        for (int ci = 0; ci < CATS.length; ci++) {
            if (ci == CATS.length - 1) errStart = n;
            n += ((String[]) CATS[ci][2]).length;
        }
        TOTAL_COLS    = n;
        ERR_COL_START = errStart;
    }

    private static DefaultTableModel model;
    private static JTable            table;

    // ════════════════════════════════════════════════════════════════════════
    public CounterPanel() {
        setLayout(new BorderLayout());
        setBackground(BG_DARK);
        setBorder(new MatteBorder(1, 0, 0, 0, BORDER));

        // Título
        JLabel title = new JLabel("  CONTADORES");
        title.setFont(FONT_TITLE);
        title.setForeground(TEXT_MAIN);
        title.setBackground(BG_HDR);
        title.setOpaque(true);
        title.setBorder(new MatteBorder(0, 0, 1, 0, BORDER));
        title.setPreferredSize(new Dimension(0, 22));
        add(title, BorderLayout.NORTH);

        // Tabla
        model = new DefaultTableModel(3, TOTAL_COLS) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(model);
        table.setTableHeader(null);
        table.setRowHeight(18);
        table.setBackground(BG_VAL);
        table.setForeground(TEXT_MAIN);
        table.setGridColor(BORDER);
        table.setShowGrid(true);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setFillsViewportHeight(false);
        table.setSelectionBackground(new Color(0x26, 0x40, 0x6E));
        table.setSelectionForeground(TEXT_MAIN);

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val,
                    boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                setHorizontalAlignment(SwingConstants.CENTER);
                setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER));

                Color catBg     = CAT_COLORS[catIndexForCol(col)];
                boolean isErrCat = col >= ERR_COL_START;

                switch (row) {
                    case 0 -> {
                        setBackground(catBg);
                        setForeground(lighter(catBg));
                        setFont(FONT_CAT);
                        if (!isPrimerColDeCat(col)) setText("");
                    }
                    case 1 -> {
                        setBackground(BG_SUB);
                        setForeground(isErrCat ? TEXT_WARN : TEXT_DIM);
                        setFont(FONT_SUB);
                    }
                    default -> {
                        setBackground(BG_VAL);
                        String s = val != null ? val.toString() : "0";
                        if (isErrCat) {
                            int errOffset = col - ERR_COL_START;
                            // col 0=Léxicos, 1=Sintácticos, 2=Total
                            setForeground("0".equals(s) ? TEXT_ZERO
                                    : errOffset == 2   ? TEXT_TOTAL
                                                       : TEXT_ERR);
                        } else {
                            setForeground("0".equals(s) ? TEXT_ZERO : TEXT_NUMBER);
                        }
                        setFont(FONT_VAL);
                    }
                }
                return this;
            }
        });

        initColumnas();

        JScrollPane scroll = new JScrollPane(table,
                JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG_VAL);
        styleScrollBar(scroll.getHorizontalScrollBar());
        add(scroll, BorderLayout.CENTER);

        int fixedH = 22 + 18 * 3 + 8;
        setPreferredSize(new Dimension(0, fixedH));
        setMinimumSize(new Dimension(0, fixedH));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, fixedH));
    }

    // ════════════════════════════════════════════════════════════════════════
    // API PÚBLICA
    // ════════════════════════════════════════════════════════════════════════

    /** Sobrecarga compatible con código existente — sin errores. */
    public static void actualizar(ContadorTokens c) {
        actualizar(c, c.comentarios, 0, 0, 0);
    }

    /** Sobrecarga compatible con código existente — con comentarios separados. */
    public static void actualizar(ContadorTokens c, int comentLinea, int comentMulti) {
        actualizar(c, comentLinea, comentMulti, 0, 0);
    }

    /**
     * Actualización completa: tokens + errores léxicos y sintácticos.
     * Llamar desde AnalyzerIDE.compilar() pasando:
     *   lexer.getErrores().size() y parser.getErroresSintaxis().size()
     */
    public static void actualizar(ContadorTokens c,
                                   int comentLinea,  int comentMulti,
                                   int errLexicos,   int errSintacticos) {
        int errTotal = errLexicos + errSintacticos;
        int[] vals = {
            // IDENTIFICADORES (8)
            c.idCadena, c.idBinario, c.idDecimal, c.idOctal,
            c.idHex,    c.idReal,    c.idExp,     c.idBool,
            // COMENTARIOS (2)
            comentLinea, comentMulti,
            // PAL. RESERVADAS (1)
            c.reservadas,
            // CONSTANTES (9)
            c.cteCadena, c.cteBinario, c.cteDecimal, c.cteOctal,
            c.cteHex,    c.cteReal,    c.cteExp,     c.cteBool, c.cteNull,
            // OPERADORES (12)
            c.opPostfix,    c.opLogBin,  c.opControl,    c.opMat,
            c.opExp,        c.opTurno,   c.opRel,         c.opIgualdad,
            c.opLogicos,    c.opTernario,c.opAsignacion,  c.opAgrup,
            // ERRORES (3)
            errLexicos, errSintacticos, errTotal
        };
        SwingUtilities.invokeLater(() -> {
            for (int col = 0; col < vals.length && col < TOTAL_COLS; col++)
                model.setValueAt(String.valueOf(vals[col]), 2, col);
            table.repaint();
        });
    }

    
    public void limpiar() {
        SwingUtilities.invokeLater(() -> {
            for (int col = 0; col < TOTAL_COLS; col++)
                model.setValueAt("0", 2, col);
            table.repaint();
        });
    }

    // ════════════════════════════════════════════════════════════════════════
    // INTERNOS
    // ════════════════════════════════════════════════════════════════════════

    private void initColumnas() {
        int col = 0;
        for (Object[] cat : CATS) {
            String   label = (String)   cat[0];
            String[] subs  = (String[]) cat[2];
            for (int si = 0; si < subs.length; si++) {
                model.setValueAt(si == 0 ? label : "", 0, col);
                model.setValueAt(subs[si],             1, col);
                model.setValueAt("0",                  2, col);
                int w = switch (label) {
                    case "PAL. RESERVADAS" -> 82;
                    case "OPERADORES"      -> 70;
                    case "ERRORES"         -> 82;
                    default                -> 68;
                };
                table.getColumnModel().getColumn(col).setPreferredWidth(w);
                table.getColumnModel().getColumn(col).setMinWidth(48);
                col++;
            }
        }
    }

    private int catIndexForCol(int col) {
        int acc = 0;
        for (int ci = 0; ci < CATS.length; ci++) {
            acc += ((String[]) CATS[ci][2]).length;
            if (col < acc) return ci;
        }
        return CATS.length - 1;
    }

    private boolean isPrimerColDeCat(int col) {
        int acc = 0;
        for (Object[] cat : CATS) {
            if (col == acc) return true;
            acc += ((String[]) cat[2]).length;
        }
        return false;
    }

    private Color lighter(Color c) {
        return new Color(
            Math.min(255, c.getRed()   + 125),
            Math.min(255, c.getGreen() + 125),
            Math.min(255, c.getBlue()  + 125)
        );
    }

    private void styleScrollBar(JScrollBar sb) {
        sb.setPreferredSize(new Dimension(0, 7));
        sb.setBackground(BG_PANEL);
        sb.setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() {
                thumbColor = new Color(0x55, 0x55, 0x55);
                trackColor = BG_PANEL;
            }
            @Override protected JButton createDecreaseButton(int o) { return invis(); }
            @Override protected JButton createIncreaseButton(int o) { return invis(); }
            private JButton invis() {
                JButton b = new JButton();
                b.setPreferredSize(new Dimension(0, 0));
                return b;
            }
        });
    }
}