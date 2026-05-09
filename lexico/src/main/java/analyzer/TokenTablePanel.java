package analyzer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;

public class TokenTablePanel extends JPanel {

    // ── Colores ──────────────────────────────────────────────────────────────
    static final Color BG_PANEL     = new Color(0x2D, 0x2D, 0x2D);
    static final Color BG_TABLE_HDR = new Color(0x3A, 0x3A, 0x3A);
    static final Color BG_ROW_EVEN  = new Color(0x2A, 0x2A, 0x2A);
    static final Color BG_ROW_ODD   = new Color(0x22, 0x22, 0x22);
    static final Color BORDER_COLOR = new Color(0x3C, 0x3C, 0x3C);
    static final Color TEXT_MAIN    = new Color(0xD4, 0xD4, 0xD4);
    static final Color TEXT_DIM     = new Color(0x85, 0x85, 0x85);
    static final Color ACCENT_BLUE  = new Color(0x56, 0x9C, 0xD6);
    static final Color ACCENT_CYAN  = new Color(0x4E, 0xC9, 0xB0);
    static final Color ACCENT_YEL   = new Color(0xDC, 0xDC, 0xAA);
    static final Color TEXT_NUMBER  = new Color(0xB5, 0xCE, 0xA8);
    static final Color TEXT_STRING  = new Color(0xCE, 0x91, 0x78);
    static final Color ERR_RED      = new Color(0xF4, 0x47, 0x47);
    static final Color ERR_ORANGE   = new Color(0xCE, 0x91, 0x78);
    static final Color WARN_YELLOW  = new Color(0xE5, 0xC0, 0x7B);

    // ── Fuentes ──────────────────────────────────────────────────────────────
    static final Font TABLE_FONT = new Font("Consolas", Font.PLAIN, 12);
    static final Font HDR_FONT   = new Font("Segoe UI", Font.BOLD, 11);
    static final Font LBL_FONT   = new Font("Segoe UI", Font.BOLD, 11);

    // ── Modelos ──────────────────────────────────────────────────────────────
    private final DefaultTableModel tokenModel;
    private final DefaultTableModel errorModel;

    // ── Referencias directas a las tablas ────────────────────────────────────
    private JTable tokenTable;
    private JTable errorTable;

    // ════════════════════════════════════════════════════════════════════════
    public TokenTablePanel() {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(560, 0));
        setBackground(new Color(0x1E, 0x1E, 0x1E));

