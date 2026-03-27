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

/**
 * CounterPanel — Barra inferior compacta del IDE.
 *
 * Muestra contadores de tokens por categoría y subcategoría.
 *
 * Integración mínima en AnalyzerIDE:
 *   CounterPanel counter = new CounterPanel();
 *   frame.add(counter, BorderLayout.SOUTH);
 *   // tras analizar:
 *   ContadorTokens ct = new ContadorTokens();
 *   ct.contar(tokens);
 *   counter.actualizar(ct);
 */
public class CounterPanel extends JPanel {

    // ── Paleta ─────────────────────────────────────────────────────────────
    private static final Color BG_DARK  = new Color(0x1E, 0x1E, 0x1E);
    private static final Color BG_PANEL = new Color(0x2D, 0x2D, 0x2D);
    private static final Color BG_HDR   = new Color(0x3A, 0x3A, 0x3A);
    private static final Color BG_SUB   = new Color(0x2A, 0x2A, 0x2A);
    private static final Color BG_VAL   = new Color(0x22, 0x22, 0x22);
    private static final Color BORDER   = new Color(0x3C, 0x3C, 0x3C);
    private static final Color TEXT_MAIN   = new Color(0xD4, 0xD4, 0xD4);
    private static final Color TEXT_DIM    = new Color(0x85, 0x85, 0x85);
    private static final Color TEXT_ZERO   = new Color(0x50, 0x50, 0x50);
    private static final Color TEXT_NUMBER = new Color(0xB5, 0xCE, 0xA8);

    // Color de fondo para cada categoría
    private static final Color CAT_ID  = new Color(0x1E, 0x3A, 0x5F);
    private static final Color CAT_COM = new Color(0x16, 0x3B, 0x26);
    private static final Color CAT_KW  = new Color(0x3B, 0x1F, 0x5C);
    private static final Color CAT_CST = new Color(0x4A, 0x2B, 0x12);
    private static final Color CAT_OP  = new Color(0x4A, 0x12, 0x12);
    private static final Color[] CAT_COLORS = { CAT_ID, CAT_COM, CAT_KW, CAT_CST, CAT_OP };

