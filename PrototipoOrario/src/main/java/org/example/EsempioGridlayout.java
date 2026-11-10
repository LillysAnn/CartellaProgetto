package org.example;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
public class EsempioGridlayout extends JFrame {

    private static final Color SFONDO = new Color(0xF3F5F9);
    private static final Color CARD = Color.WHITE;
    private static final Color PRIMARIO = new Color(0x2F4B8F);
    private static final Color PRIMARIO_HOVER = new Color(0x3F62BA);
    private static final Color INTESTAZIONE = new Color(0x2F4B8F);
    private static final Color ORA_SFONDO = new Color(0xE3E9F6);
    private static final Color RIGA_PARI = new Color(0xFFFFFF);
    private static final Color RIGA_DISPARI = new Color(0xF7F9FD);
    private static final Color BORDO = new Color(0xD5DBE8);
    private static final Color TESTO = new Color(0x2B3245);

    static {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
    }

    JPanel riga1 = new JPanel();
    JPanel titoloPanel = new JPanel();
    JLabel titolo = new JLabel("Orario Docenti");
    JPanel box = new JPanel();
    JPanel tabella = new JPanel();
    JPanel wrapper = new JPanel();
    Container cFinal = new Container();
    String[][] orario = new String[9][7];
    String[] giorni = {"Lunedi", "Martedi", "Mercoledi", "Giovedi", "Venerdi", "Sabato"};
    String[] ore = {"8:00", "9:00", "10:00", "11:00", "12:00", "13:00", "14:00", "15:00"};
    JTextField campi = new JTextField();
    ;
    JLabel docente = new JLabel("Docente: ");
    JComboBox sceltaDocente = new JComboBox();
    JTextField[][] celle = new JTextField[9][7];

