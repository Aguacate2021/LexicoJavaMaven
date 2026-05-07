package analyzer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;


public class AnalyzerIDE extends JFrame {

    // ── Paleta ───────────────────────────────────────────────────────────────
    private static final Color BG_DARK      = new Color(0x1E, 0x1E, 0x1E);
    private static final Color BG_MID       = new Color(0x25, 0x25, 0x25);
    private static final Color BG_PANEL     = new Color(0x2D, 0x2D, 0x2D);
    private static final Color BG_TABLE_HDR = new Color(0x3A, 0x3A, 0x3A);
    private static final Color BORDER_COLOR = new Color(0x3C, 0x3C, 0x3C);
    private static final Color TEXT_MAIN    = new Color(0xD4, 0xD4, 0xD4);
    private static final Color TEXT_DIM     = new Color(0x85, 0x85, 0x85);
    private static final Color TEXT_KEYWORD = new Color(0x56, 0x9C, 0xD6);
    private static final Color TEXT_STRING  = new Color(0xCE, 0x91, 0x78);
    private static final Color TEXT_NUMBER  = new Color(0xB5, 0xCE, 0xA8);
    private static final Color TEXT_COMMENT = new Color(0x6A, 0x99, 0x55);
    private static final Color TEXT_FUNC    = new Color(0xDC, 0xDC, 0xAA);
    private static final Color ACCENT_GREEN = new Color(0x21, 0x7A, 0x3C);

    private static final Font MONO_FONT = new Font("Consolas", Font.PLAIN, 14);
    private static final Font LABEL_FONT = new Font("Segoe UI", Font.BOLD, 11);
    private static final Font BTN_FONT   = new Font("Segoe UI", Font.BOLD, 12);

    // ── Componentes ──────────────────────────────────────────────────────────
    private JTextPane      codeEditor;
    private TokenTablePanel tablePanel;
    private JLabel         statusLabel;

    // ── Lógica ───────────────────────────────────────────────────────────────
    private final Lexer lexer = new Lexer();
    private boolean highlightLock = false;