    // Fuentes
    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 11);
    private static final Font FONT_CAT   = new Font("Segoe UI", Font.BOLD, 10);
    private static final Font FONT_SUB   = new Font("Segoe UI", Font.ITALIC, 9);
    private static final Font FONT_VAL   = new Font("Consolas", Font.BOLD, 12);

    // ── Definición estática de categorías y subcategorías ──────────────────
    // Cada entrada: { "Etiqueta categoría", Color, String[]{"sub1","sub2",...} }
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
    };

    private static final int TOTAL_COLS;
    static {
        int n = 0;
        for (Object[] cat : CATS) n += ((String[]) cat[2]).length;
        TOTAL_COLS = n;
    }

    // ── Modelo de tabla (3 filas × TOTAL_COLS) ─────────────────────────────
    private static DefaultTableModel model;
    private static JTable table;

    // ══════════════════════════════════════════════════════════════════════
    public CounterPanel() {
        setLayout(new BorderLayout());
        setBackground(BG_DARK);
        setBorder(new MatteBorder(1, 0, 0, 0, BORDER));

        // ── Título ─────────────────────────────────────────────────────────
        JLabel title = new JLabel("  CONTADORES");
        title.setFont(FONT_TITLE);
        title.setForeground(TEXT_MAIN);
        title.setBackground(BG_HDR);
        title.setOpaque(true);
        title.setBorder(new MatteBorder(0, 0, 1, 0, BORDER));
        title.setPreferredSize(new Dimension(0, 22));
        add(title, BorderLayout.NORTH);

        // ── Tabla ──────────────────────────────────────────────────────────
        model = new DefaultTableModel(3, TOTAL_COLS) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(model);
        table.setTableHeader(null);  // sin JTableHeader nativo
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

                Color catBg = CAT_COLORS[catIndexForCol(col)];

                switch (row) {
                    case 0 -> {
                        // Categoría: solo texto en la 1.ª columna de cada grupo
                        setBackground(catBg);
                        setForeground(lighter(catBg));
                        setFont(FONT_CAT);
                        if (!isPrimerColDeCat(col)) setText("");
                    }
                    case 1 -> {
                        setBackground(BG_SUB);
                        setForeground(TEXT_DIM);
                        setFont(FONT_SUB);
                    }
                    default -> {
                        setBackground(BG_VAL);
                        String s = val != null ? val.toString() : "0";
                        setForeground("0".equals(s) ? TEXT_ZERO : TEXT_NUMBER);
                        setFont(FONT_VAL);
                    }
                }
                return this;
            }
        });

        initColumnas();

        // ── Scroll sólo horizontal ─────────────────────────────────────────
        JScrollPane scroll = new JScrollPane(table,
                JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG_VAL);
        styleScrollBar(scroll.getHorizontalScrollBar());
        add(scroll, BorderLayout.CENTER);

        // Alto fijo: 22 (título) + 18×3 (filas) + 8 (scrollbar) = 84px
        int fixedH = 22 + 18 * 3 + 8;
        setPreferredSize(new Dimension(0, fixedH));
        setMinimumSize(new Dimension(0, fixedH));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, fixedH));
    }

    // ══════════════════════════════════════════════════════════════════════
    // API PÚBLICA
    // ══════════════════════════════════════════════════════════════════════

    public static void actualizar(ContadorTokens c) {
        actualizar(c, c.comentarios, 0);
    }

    public static void actualizar(ContadorTokens c, int comentLinea, int comentMulti) {
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
            c.opPostfix,   c.opLogBin,  c.opControl,   c.opMat,
            c.opExp,       c.opTurno,   c.opRel,        c.opIgualdad,
            c.opLogicos,   c.opTernario,c.opAsignacion, c.opAgrup
        };
        SwingUtilities.invokeLater(() -> {
            for (int col = 0; col < vals.length && col < TOTAL_COLS; col++)
                try {
                    try {
                        model.setValueAt(String.valueOf(vals[col]), 2, col);
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            try {
                table.repaint();
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        });
    }

    /** Pone todos los contadores a 0. */
    public void limpiar() {
        SwingUtilities.invokeLater(() -> {
            for (int col = 0; col < TOTAL_COLS; col++)
                model.setValueAt("0", 2, col);
            table.repaint();
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    // INTERNOS
    // ══════════════════════════════════════════════════════════════════════

    /** Rellena etiquetas y anchos de columna. */
    private void initColumnas() {
        int col = 0;
        for (Object[] cat : CATS) {
            String   label = (String)   cat[0];
            String[] subs  = (String[]) cat[2];
            for (int si = 0; si < subs.length; si++) {
                model.setValueAt(si == 0 ? label : "", 0, col);
                model.setValueAt(subs[si], 1, col);
                model.setValueAt("0",      2, col);
                int w = label.equals("PAL. RESERVADAS") ? 82
                      : label.equals("OPERADORES")      ? 70
                      : 68;
                table.getColumnModel().getColumn(col).setPreferredWidth(w);
                table.getColumnModel().getColumn(col).setMinWidth(48);
                col++;
            }
        }
    }

    /** Índice de categoría para una columna dada. */
    private int catIndexForCol(int col) {
        int acc = 0;
        for (int ci = 0; ci < CATS.length; ci++) {
            acc += ((String[]) CATS[ci][2]).length;
            if (col < acc) return ci;
        }
        return CATS.length - 1;
    }

    /** ¿Es la primera columna de su categoría? */
    private boolean isPrimerColDeCat(int col) {
        int acc = 0;
        for (Object[] cat : CATS) {
            if (col == acc) return true;
            acc += ((String[]) cat[2]).length;
        }
        return false;
    }

    /** Calcula un tono más claro para el texto sobre el fondo de categoría. */
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