    JButton download = new JButton("PDF") {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (getModel().isPressed()) {
                g2.setColor(PRIMARIO.darker());
            } else if (getModel().isRollover()) {
                g2.setColor(PRIMARIO_HOVER);
            } else {
                g2.setColor(PRIMARIO);
            }
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
            g2.dispose();
            super.paintComponent(g);
        }
    };
    JButton generazione = new JButton("Generazione") {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (getModel().isPressed()) {
                g2.setColor(PRIMARIO.darker());
            } else if (getModel().isRollover()) {
                g2.setColor(PRIMARIO_HOVER);
            } else {
                g2.setColor(PRIMARIO);
            }
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
            g2.dispose();
            super.paintComponent(g);
        }
    };
    JButton csv = new JButton("CSV") {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (getModel().isPressed()) {
                g2.setColor(PRIMARIO.darker());
            } else if (getModel().isRollover()) {
                g2.setColor(PRIMARIO_HOVER);
            } else {
                g2.setColor(PRIMARIO);
            }
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
            g2.dispose();
            super.paintComponent(g);
        }
    };

    public EsempioGridlayout() {
        super("Tabella orari");
        tabella.setLayout(new GridLayout(9, 7));
        tabella.setBackground(CARD);
        tabella.setBorder(BorderFactory.createLineBorder(BORDO, 1));

        titoloPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
        titoloPanel.setOpaque(false);
        titolo.setFont(new Font("Georgia", Font.BOLD + Font.ITALIC, 50));
        titolo.setForeground(PRIMARIO);
        titolo.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        titoloPanel.add(titolo);

        riga1.setLayout(new BoxLayout(riga1, BoxLayout.X_AXIS));
        riga1.setSize(new Dimension(50, 30));
        riga1.setOpaque(false);

        docente.setFont(new Font("Segoe UI", Font.BOLD, 20));
        docente.setForeground(TESTO);
        riga1.add(docente);

        sceltaDocente.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        sceltaDocente.setPreferredSize(new Dimension(240, 38));
        sceltaDocente.setMaximumSize(new Dimension(240, 38));
        riga1.add(sceltaDocente);

        riga1.add(Box.createHorizontalStrut(500));

        download.setFont(new Font("Segoe UI", Font.BOLD, 16));
        download.setForeground(Color.WHITE);
        download.setContentAreaFilled(false);
        download.setBorderPainted(false);
        download.setFocusPainted(false);
        download.setOpaque(false);
        download.setPreferredSize(new Dimension(100, 38));
        csv.setFont(new Font("Segoe UI", Font.BOLD, 16));
        csv.setPreferredSize(new Dimension(100, 38));
        csv.setForeground(Color.WHITE);
        csv.setContentAreaFilled(false);
        csv.setBorderPainted(false);
        csv.setFocusPainted(false);
        csv.setOpaque(false);
        csv.setPreferredSize(new Dimension(100, 38));
        generazione.setFont(new Font("Segoe UI", Font.BOLD, 16));
        generazione.setPreferredSize(new Dimension(150, 38));
        generazione.setOpaque(false);
        generazione.setFocusPainted(false);
        generazione.setForeground(Color.WHITE);
        generazione.setContentAreaFilled(false);
        generazione.setBorderPainted(false);

        riga1.add(generazione);
        riga1.add(Box.createHorizontalStrut(30));
        riga1.add(csv);
        riga1.add(Box.createHorizontalStrut(30));
        riga1.add(download);

        generaTabella();

        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setOpaque(false);
        box.add(titoloPanel);
        box.add(Box.createVerticalStrut(100));
        box.add(riga1);
        box.add(Box.createVerticalStrut(40));
        box.add(tabella);

        wrapper.setLayout(new GridBagLayout());
        wrapper.setBackground(SFONDO);
        wrapper.add(box);

        cFinal.setLayout(new BorderLayout());
        cFinal.add(wrapper, BorderLayout.CENTER);


        add(cFinal, BorderLayout.CENTER);
        setExtendedState(JFrame.MAXIMIZED_BOTH);

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setVisible(true);

        csv.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                new csvLayout();
            }
        });
    }

    public void generaTabella() {
        for (int r = 1; r <= 8; ++r) {
            orario[r][0] = ore[r - 1];
            for (int c = 1; c <= 6; ++c) {
                orario[0][c] = giorni[c - 1];
            }
        }

        for (int r = 0; r < 9; ++r) {
            for (int c = 0; c < 7; ++c) {
                if (orario[r][c] != null && (orario[r][c].equals("Lunedi") || orario[r][c].equals("Martedi") || orario[r][c].equals("Mercoledi")
                        || orario[r][c].equals("Giovedi") || orario[r][c].equals("Venerdi") || orario[r][c].equals("Sabato")
                        || orario[r][c].equals("8:00") || orario[r][c].equals("9:00") || orario[r][c].equals("10:00")
                        || orario[r][c].equals("11:00") || orario[r][c].equals("12:00") || orario[r][c].equals("13:00")
                        || orario[r][c].equals("14:00") || orario[r][c].equals("15:00"))) {

                    JTextField temp2 = new JTextField();
                    temp2.setPreferredSize(new Dimension(120, 60));
                    temp2.setEditable(false);
                    temp2.setHorizontalAlignment(JTextField.CENTER);
                    temp2.setFocusable(false);
                    temp2.setFont(new Font("Segoe UI", Font.BOLD, 15));
                    temp2.setText(orario[r][c]);
                    if (r == 0) {                       // intestazione giorni
                        temp2.setBackground(INTESTAZIONE);
                        temp2.setForeground(Color.WHITE);
                    } else {                            // colonna ore
                        temp2.setBackground(ORA_SFONDO);
                        temp2.setForeground(PRIMARIO);
                    }
                    temp2.setBorder(BorderFactory.createLineBorder(BORDO));
                    celle[r][c] = temp2;
                    tabella.add(temp2);
                } else {
                    JTextField temp = new JTextField();
                    temp.setPreferredSize(new Dimension(120, 60));
                    temp.setEditable(false);
                    temp.setHorizontalAlignment(JTextField.CENTER);
                    temp.setBorder(BorderFactory.createLineBorder(BORDO));
                    temp.setFocusable(false);
                    temp.setFont(new Font("Segoe UI", Font.PLAIN, 14));
                    temp.setForeground(TESTO);
                    if (r == 0 || c == 0) {
                        temp.setBackground(INTESTAZIONE);   // cella in alto a sinistra
                    } else {
                        if (r % 2 == 0) {
                            temp.setBackground(RIGA_PARI);
                        } else {
                            temp.setBackground(RIGA_DISPARI);
                        }
                    }
                    if (orario[r][c] != null) {
                        temp.setText(orario[r][c]);         // se già compilata
                    }
                    celle[r][c] = temp;
                    tabella.add(temp);
                }
            }
        }
    }
}