    // ════════════════════════════════════════════════════════════════════════
    public AnalyzerIDE() {
        super("Analyzer IDE v3.1 - compiler_v1.2");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1200, 740);
        setMinimumSize(new Dimension(900, 560));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_DARK);
        setLayout(new BorderLayout());

        add(buildTitleBar(),  BorderLayout.NORTH);
        add(buildEditor(),    BorderLayout.CENTER);
        add(buildTablePanel(),BorderLayout.EAST);
        add(buildStatusBar(), BorderLayout.SOUTH);
        CounterPanel counterPanel = new CounterPanel();
        add(counterPanel, BorderLayout.SOUTH);
        //loadSampleCode();
        setVisible(true);
    }

    // ════════════════════════════════════════════════════════════════════════
    // BARRA DE TÍTULO  
    // ════════════════════════════════════════════════════════════════════════
    private JPanel buildTitleBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(BG_MID);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR));
        bar.setPreferredSize(new Dimension(0, 36));

        JLabel title = new JLabel("Anlatorre - Lexico_v1.0", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        title.setForeground(TEXT_DIM);
        bar.add(title, BorderLayout.CENTER);

        return bar;
    }

    // ════════════════════════════════════════════════════════════════════════
    // EDITOR DE CÓDIGO
    // ════════════════════════════════════════════════════════════════════════
    private JPanel buildEditor() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_PANEL);
        panel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER_COLOR));

        // ── Cabecera con botones ─────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_TABLE_HDR);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR),
                new EmptyBorder(6, 12, 6, 12)));

        JLabel lbl = new JLabel("EDITOR DE CÓDIGO");
        lbl.setFont(LABEL_FONT);
        lbl.setForeground(TEXT_MAIN);
        header.add(lbl, BorderLayout.WEST);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btns.setOpaque(false);
        JButton btnAbrir    = makeButton("+ ABRIR",    new Color(0x3C, 0x3C, 0x3C), TEXT_MAIN);
        JButton btnCompilar = makeButton("- COMPILAR", new Color(0x3C, 0x3C, 0x3C), TEXT_MAIN);
        JButton btnExcel = makeButton("+- EXCEL", new Color(0x3C, 0x3C, 0x3C), TEXT_MAIN);
        btns.add(btnAbrir);
        btns.add(btnCompilar);
        btns.add(btnExcel);

        header.add(btns, BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);

        btnCompilar.addActionListener(e -> compilar());
        btnExcel.addActionListener(e -> exportToExcel());
        btnAbrir.addActionListener(e -> abrirArchivo());
        // ── Editor de texto ──────────────────────────────────────────────────
        codeEditor = new JTextPane();
        codeEditor.setBackground(BG_DARK);
        codeEditor.setForeground(TEXT_MAIN);
        codeEditor.setCaretColor(new Color(0xAE, 0xAF, 0xAD));
        codeEditor.setFont(MONO_FONT);
        codeEditor.setMargin(new Insets(4, 8, 4, 8));

        // Syntax highlight 
        codeEditor.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                SwingUtilities.invokeLater(() -> applySyntaxHighlight());
            }
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                SwingUtilities.invokeLater(() -> applySyntaxHighlight());
            }
            public void changedUpdate(javax.swing.event.DocumentEvent e) {}
        });

        // ── Números de línea ─────────────────────────────────────────────────
        JPanel lineNumbers = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(MONO_FONT);
                FontMetrics fm = g2.getFontMetrics();
                int lineH = fm.getHeight();
                int y     = fm.getAscent() + 8;
                int lines = codeEditor.getDocument()
                        .getDefaultRootElement().getElementCount();
                for (int i = 1; i <= lines; i++) {
                    g2.setColor(TEXT_DIM);
                    String num = String.valueOf(i);
                    g2.drawString(num, getWidth() - fm.stringWidth(num) - 6, y);
                    y += lineH;
                }
            }
        };
        lineNumbers.setPreferredSize(new Dimension(42, 0));
        lineNumbers.setBackground(new Color(0x1A, 0x1A, 0x1A));
        codeEditor.addCaretListener(e -> lineNumbers.repaint());

        JScrollPane scroll = new JScrollPane(codeEditor);
        scroll.setRowHeaderView(lineNumbers);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG_DARK);
        styleScrollBar(scroll.getVerticalScrollBar());
        styleScrollBar(scroll.getHorizontalScrollBar());

        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    // ════════════════════════════════════════════════════════════════════════
    // PANEL DERECHO
    // ════════════════════════════════════════════════════════════════════════
    private TokenTablePanel buildTablePanel() {
        tablePanel = new TokenTablePanel();
        return tablePanel;
    }

    // ════════════════════════════════════════════════════════════════════════
    // BARRA DE ESTADO
    // ════════════════════════════════════════════════════════════════════════
    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(new Color(0x00, 0x7A, 0xCC));
        bar.setPreferredSize(new Dimension(0, 24));
        bar.setBorder(new EmptyBorder(0, 12, 0, 12));

        statusLabel = new JLabel("Listo  |  ManuelCode 2026");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        statusLabel.setForeground(Color.WHITE);
        bar.add(statusLabel, BorderLayout.WEST);

        JLabel right = new JLabel("Anlatorre v1.0");
        right.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        right.setForeground(new Color(0xCC, 0xE0, 0xFF));
        bar.add(right, BorderLayout.EAST);

        return bar;
    }

    // ════════════════════════════════════════════════════════════════════════
    // COMPILAR / ANALIZAR
    // ════════════════════════════════════════════════════════════════════════
    private void compilar() {
        String codigo = codeEditor.getText();

        List<Token>      tokens  = lexer.tokenizar(codigo);
        List<ErrorEntry> errores = lexer.getErrores();
        ContadorTokens ct = new ContadorTokens();
        ct.contar(tokens);
        CounterPanel.actualizar(ct);
        Token.eliminarComentarios(tokens);
        
        tablePanel.setTokens(tokens);
        tablePanel.setErrors(errores);
        for (ErrorEntry e : errores) {
            System.out.println(e.getLexema()+" | "+e.getDescripcion()+" | "+e.getLinea());
        }
        statusLabel.setText(String.format(
                "Compilado  |  %d tokens  |  %d error(es)  |  ManuelCode 2026",
                tokens.size(), errores.size()));
        sintaxis parser = new sintaxis();
            parser.parsear(tokens);
    }
    // ════════════════════════════════════════════════════════════════════════
    // Exportar a Excel 
    // ════════════════════════════════════════════════════════════════════════
    private void exportToExcel() {
        List<Token>      tokens  = lexer.tokenizar(codeEditor.getText());
        List<ErrorEntry> errores = lexer.getErrores();
        ExcelExporter.exportar(this, tokens, errores);
    }
    // ════════════════════════════════════════════════════════════════════════
    // Abrir archivo .txt 
    // ════════════════════════════════════════════════════════════════════════
    private void abrirArchivo() {
        javax.swing.JFileChooser fileChooser = new javax.swing.JFileChooser();
    fileChooser.setDialogTitle("Seleccionar archivo .txt");

    // Filtro para solo archivos .txt
    fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Archivos de texto", "txt"));

    int resultado = fileChooser.showOpenDialog(this);

    if (resultado == javax.swing.JFileChooser.APPROVE_OPTION) {
        java.io.File archivo = fileChooser.getSelectedFile();

        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(archivo))) {
            StringBuilder contenido = new StringBuilder();
            String linea;

            while ((linea = br.readLine()) != null) {
                contenido.append(linea).append("\n");
            }

            codeEditor.setText(contenido.toString());

            // Si quieres compilar automáticamente
            // SwingUtilities.invokeLater(this::compilar);

        } catch (Exception e) {
            javax.swing.JOptionPane.showMessageDialog(this, "Error al leer el archivo");
            e.printStackTrace();
        }
    }
    }
    // ════════════════════════════════════════════════════════════════════════
    // SYNTAX HIGHLIGHT
    // ════════════════════════════════════════════════════════════════════════
    private void applySyntaxHighlight() {
        if (highlightLock) return;
        highlightLock = true;

        StyledDocument doc  = codeEditor.getStyledDocument();
        String         text = codeEditor.getText();
        int caretPos        = codeEditor.getCaretPosition();

        // Reset a color base
        SimpleAttributeSet normal = new SimpleAttributeSet();
        StyleConstants.setForeground(normal, TEXT_MAIN);
        doc.setCharacterAttributes(0, text.length(), normal, true);

        // Aplicar patrones por orden de prioridad
        colorPattern(doc, text, "\"[^\"]*\"|'[^']*'",          TEXT_STRING,  false);
        colorPattern(doc, text, "#.*",                          TEXT_COMMENT, false);
        colorPattern(doc, text, "\\b\\d+(\\.\\d+)?\\b",         TEXT_NUMBER,  false);
        colorPattern(doc, text,
                "\\b(" + String.join("|", new java.util.TreeSet<>(java.util.Arrays.asList(
                    "def","return","import","from","if","elif","else",
                    "while","for","in","not","and","or","True","False","None",
                    "class","pass","break","continue","try","except","finally",
                    "with","as","lambda","yield","global","nonlocal","del",
                    "raise","assert","print","range","len","type","int","str",
                    "float","list","dict","set","tuple","bool","input","open","math"
                ))) + ")\\b",
                TEXT_KEYWORD, true);
        colorPattern(doc, text, "\\b[A-Za-z_][A-Za-z0-9_]*(?=\\()", TEXT_FUNC, false);

        try { codeEditor.setCaretPosition(Math.min(caretPos, text.length())); }
        catch (Exception ignored) {}

        highlightLock = false;
    }

    private void colorPattern(StyledDocument doc, String text,
                               String regex, Color color, boolean bold) {
        SimpleAttributeSet attr = new SimpleAttributeSet();
        StyleConstants.setForeground(attr, color);
        StyleConstants.setBold(attr, bold);
        Matcher m = Pattern.compile(regex).matcher(text);
        while (m.find())
            doc.setCharacterAttributes(m.start(), m.end() - m.start(), attr, false);
    }

    // ════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════════════════
    private void loadSampleCode() {
        String sample =
            "import math\n\n" +
            "def area_circulo(r):\n" +
            "    return math.pi * r**2\n\n" +
            "radio = 5\n" +
            "resultado = area_circulo(radio)\n" +
            "print(\"El área es:\", resultado)\n";
        codeEditor.setText(sample);
        //SwingUtilities.invokeLater(this::compilar);
    }

    private JButton makeButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isPressed()  ? bg.darker()  :
                            getModel().isRollover() ? bg.brighter() : bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2.setColor(fg);
                g2.setFont(BTN_FONT);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(),
                        (getWidth()  - fm.stringWidth(getText())) / 2,
                        (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            }
        };
        btn.setPreferredSize(new Dimension(text.length() * 8 + 18, 27));
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private void styleScrollBar(JScrollBar sb) {
        sb.setBackground(BG_PANEL);
        sb.setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() {
                thumbColor = new Color(0x55, 0x55, 0x55);
                trackColor = BG_PANEL;
            }
            @Override protected JButton createDecreaseButton(int o) { return invisible(); }
            @Override protected JButton createIncreaseButton(int o) { return invisible(); }
            private JButton invisible() {
                JButton b = new JButton();
                b.setPreferredSize(new Dimension(0, 0));
                return b;
            }
        });
    }

    // ════════════════════════════════════════════════════════════════════════
    // MAIN
    // ════════════════════════════════════════════════════════════════════════
    public static void main(String[] args) {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {}

        UIManager.put("Table.background",    new Color(0x2A, 0x2A, 0x2A));
        UIManager.put("Table.foreground",    new Color(0xD4, 0xD4, 0xD4));
        UIManager.put("ScrollPane.background", new Color(0x1E, 0x1E, 0x1E));

        SwingUtilities.invokeLater(AnalyzerIDE::new);
    }
}