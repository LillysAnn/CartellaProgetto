package org.example;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
public class EsempioGridlayout extends JFrame {

    // Orario scolastico usato dal generatore: 6 giorni x 8 righe (8-13 mattina, 13-14 PAUSA, 14-16 pomeriggio).
    // La riga della pausa (GeneratoreOrario.ORA_PAUSA) resta sempre grigia e vuota.
    private static final int GIORNI_ATTIVI = 6;
    private static final int ORE_ATTIVE = 8;

    private static final Color CELLA_OCCUPATA = new Color(0xDDE7FA);
    private static final Color CELLA_NON_ATTIVA = new Color(0xE9ECF2);

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
    JComboBox<String> sceltaDocente = new JComboBox<>();
    JLabel classeLabel = new JLabel("Classe: ");
    JComboBox<String> sceltaClasse = new JComboBox<>();
    private boolean vistaClasse = false;   // false = si vede l'orario del docente, true = quello della classe
    JTextField[][] celle = new JTextField[9][7];
    JLabel info = new JLabel("Carica un file CSV per iniziare");

    // dati importati dal CSV (null finche' non viene caricato un file)
    private GestoreDati gestore = null;
    private boolean caricamentoInCorso = false;

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

        riga1.add(Box.createHorizontalStrut(30));
        classeLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        classeLabel.setForeground(TESTO);
        riga1.add(classeLabel);
        sceltaClasse.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        sceltaClasse.setPreferredSize(new Dimension(200, 38));
        sceltaClasse.setMaximumSize(new Dimension(200, 38));
        sceltaClasse.addItem("(nessuna)");
        riga1.add(sceltaClasse);

        riga1.add(Box.createHorizontalStrut(150));

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
        pulisciTabella();   // colora di grigio le celle fuori dall'orario attivo

        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setOpaque(false);
        box.add(titoloPanel);
        box.add(Box.createVerticalStrut(100));
        info.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        info.setForeground(TESTO);
        info.setAlignmentX(Component.CENTER_ALIGNMENT);