        tokenModel = buildTokenModel();
        errorModel = buildErrorModel();

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                buildTokenSection(), buildErrorSection());
        split.setResizeWeight(0.58);
        split.setDividerSize(4);
        split.setBorder(null);
        split.setBackground(new Color(0x1E, 0x1E, 0x1E));

        add(split, BorderLayout.CENTER);
    }

    // ════════════════════════════════════════════════════════════════════════
    // SECCIÓN TOKENS
    // ════════════════════════════════════════════════════════════════════════

    private DefaultTableModel buildTokenModel() {
        return new DefaultTableModel(
                new String[]{"#", "ESTADO", "LEXEMA", "LÍNEA"}, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
    }

    private JPanel buildTokenSection() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_PANEL);

        panel.add(buildSectionHeader("TABLA DE TOKENS"), BorderLayout.NORTH);

        tokenTable = new JTable(tokenModel);
        // AUTO_RESIZE_OFF: impide que Swing redistribuya anchos durante el scroll
        tokenTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        styleTable(tokenTable);

        tokenTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val,
                    boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                // convertRowIndexToModel garantiza que el índice de fila del
                // modelo coincida con el visual aunque haya sorting/filtering
                int modelRow = t.convertRowIndexToModel(row);
                setBackground(sel ? new Color(0x26, 0x40, 0x6E)
                                  : (modelRow % 2 == 0 ? BG_ROW_EVEN : BG_ROW_ODD));
                setForeground(colorForTokenCol(col, val));
                setFont(TABLE_FONT);
                setBorder(new EmptyBorder(3, 8, 3, 8));
                return this;
            }
        });

        // #=40  ESTADO=115  LEXEMA=185  LÍNEA=55
        int[] widths = {40, 115, 185, 55};
        for (int i = 0; i < widths.length; i++) {
            TableColumn c = tokenTable.getColumnModel().getColumn(i);
            c.setPreferredWidth(widths[i]);
            c.setMinWidth(widths[i]);
        }

        JScrollPane scroll = new JScrollPane(tokenTable);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG_ROW_EVEN);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        styleScrollBar(scroll.getVerticalScrollBar());
        styleScrollBar(scroll.getHorizontalScrollBar());

        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    /** Color de celda según columna y valor para la tabla de tokens. */
    private Color colorForTokenCol(int col, Object val) {
        if (col == 0) return TEXT_DIM;
        if (col != 2) return TEXT_MAIN;
        String tok = val != null ? val.toString() : "";
        return switch (tok) {
            case "KEYWORD"                        -> ACCENT_BLUE;
            case "ID"                             -> ACCENT_CYAN;
            case "OP_PAR", "CL_PAR",
                 "OP_BRAC", "CL_BRAC",
                 "OP_CURL", "CL_CURL"             -> ACCENT_YEL;
            case "NUMBER"                         -> TEXT_NUMBER;
            case "STRING"                         -> TEXT_STRING;
            case "OPERATOR", "ASSIGN", "DOT",
                 "COLON", "COMMA"                 -> new Color(0xD4, 0xD4, 0xD4);
            case "COMMENT"                        -> new Color(0x6A, 0x99, 0x55);
            default                               -> TEXT_DIM;
        };
    }

    // ════════════════════════════════════════════════════════════════════════
    // SECCIÓN ERRORES
    // ════════════════════════════════════════════════════════════════════════

    private DefaultTableModel buildErrorModel() {
        return new DefaultTableModel(
                new String[]{"#", "TOKEN", "DESCRIPCIÓN", "LEXEMA", "LÍNEA"}, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
    }

    private JPanel buildErrorSection() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_PANEL);
        panel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR));

        panel.add(buildSectionHeader("PANEL DE ERRORES"), BorderLayout.NORTH);

        errorTable = new JTable(errorModel);
        errorTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        styleTable(errorTable);

        errorTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val,
                    boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                // convertRowIndexToModel corrige desincronía visual/real
                int modelRow = t.convertRowIndexToModel(row);
                setBackground(sel ? new Color(0x26, 0x40, 0x6E)
                                  : (modelRow % 2 == 0 ? BG_ROW_EVEN : BG_ROW_ODD));

                Object codObj = errorModel.getValueAt(modelRow, 1);
                boolean esWarn = codObj != null && codObj.toString().startsWith("W");

                setForeground(switch (col) {
                    case 1  -> esWarn ? WARN_YELLOW : ERR_RED;
                    case 2  -> esWarn ? WARN_YELLOW : ERR_ORANGE;
                    case 0  -> TEXT_DIM;
                    default -> TEXT_MAIN;
                });
                setFont(TABLE_FONT);
                setBorder(new EmptyBorder(3, 8, 3, 8));
                return this;
            }
        });

        // #=40  TOKEN=130  DESCRIPCIÓN=235  LEXEMA=90  LÍNEA=55
        int[] widths = {40, 130, 235, 90, 55};
        for (int i = 0; i < widths.length; i++) {
            TableColumn c = errorTable.getColumnModel().getColumn(i);
            c.setPreferredWidth(widths[i]);
            c.setMinWidth(i == 2 ? 120 : widths[i]);
        }

        JScrollPane scroll = new JScrollPane(errorTable);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG_ROW_EVEN);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        styleScrollBar(scroll.getVerticalScrollBar());
        styleScrollBar(scroll.getHorizontalScrollBar());

        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    // ════════════════════════════════════════════════════════════════════════
    // API PÚBLICA — llamada desde AnalyzerIDE
    // ════════════════════════════════════════════════════════════════════════

    public void setTokens(List<Token> tokens) {
        SwingUtilities.invokeLater(() -> {
            tokenModel.setRowCount(0);
            int idx = 1;
            for (Token t : tokens) {
                tokenModel.addRow(new Object[]{
                    idx++,
                    t.getEstado(),
                    t.getLexema(),
                    t.getLinea()
                });
            }
            // Volver al inicio después de cargar
            if (tokenModel.getRowCount() > 0)
                tokenTable.scrollRectToVisible(tokenTable.getCellRect(0, 0, true));
        });
    }

    public void setErrors(List<ErrorEntry> errores) {
        SwingUtilities.invokeLater(() -> {
            errorModel.setRowCount(0);
            int idx = 1;
            for (ErrorEntry e : errores) {
                boolean esWarn = e.getTipo() == ErrorEntry.Tipo.SINTAXIS;
                String codigoDisplay = esWarn
                        ? e.getCodigo().replace("ERR", "WARN")
                        : e.getCodigo();
                errorModel.addRow(new Object[]{
                    idx++,
                    codigoDisplay,
                    e.getDescripcion(),
                    e.getLexema(),
                    e.getLinea()
                });
            }
            if (errorModel.getRowCount() > 0)
                errorTable.scrollRectToVisible(errorTable.getCellRect(0, 0, true));
        });
    }

    // ════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════════════════

    private JPanel buildSectionHeader(String titulo) {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_TABLE_HDR);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR),
                new EmptyBorder(6, 12, 6, 12)));
        JLabel lbl = new JLabel(titulo);
        lbl.setFont(LBL_FONT);
        lbl.setForeground(TEXT_MAIN);
        header.add(lbl, BorderLayout.WEST);
        return header;
    }

    private void styleTable(JTable table) {
        table.setBackground(BG_ROW_EVEN);
        table.setForeground(TEXT_MAIN);
        table.setFont(TABLE_FONT);
        table.setRowHeight(27);
        table.setGridColor(BORDER_COLOR);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setSelectionBackground(new Color(0x26, 0x40, 0x6E));
        table.setSelectionForeground(TEXT_MAIN);
        table.setFillsViewportHeight(true);
        table.setIntercellSpacing(new Dimension(0, 1));

        JTableHeader th = table.getTableHeader();
        th.setBackground(BG_TABLE_HDR);
        th.setForeground(TEXT_DIM);
        th.setFont(HDR_FONT);
        th.setReorderingAllowed(false);
        th.setResizingAllowed(true);
        th.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR));
        th.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val,
                    boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(t, val, sel, foc, r, c);
                setBackground(BG_TABLE_HDR);
                setForeground(TEXT_DIM);
                setFont(HDR_FONT);
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 1, BORDER_COLOR),
                        new EmptyBorder(4, 8, 4, 8)));
                return this;
            }
        });
    }

    private void styleScrollBar(JScrollBar sb) {
        sb.setBackground(BG_PANEL);
        sb.setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
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