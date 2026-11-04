package analyzer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.BoxLayout;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;

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
    private static final Color TEXT_SEM2   = new Color(0xFF, 0xA0, 0x40); // errores de Semántica 2: distinto al rojo del resto

    // Colores de categoría
    private static final Color CAT_ID  = new Color(0x1E, 0x3A, 0x5F);
    private static final Color CAT_COM = new Color(0x16, 0x3B, 0x26);
    private static final Color CAT_KW  = new Color(0x3B, 0x1F, 0x5C);
    private static final Color CAT_CST = new Color(0x4A, 0x2B, 0x12);
    private static final Color CAT_OP  = new Color(0x4A, 0x12, 0x12);
    private static final Color CAT_ERR = new Color(0x5A, 0x10, 0x10);

    // Fuentes
    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 12);
    private static final Font FONT_CAT   = new Font("Segoe UI", Font.BOLD, 11);
    private static final Font FONT_SUB   = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font FONT_VAL   = new Font("Consolas", Font.BOLD, 13);

    // ── Definición de categorías ────────────────────────────────────────────
    // ERRORES va primero: es el bloque que se muestra arriba. El orden de los
    // valores en actualizar(...) sigue exactamente este orden.
    private static final Object[][] CATS = {
        { "ERRORES", CAT_ERR,
          new String[]{"Léxicos","Sintácticos","Ámbito","Semántica 1","Semántica 2","Total"} },
        { "IDENTIFICADORES", CAT_ID,
          new String[]{"Cadena","Binario","Decimal","Octal","Hex","Real","Exp","Bool","Registro"} },
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
    private static final int IDX_SEM2  = 4;  // posición de "Semántica 2" dentro de ERRORES
    private static final int IDX_TOTAL = 5;  // posición de "Total" dentro de ERRORES
    private static final int TOTAL_COLS;

    static {
        int n = 0;
        for (Object[] cat : CATS) n += ((String[]) cat[2]).length;
        TOTAL_COLS = n;
    }

    // Etiquetas de valor, en el mismo orden que los valores de actualizar(...)
    private static JLabel[] valores;

    /** Alto con el que se ve completo (la columna de ERRORES: 9 filas + 3 títulos + márgenes). */
    public static final int ALTO_IDEAL = 26 + 9 * 20 + 3 * 24 + 2 * 8 + 16 + 6 + 18;

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
        title.setPreferredSize(new Dimension(0, 26));
        add(title, BorderLayout.NORTH);

        // Una tarjeta por categoría; cada contador es una fila "nombre ....... valor".
        JLabel[] vals = new JLabel[TOTAL_COLS];
        JPanel[] tarjetas = new JPanel[CATS.length];
        int col = 0;
        for (int ci = 0; ci < CATS.length; ci++) {
            String[] subs = (String[]) CATS[ci][2];
            JLabel[] mias = new JLabel[subs.length];
            for (int si = 0; si < subs.length; si++) {
                vals[col] = new JLabel("0", SwingConstants.RIGHT);
                mias[si] = vals[col];
                col++;
            }
            tarjetas[ci] = crearTarjeta((String) CATS[ci][0], (Color) CATS[ci][1], subs, mias);
        }
        valores = vals;
        pintarValores(new String[TOTAL_COLS]);

        // Cada columna es una lista vertical de contadores. ERRORES queda primero
        // (arriba a la izquierda), separado de los elementos aceptados.
        JPanel[] columnas = {
            columna(tarjetas[0], tarjetas[2], tarjetas[3]), // ERRORES, COMENTARIOS, PAL. RESERVADAS
            columna(tarjetas[1]),                            // IDENTIFICADORES
            columna(tarjetas[4]),                            // CONSTANTES
            columna(tarjetas[5]),                            // OPERADORES
        };

        JPanel body = new JPanel(new GridBagLayout());
        body.setBackground(BG_DARK);
        body.setBorder(new EmptyBorder(8, 8, 8, 8));
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.BOTH;
        g.anchor = GridBagConstraints.NORTH;
        g.weightx = 1;
        g.weighty = 1;
        for (int i = 0; i < columnas.length; i++) {
            g.gridx = i;
            g.insets = new Insets(0, i == 0 ? 0 : 5, 0, i == columnas.length - 1 ? 0 : 5);
            body.add(columnas[i], g);
        }

        JScrollPane scroll = new JScrollPane(body,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG_DARK);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        styleScrollBar(scroll.getVerticalScrollBar());
        add(scroll, BorderLayout.CENTER);

        setPreferredSize(new Dimension(380, ALTO_IDEAL));
        setMinimumSize(new Dimension(260, 120));
    }

    private JPanel columna(JPanel... tarjetas) {
        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setBackground(BG_DARK);
        for (int i = 0; i < tarjetas.length; i++) {
            if (i > 0) col.add(javax.swing.Box.createVerticalStrut(8));
            tarjetas[i].setAlignmentX(Component.LEFT_ALIGNMENT);
            col.add(tarjetas[i]);
        }
        return col;
    }

    private JPanel crearTarjeta(String titulo, Color catBg, String[] subs, JLabel[] vals) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(BG_VAL);
        card.setBorder(BorderFactory.createLineBorder(BORDER));

        JLabel h = new JLabel("  " + titulo);
        h.setOpaque(true);
        h.setBackground(catBg);
        h.setForeground(lighter(catBg));
        h.setFont(FONT_CAT);
        h.setPreferredSize(new Dimension(0, 24));
        card.add(h, BorderLayout.NORTH);

        JPanel filas = new JPanel(new GridLayout(subs.length, 1));
        filas.setBackground(BG_VAL);
        for (int i = 0; i < subs.length; i++) {
            JPanel fila = new JPanel(new BorderLayout());
            fila.setBackground(i % 2 == 0 ? BG_VAL : BG_SUB);
            fila.setBorder(new EmptyBorder(2, 10, 2, 10));
            JLabel n = new JLabel(subs[i]);
            n.setFont(FONT_SUB);
            n.setForeground(TEXT_MAIN);
            vals[i].setFont(FONT_VAL);
            fila.add(n, BorderLayout.WEST);
            fila.add(vals[i], BorderLayout.EAST);
            fila.setPreferredSize(new Dimension(0, 20));
            filas.add(fila);
        }
        card.add(filas, BorderLayout.CENTER);
        return card;
    }

    /** Escribe los valores y los colorea (errores en rojo, Semántica 2 en naranja). */
    private static void pintarValores(String[] txt) {
        int errCount = ((String[]) CATS[0][2]).length;
        for (int col = 0; col < TOTAL_COLS; col++) {
            String s = txt[col] == null ? "0" : txt[col];
            JLabel l = valores[col];
            l.setText(s);
            boolean cero = "0".equals(s);
            if (col < errCount) {
                l.setForeground(cero ? TEXT_ZERO
                        : col == IDX_TOTAL ? TEXT_TOTAL
                        : col == IDX_SEM2  ? TEXT_SEM2
                                           : TEXT_ERR);
            } else {
                l.setForeground(cero ? TEXT_ZERO : TEXT_NUMBER);
            }
        }
    }

    public static void actualizar(ContadorTokens c) {
        actualizar(c, c.comentarios, 0, 0, 0);
    }

    public static void actualizar(ContadorTokens c, int comentLinea, int comentMulti) {
        actualizar(c, comentLinea, comentMulti, 0, 0);
    }

    public static void actualizar(ContadorTokens c,
                                   int comentLinea,  int comentMulti,
                                   int errLexicos,   int errSintacticos) {
        actualizar(c, comentLinea, comentMulti, errLexicos, errSintacticos, 0, 0);
    }

    public static void actualizar(ContadorTokens c,
                                   int comentLinea,  int comentMulti,
                                   int errLexicos,   int errSintacticos,
                                   int errAmbito,    int errSemantica) {
        actualizar(c, comentLinea, comentMulti, errLexicos, errSintacticos,
                errAmbito, errSemantica, 0);
    }

    /** errSemantica = Semántica 1; errSemantica2 = Semántica 2 (contador independiente). */
    public static void actualizar(ContadorTokens c,
                                   int comentLinea,  int comentMulti,
                                   int errLexicos,   int errSintacticos,
                                   int errAmbito,    int errSemantica,
                                   int errSemantica2) {
        int errTotal = errLexicos + errSintacticos + errAmbito + errSemantica + errSemantica2;
        int[] vals = {
            // ERRORES (6)
            errLexicos, errSintacticos, errAmbito, errSemantica, errSemantica2, errTotal,
            // IDENTIFICADORES (9)
            c.idCadena, c.idBinario, c.idDecimal, c.idOctal,
            c.idHex,    c.idReal,    c.idExp,     c.idBool,
            c.idRegistro,
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
            c.opLogicos,    c.opTernario,c.opAsignacion,  c.opAgrup
        };
        SwingUtilities.invokeLater(() -> {
            if (valores == null) return;
            String[] txt = new String[TOTAL_COLS];
            for (int col = 0; col < vals.length && col < TOTAL_COLS; col++)
                txt[col] = String.valueOf(vals[col]);
            pintarValores(txt);
        });
    }

    public void limpiar() {
        SwingUtilities.invokeLater(() -> pintarValores(new String[TOTAL_COLS]));
    }

    // ════════════════════════════════════════════════════════════════════════
    // INTERNOS
    // ════════════════════════════════════════════════════════════════════════

    private Color lighter(Color c) {
        return new Color(
            Math.min(255, c.getRed()   + 125),
            Math.min(255, c.getGreen() + 125),
            Math.min(255, c.getBlue()  + 125)
        );
    }

    private void styleScrollBar(JScrollBar sb) {
        sb.setPreferredSize(new Dimension(8, 0));
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