        box.add(riga1);
        box.add(Box.createVerticalStrut(10));
        box.add(info);
        box.add(Box.createVerticalStrut(25));
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
                new csvLayout(EsempioGridlayout.this);
            }
        });

        // cambio docente nel menu a tendina -> mostra il suo orario
        sceltaDocente.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!caricamentoInCorso) {
                    vistaClasse = false;
                    azzeraSceltaClasse();
                    mostraDocente();
                }
            }
        });

        // cambio classe nel menu a tendina -> mostra l'orario della classe ("(nessuna)" = torna ai docenti)
        sceltaClasse.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!caricamentoInCorso) {
                    vistaClasse = sceltaClasse.getSelectedIndex() > 0;
                    aggiornaVista();
                }
            }
        });

        download.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                esportaPdf();
            }
        });

        generazione.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                generaOrario();
            }
        });
    }

    // Chiamato da csvLayout dopo la lettura del file: riempie il menu dei docenti e mostra il riepilogo
    public void caricaDati(GestoreDati nuoviDati, int errori, List<String> messaggiErrore) {
        gestore = nuoviDati;

        caricamentoInCorso = true;
        sceltaDocente.removeAllItems();
        for (String nome : gestore.getDocenti()) {
            sceltaDocente.addItem(nome);
        }
        sceltaClasse.removeAllItems();
        sceltaClasse.addItem("(nessuna)");
        for (String nome : nomiClassi()) {
            sceltaClasse.addItem(nome);
        }
        vistaClasse = false;
        caricamentoInCorso = false;
        if (sceltaDocente.getItemCount() > 0) {
            sceltaDocente.setSelectedIndex(0);
        }
        mostraDocente();

        StringBuilder msg = new StringBuilder();
        msg.append("Docenti: ").append(gestore.getDocenti().size());
        msg.append("\nAssegnazioni (docente-classe-materia): ").append(gestore.getNumeroAssegnazioni());
        msg.append("\nOre settimanali totali: ").append(gestore.getOreTotali());
        msg.append("\nRighe scartate: ").append(errori);
        for (int i = 0; i < messaggiErrore.size() && i < 10; i++) {
            msg.append("\n  - ").append(messaggiErrore.get(i));
        }
        if (messaggiErrore.size() > 10) {
            msg.append("\n  ... e altre ").append(messaggiErrore.size() - 10);
        }
        JOptionPane.showMessageDialog(this, msg.toString(), "Riepilogo importazione",
                errori == 0 ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
    }

    // Pulsante "Generazione": colloca le lezioni di tutti i docenti e aggiorna la tabella
    private void generaOrario() {
        if (gestore == null || gestore.getListaLezioni().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Carica prima un file CSV valido.", "Attenzione", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<Lezione> nonCollocate = GeneratoreOrario.genera(gestore.getListaLezioni(), giorni, GIORNI_ATTIVI, ORE_ATTIVE);
        aggiornaVista();

        // controllo indipendente: un orario che viola i vincoli non viene mai dichiarato valido
        List<String> violazioni = GeneratoreOrario.verifica(gestore.getListaLezioni());
        if (!violazioni.isEmpty()) {
            StringBuilder v = new StringBuilder("L'orario viola dei vincoli obbligatori:");
            for (int i = 0; i < violazioni.size() && i < 10; i++) {
                v.append("\n  - ").append(violazioni.get(i));
            }
            JOptionPane.showMessageDialog(this, v.toString(), "Vincoli violati", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int totali = gestore.getListaLezioni().size();
        if (nonCollocate.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Orario generato: tutte le " + totali + " lezioni sono state collocate.",
                    "Generazione", JOptionPane.INFORMATION_MESSAGE);
        } else {
            StringBuilder msg = new StringBuilder();
            msg.append("Collocate ").append(totali - nonCollocate.size()).append(" lezioni su ").append(totali)
                    .append(".\nLezioni rimaste da collocare: ").append(nonCollocate.size());
            for (int i = 0; i < nonCollocate.size() && i < 15; i++) {
                Lezione l = nonCollocate.get(i);
                msg.append("\n  - ").append(l.getDocente().getNome()).append(", ")
                        .append(l.getClasse().getNome()).append(", ").append(l.getMateria().getNome());
            }
            if (nonCollocate.size() > 15) {
                msg.append("\n  ... e altre ").append(nonCollocate.size() - 15);
            }
            List<String> cause = GeneratoreOrario.controllaCapacita(gestore.getListaLezioni(), GIORNI_ATTIVI, ORE_ATTIVE);
            if (!cause.isEmpty()) {
                msg.append("\n\nPossibili cause (ore richieste superiori agli slot disponibili):");
                for (int i = 0; i < cause.size() && i < 10; i++) {
                    msg.append("\n  - ").append(cause.get(i));
                }
            }
            JOptionPane.showMessageDialog(this, msg.toString(), "Orario incompleto", JOptionPane.WARNING_MESSAGE);
        }
    }

    // Mostra nella tabella l'orario del docente selezionato
    public void mostraDocente() {
        pulisciTabella();
        titolo.setText("Orario Docenti");
        String nome = (String) sceltaDocente.getSelectedItem();
        if (gestore == null || nome == null) {
            info.setText("Carica un file CSV per iniziare");
            return;
        }

        int totali = 0;
        int collocate = 0;
        for (Lezione l : gestore.getListaLezioni()) {
            if (!l.getDocente().getNome().equals(nome)) {
                continue;
            }
            totali++;
            int r = l.getOra();
            int c = indiceGiorno(l.getGiorno());
            if (r >= 1 && r <= 8 && c >= 1) {
                collocate++;
                JTextField cella = celle[r][c];
                cella.setText(l.getClasse().getNome());
                cella.setToolTipText(l.getMateria().getNome() + " - classe " + l.getClasse().getNome());
                cella.setBackground(CELLA_OCCUPATA);
            }
        }

        int assegnazioni = gestore.classiPerDocente.get(nome).size();
        String testo = nome + "  |  " + assegnazioni + " assegnazioni  |  " + totali + " ore settimanali";
        if (collocate == 0) {
            testo += "  |  premi Generazione per creare l'orario";
        } else {
            testo += "  |  collocate " + collocate + "/" + totali;
        }
        info.setText(testo);
    }

    // aggiorna la tabella in base alla vista attiva (docente o classe)
    private void aggiornaVista() {
        if (vistaClasse) {
            mostraClasse();
        } else {
            mostraDocente();
        }
    }

    // riporta il menu delle classi su "(nessuna)" senza scatenare eventi
    private void azzeraSceltaClasse() {
        boolean prima = caricamentoInCorso;
        caricamentoInCorso = true;
        if (sceltaClasse.getItemCount() > 0) {
            sceltaClasse.setSelectedIndex(0);
        }
        caricamentoInCorso = prima;
    }

    // Mostra nella tabella l'orario della classe selezionata: si ricostruisce dalle lezioni dei docenti
    // che insegnano in quella classe (la cella mostra la materia, il docente e' nel tooltip)
    public void mostraClasse() {
        pulisciTabella();
        titolo.setText("Orario Classi");
        String nome = (String) sceltaClasse.getSelectedItem();
        if (gestore == null || nome == null || sceltaClasse.getSelectedIndex() <= 0) {
            info.setText("Carica un file CSV per iniziare");
            return;
        }

        int totali = 0;
        int collocate = 0;
        for (Lezione l : gestore.getListaLezioni()) {
            if (!l.getClasse().getNome().equals(nome)) {
                continue;
            }
            totali++;
            int r = l.getOra();
            int c = indiceGiorno(l.getGiorno());
            if (r >= 1 && r <= 8 && c >= 1) {
                collocate++;
                JTextField cella = celle[r][c];
                cella.setText(l.getMateria().getNome());
                cella.setToolTipText(l.getMateria().getNome() + " - " + l.getDocente().getNome());
                cella.setBackground(CELLA_OCCUPATA);
            }
        }

        String testo = "Classe " + nome + "  |  " + totali + " ore settimanali";
        if (collocate == 0) {
            testo += "  |  premi Generazione per creare l'orario";
        } else {
            testo += "  |  collocate " + collocate + "/" + totali;
        }
        info.setText(testo);
    }

    // nomi delle classi presenti nel CSV (ordinati)
    private List<String> nomiClassi() {
        TreeSet<String> nomi = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Lezione l : gestore.getListaLezioni()) {
            nomi.add(l.getClasse().getNome());
        }
        return new ArrayList<>(nomi);
    }

    // tabella [ora - 1][giorno] di un docente o di una classe, pronta per il PDF
    private String[][] tabellaPerPdf(String nome, boolean perClasse) {
        String[][] t = new String[ORE_ATTIVE][GIORNI_ATTIVI];
        for (int r = 0; r < ORE_ATTIVE; r++) {
            for (int c = 0; c < GIORNI_ATTIVI; c++) {
                t[r][c] = "";
            }
        }
        for (Lezione l : gestore.getListaLezioni()) {
            String chi = perClasse ? l.getClasse().getNome() : l.getDocente().getNome();
            int r = l.getOra();
            int c = indiceGiorno(l.getGiorno());
            if (!chi.equals(nome) || r < 1 || r > ORE_ATTIVE || c < 1) {
                continue;
            }
            t[r - 1][c - 1] = perClasse ? l.getMateria().getNome() + " - " + l.getDocente().getNome()
                    : l.getClasse().getNome() + " - " + l.getMateria().getNome();
        }
        return t;
    }

    // Pulsante "PDF": una pagina per ogni docente e una per ogni classe
    private void esportaPdf() {
        boolean generato = false;
        if (gestore != null) {
            for (Lezione l : gestore.getListaLezioni()) {
                if (l.getGiorno() != null) {
                    generato = true;
                    break;
                }
            }
        }
        if (!generato) {
            JOptionPane.showMessageDialog(this, "Carica un CSV e genera l'orario prima di esportare.",
                    "Attenzione", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser scelta = new JFileChooser();
        scelta.setFileFilter(new FileNameExtensionFilter("File PDF", "pdf"));
        scelta.setSelectedFile(new File("orario.pdf"));
        if (scelta.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = scelta.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".pdf")) {
            file = new File(file.getParentFile(), file.getName() + ".pdf");
        }

        List<String> titoli = new ArrayList<>();
        List<String[][]> tabelle = new ArrayList<>();
        for (String d : gestore.getDocenti()) {
            titoli.add("Docente: " + d);
            tabelle.add(tabellaPerPdf(d, false));
        }
        for (String c : nomiClassi()) {
            titoli.add("Classe: " + c);
            tabelle.add(tabellaPerPdf(c, true));
        }
        try {
            GeneratoreOrario.esportaPdf(file.toPath(), titoli, tabelle, giorni);
            JOptionPane.showMessageDialog(this, "PDF salvato (" + tabelle.size() + " pagine):\n" + file.getAbsolutePath(),
                    "Esportazione", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Impossibile salvare il PDF.\nMessaggio: " + e.getMessage(),
                    "Errore", JOptionPane.ERROR_MESSAGE);
        }
    }

    // posizione della colonna del giorno (1 = Lunedi), -1 se non trovato
    private int indiceGiorno(String giorno) {
        for (int c = 1; c <= 6; c++) {
            if (giorni[c - 1].equals(giorno)) {
                return c;
            }
        }
        return -1;
    }

    // svuota le celle dell'orario (non le intestazioni) e ripristina i colori
    private void pulisciTabella() {
        for (int r = 1; r <= 8; r++) {
            for (int c = 1; c <= 6; c++) {
                JTextField cella = celle[r][c];
                cella.setText("");
                cella.setToolTipText(null);
                if (r > ORE_ATTIVE || c > GIORNI_ATTIVI || r == GeneratoreOrario.ORA_PAUSA) {
                    cella.setBackground(CELLA_NON_ATTIVA);
                } else if (r % 2 == 0) {
                    cella.setBackground(RIGA_PARI);
                } else {
                    cella.setBackground(RIGA_DISPARI);
                }
            }
        }
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
//14/11