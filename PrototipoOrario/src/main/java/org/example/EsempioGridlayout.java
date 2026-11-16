package org.example;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public class EsempioGridlayout extends JFrame {

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

    String[] giorni = {"Lunedi", "Martedi", "Mercoledi", "Giovedi", "Venerdi", "Sabato"};
    String[] ore = {"8:00", "9:00", "10:00", "11:00", "12:00", "13:00", "14:00", "15:00"};

    JLabel docente = new JLabel("Docente: ");
    JComboBox<String> sceltaDocente = new JComboBox<>();
    JLabel classeLabel = new JLabel("Classe: ");
    JComboBox<String> sceltaClasse = new JComboBox<>();
    private boolean vistaClasse = false;
    JTextField[][] celle = new JTextField[9][7];
    JLabel info = new JLabel("Carica un file CSV per iniziare");

    private GestoreDati gestore = null;
    private boolean caricamentoInCorso = false;

    JButton generazione = creaPulsante("Generazione", 150);
    JButton vincoli = creaPulsante("Vincoli", 100);
    JButton csv = creaPulsante("Inserisci CSV", 90);
    JButton downloadPdf = creaPulsante("Esporta PDF", 90);
    JButton downloadCsv = creaPulsante("Esporta CSV", 135);

    private static JButton creaPulsante(String testo, int larghezza) {
        JButton btn = new JButton(testo) {
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
        btn.setFont(new Font("Segoe UI", Font.BOLD, 16));
        btn.setPreferredSize(new Dimension(larghezza, 38));
        btn.setForeground(Color.WHITE);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setOpaque(false);
        return btn;
    }

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
        riga1.setOpaque(false);

        docente.setFont(new Font("Segoe UI", Font.BOLD, 20));
        docente.setForeground(TESTO);
        riga1.add(docente);

        sceltaDocente.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        sceltaDocente.setPreferredSize(new Dimension(220, 38));
        sceltaDocente.setMaximumSize(new Dimension(220, 38));
        riga1.add(sceltaDocente);

        riga1.add(Box.createHorizontalStrut(20));
        classeLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        classeLabel.setForeground(TESTO);
        riga1.add(classeLabel);

        sceltaClasse.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        sceltaClasse.setPreferredSize(new Dimension(180, 38));
        sceltaClasse.setMaximumSize(new Dimension(180, 38));
        sceltaClasse.addItem("(nessuna)");
        riga1.add(sceltaClasse);

        riga1.add(Box.createHorizontalStrut(20));
        riga1.add(generazione);
        riga1.add(Box.createHorizontalStrut(16));
        riga1.add(vincoli);
        riga1.add(Box.createHorizontalStrut(16));
        riga1.add(csv);
        riga1.add(Box.createHorizontalStrut(16));
        riga1.add(downloadPdf);
        riga1.add(Box.createHorizontalStrut(16));
        riga1.add(downloadCsv);

        generaTabella();
        pulisciTabella();

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

        csv.addActionListener(e -> new csvLayout(EsempioGridlayout.this));

        sceltaDocente.addActionListener(e -> {
            if (!caricamentoInCorso) {
                vistaClasse = false;
                azzeraSceltaClasse();
                mostraDocente();
            }
        });

        sceltaClasse.addActionListener(e -> {
            if (!caricamentoInCorso) {
                vistaClasse = sceltaClasse.getSelectedIndex() > 0;
                aggiornaVista();
            }
        });

        downloadPdf.addActionListener(e -> esportaOrario(true));
        downloadCsv.addActionListener(e -> esportaOrario(false));
        vincoli.addActionListener(e -> apriVincoli());
        generazione.addActionListener(e -> generaOrario());
    }

    public void caricaDati(GestoreDati nuoviDati, int errori, List<String> messaggiErrore) {
        gestore = nuoviDati;

        caricamentoInCorso = true;
        sceltaDocente.removeAllItems();
        for (String nome : gestore.docentiOrdinati()) {
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
        msg.append("Docenti: ").append(gestore.docentiOrdinati().size());
        msg.append("\nAssegnazioni (docente-classe-materia): ").append(gestore.getNumeroAssegnazioni());
        msg.append("\nOre settimanali totali: ").append(gestore.getOreTotali());
        msg.append("\nRighe scartate: ").append(errori);
        appendiElenco(msg, messaggiErrore, 10);
        JOptionPane.showMessageDialog(this, msg.toString(), "Riepilogo importazione",
                errori == 0 ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
    }

    private void generaOrario() {
        if (gestore == null || gestore.getListaLezioni().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Carica prima un file CSV valido.", "Attenzione", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<Lezione> nonCollocate = GeneratoreOrario.genera(gestore.getListaLezioni(), giorni, GIORNI_ATTIVI, ORE_ATTIVE, gestore);
        aggiornaVista();

        List<String> violazioni = GeneratoreOrario.verifica(gestore.getListaLezioni(), gestore, giorni);
        if (!violazioni.isEmpty()) {
            StringBuilder v = new StringBuilder("L'orario viola dei vincoli obbligatori:");
            appendiElenco(v, violazioni, 10);
            JOptionPane.showMessageDialog(this, v.toString(), "Vincoli violati", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int totali = gestore.getListaLezioni().size();
        if (nonCollocate.isEmpty()) {
            StringBuilder ok = new StringBuilder("Orario generato: tutte le " + totali + " lezioni sono state collocate.");
            List<String> prefNonRispettate = GeneratoreOrario.preferenzeNonRispettate(gestore.getListaLezioni(), gestore, giorni);
            if (!prefNonRispettate.isEmpty()) {
                ok.append("\n\nPreferenze non rispettate (").append(prefNonRispettate.size()).append("):");
                appendiElenco(ok, prefNonRispettate, 10);
            }
            JOptionPane.showMessageDialog(this, ok.toString(), "Generazione",
                    prefNonRispettate.isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
        } else {
            StringBuilder msg = new StringBuilder();
            msg.append("Collocate ").append(totali - nonCollocate.size()).append(" lezioni su ").append(totali)
                    .append(".\nLezioni rimaste da collocare: ").append(nonCollocate.size());
            List<String> elencoNonCollocate = new ArrayList<>();
            for (Lezione l : nonCollocate) {
                elencoNonCollocate.add(l.getDocente().getNome() + ", " + l.getClasse().toString() + ", " + l.getMateria().getNome());
            }
            appendiElenco(msg, elencoNonCollocate, 15);

            List<String> cause = GeneratoreOrario.controllaCapacita(gestore.getListaLezioni(), GIORNI_ATTIVI, ORE_ATTIVE, gestore);
            List<String> vincoliCoinvolti = GeneratoreOrario.vincoliDocentiCoinvolti(nonCollocate, gestore);
            if (!vincoliCoinvolti.isEmpty()) {
                msg.append("\n\nDocenti coinvolti con vincoli obbligatori (riducono gli slot disponibili):");
                appendiElenco(msg, vincoliCoinvolti, 10);
                msg.append("\nPer collocarle prova a togliere qualche vincolo obbligatorio.");
            }
            if (!cause.isEmpty()) {
                msg.append("\n\nPossibili cause (ore richieste superiori agli slot disponibili):");
                appendiElenco(msg, cause, 10);
            }
            JOptionPane.showMessageDialog(this, msg.toString(), "Orario incompleto", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void apriVincoli() {
        if (gestore == null || gestore.docentiOrdinati().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Carica prima un file CSV valido.", "Attenzione", JOptionPane.WARNING_MESSAGE);
            return;
        }
        final String nessuno = "(automatico)";
        final List<String> docenti = gestore.docentiOrdinati();
        String[] colonne = {"Docente", "Giorno libero (obbl.)", "No 1a ora 8:00 (obbl.)",
                "Evita ultima ora 12:00 (pref.)", "Ore compatte (pref.)"};
        final DefaultTableModel modello = new DefaultTableModel(colonne, 0) {
            @Override
            public Class<?> getColumnClass(int colonna) {
                return colonna >= 2 ? Boolean.class : String.class;
            }

            @Override
            public boolean isCellEditable(int riga, int colonna) {
                return colonna >= 1;
            }
        };
        for (String d : docenti) {
            String libero = gestore.giornoLibero.get(d);
            modello.addRow(new Object[]{d, libero == null ? nessuno : libero, gestore.senzaPrimaOra.contains(d),
                    gestore.evitaUltimaOra.contains(d), gestore.oreCompatte.contains(d)});
        }

        final JTable tab = new JTable(modello);
        tab.setRowHeight(28);
        tab.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        tab.setForeground(TESTO);
        tab.setGridColor(BORDO);

        JTableHeader header = tab.getTableHeader();
        header.setReorderingAllowed(false);
        header.setPreferredSize(new Dimension(0, 36));
        DefaultTableCellRenderer headerRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                lbl.setOpaque(true);
                lbl.setBackground(INTESTAZIONE);
                lbl.setForeground(Color.WHITE);
                lbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
                lbl.setHorizontalAlignment(SwingConstants.CENTER);
                lbl.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, BORDO));
                return lbl;
            }
        };
        header.setDefaultRenderer(headerRenderer);

        JComboBox<String> sceltaGiorno = new JComboBox<>();
        sceltaGiorno.addItem(nessuno);
        for (String g : giorni) {
            sceltaGiorno.addItem(g);
        }
        tab.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(sceltaGiorno));

        JLabel legenda = new JLabel("<html>OBBLIGATORI: il generatore non li viola mai; se non c'e' posto la lezione resta non collocata.<br>"
                + "PREFERENZE: il generatore prova a rispettarle, ma se non puo' le viola e lo segnala.<br>"
                + "Ogni docente ha comunque almeno un giorno libero: se non ne scegli uno, lo sceglie il programma.</html>");
        legenda.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        legenda.setForeground(TESTO);
        legenda.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        final JDialog finestra = new JDialog(this, "Vincoli dei docenti", true);
        JButton applica = new JButton("Applica");
        JButton annulla = new JButton("Annulla");
        applica.addActionListener(e -> {
            if (tab.isEditing()) {
                tab.getCellEditor().stopCellEditing();
            }
            gestore.giornoLibero.clear();
            gestore.senzaPrimaOra.clear();
            gestore.evitaUltimaOra.clear();
            gestore.oreCompatte.clear();
            for (int r = 0; r < modello.getRowCount(); r++) {
                String d = docenti.get(r);
                String libero = (String) modello.getValueAt(r, 1);
                if (libero != null && !libero.equals(nessuno)) {
                    gestore.giornoLibero.put(d, libero);
                }
                if ((Boolean) modello.getValueAt(r, 2)) {
                    gestore.senzaPrimaOra.add(d);
                }
                if ((Boolean) modello.getValueAt(r, 3)) {
                    gestore.evitaUltimaOra.add(d);
                }
                if ((Boolean) modello.getValueAt(r, 4)) {
                    gestore.oreCompatte.add(d);
                }
            }
            info.setText("Vincoli aggiornati: premi Generazione per applicarli");
            finestra.dispose();
        });
        annulla.addActionListener(e -> finestra.dispose());

        JPanel pulsanti = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        pulsanti.add(annulla);
        pulsanti.add(applica);

        finestra.setLayout(new BorderLayout());
        finestra.add(legenda, BorderLayout.NORTH);
        finestra.add(new JScrollPane(tab), BorderLayout.CENTER);
        finestra.add(pulsanti, BorderLayout.SOUTH);
        finestra.setSize(950, 600);
        finestra.setLocationRelativeTo(this);
        finestra.setVisible(true);
    }

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
                cella.setText(l.getClasse().toString());
                cella.setToolTipText(l.getMateria().getNome() + " - classe " + l.getClasse().toString());
                cella.setBackground(CELLA_OCCUPATA);
            }
        }

        int assegnazioni = gestore.DocenteClassi.get(nome).size();
        aggiornaInfoStato(nome + "  |  " + assegnazioni + " assegnazioni  |  " + totali + " ore settimanali", collocate, totali);
    }

    private void aggiornaVista() {
        if (vistaClasse) {
            mostraClasse();
        } else {
            mostraDocente();
        }
    }

    private void azzeraSceltaClasse() {
        boolean prima = caricamentoInCorso;
        caricamentoInCorso = true;
        if (sceltaClasse.getItemCount() > 0) {
            sceltaClasse.setSelectedIndex(0);
        }
        caricamentoInCorso = prima;
    }

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
        Map<String, String> teoria = GeneratoreOrario.docenteTeoria(gestore.getListaLezioni());
        for (int passo = 0; passo < 2; passo++) {
            for (Lezione l : gestore.getListaLezioni()) {
                if (!l.getClasse().toString().equals(nome)) {
                    continue;
                }
                String dt = teoria.get(nome + "|" + l.getMateria().getNome());
                boolean laboratorio = dt != null && !dt.equals(l.getDocente().getNome());
                if (laboratorio != (passo == 1)) {
                    continue;
                }
                if (!laboratorio) {
                    totali++;
                }
                int r = l.getOra();
                int c = indiceGiorno(l.getGiorno());
                if (r >= 1 && r <= 8 && c >= 1) {
                    JTextField cella = celle[r][c];
                    if (laboratorio) {
                        cella.setToolTipText(cella.getToolTipText() + " + " + l.getDocente().getNome() + " (laboratorio)");
                    } else {
                        collocate++;
                        cella.setText(l.getMateria().getNome());
                        cella.setToolTipText(l.getMateria().getNome() + " - " + l.getDocente().getNome());
                        cella.setBackground(CELLA_OCCUPATA);
                    }
                }
            }
        }

        aggiornaInfoStato("Classe " + nome + "  |  " + totali + " ore settimanali", collocate, totali);
    }

    private void aggiornaInfoStato(String prefisso, int collocate, int totali) {
        if (collocate == 0) {
            info.setText(prefisso + "  |  premi Generazione per creare l'orario");
        } else {
            info.setText(prefisso + "  |  collocate " + collocate + "/" + totali);
        }
    }

    private static void appendiElenco(StringBuilder sb, List<String> righe, int max) {
        for (int i = 0; i < righe.size() && i < max; i++) {
            sb.append("\n  - ").append(righe.get(i));
        }
        if (righe.size() > max) {
            sb.append("\n  ... e altre ").append(righe.size() - max);
        }
    }

    private List<String> nomiClassi() {
        TreeSet<String> nomi = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Lezione l : gestore.getListaLezioni()) {
            nomi.add(l.getClasse().toString());
        }
        return new ArrayList<>(nomi);
    }

    private String[][] tabellaOrario(String nome, boolean perClasse, boolean perCsv) {
        String[][] t = new String[ORE_ATTIVE][GIORNI_ATTIVI];
        for (int r = 0; r < ORE_ATTIVE; r++) {
            for (int c = 0; c < GIORNI_ATTIVI; c++) {
                t[r][c] = "";
            }
        }
        String sep = perCsv ? "\n" : " - ";
        for (Lezione l : gestore.getListaLezioni()) {
            String chi = perClasse ? l.getClasse().toString() : l.getDocente().getNome();
            int r = l.getOra();
            int c = indiceGiorno(l.getGiorno());
            if (!chi.equals(nome) || r < 1 || r > ORE_ATTIVE || c < 1) {
                continue;
            }
            if (perClasse && !t[r - 1][c - 1].isEmpty()) {
                t[r - 1][c - 1] += (perCsv ? "\n+ " : " + ") + l.getDocente().getNome();
            } else {
                t[r - 1][c - 1] = perClasse ? l.getMateria().getNome() + sep + l.getDocente().getNome()
                        : l.getClasse().toString() + sep + l.getMateria().getNome();
            }
        }
        return t;
    }

    private void esportaOrario(boolean pdf) {
        if (gestore == null || gestore.getListaLezioni().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Carica un CSV e genera l'orario prima di esportare.",
                    "Attenzione", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String selezionato = vistaClasse
                ? (sceltaClasse.getSelectedIndex() > 0 ? (String) sceltaClasse.getSelectedItem() : null)
                : (String) sceltaDocente.getSelectedItem();
        if (selezionato == null) {
            JOptionPane.showMessageDialog(this, "Seleziona un docente o una classe da esportare.",
                    "Attenzione", JOptionPane.WARNING_MESSAGE);
            return;
        }

        boolean haLezioniCollocate = false;
        for (Lezione l : gestore.getListaLezioni()) {
            String chi = vistaClasse ? l.getClasse().toString() : l.getDocente().getNome();
            if (chi.equals(selezionato) && l.getGiorno() != null) {
                haLezioniCollocate = true;
                break;
            }
        }
        if (!haLezioniCollocate) {
            JOptionPane.showMessageDialog(this, "Genera prima l'orario per poter esportare la tabella visualizzata.",
                    "Attenzione", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String titoloTabella = (vistaClasse ? "Classe: " : "Docente: ") + selezionato;
        String nomeBase = "orario_" + selezionato.trim().replaceAll("[^a-zA-Z0-9_-]+", "_");
        String estensione = pdf ? "pdf" : "csv";
        String descrizione = pdf ? "File PDF" : "File CSV";

        JFileChooser scelta = new JFileChooser();
        scelta.setFileFilter(new FileNameExtensionFilter(descrizione, estensione));
        scelta.setSelectedFile(new File(nomeBase + "." + estensione));
        if (scelta.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = scelta.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith("." + estensione)) {
            file = new File(file.getParentFile(), file.getName() + "." + estensione);
        }

        List<String> titoli = new ArrayList<>();
        titoli.add(titoloTabella);
        List<String[][]> tabelle = new ArrayList<>();
        tabelle.add(tabellaOrario(selezionato, vistaClasse, !pdf));
        try {
            if (pdf) {
                GeneratoreOrario.esportaPdf(file.toPath(), titoli, tabelle, giorni);
            } else {
                GeneratoreOrario.esportaCsv(file.toPath(), titoli, tabelle, giorni);
            }
            JOptionPane.showMessageDialog(this,
                    (pdf ? "PDF" : "CSV") + " salvato per " + titoloTabella + ":\n" + file.getAbsolutePath(),
                    "Esportazione", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Impossibile salvare il file.\nMessaggio: " + e.getMessage(),
                    "Errore", JOptionPane.ERROR_MESSAGE);
        }
    }

    private int indiceGiorno(String giorno) {
        for (int c = 1; c <= 6; c++) {
            if (giorni[c - 1].equals(giorno)) {
                return c;
            }
        }
        return -1;
    }

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
        for (int r = 0; r < 9; ++r) {
            for (int c = 0; c < 7; ++c) {
                JTextField cella = new JTextField();
                cella.setPreferredSize(new Dimension(120, 60));
                cella.setEditable(false);
                cella.setHorizontalAlignment(JTextField.CENTER);
                cella.setFocusable(false);
                cella.setBorder(BorderFactory.createLineBorder(BORDO));

                if (r == 0 && c == 0) {
                    cella.setBackground(INTESTAZIONE);
                } else if (r == 0) {
                    cella.setFont(new Font("Segoe UI", Font.BOLD, 15));
                    cella.setBackground(INTESTAZIONE);
                    cella.setForeground(Color.WHITE);
                    cella.setText(giorni[c - 1]);
                } else if (c == 0) {
                    cella.setFont(new Font("Segoe UI", Font.BOLD, 15));
                    cella.setBackground(ORA_SFONDO);
                    cella.setForeground(PRIMARIO);
                    cella.setText(ore[r - 1]);
                } else {
                    cella.setFont(new Font("Segoe UI", Font.PLAIN, 14));
                    cella.setForeground(TESTO);
                    cella.setBackground(r % 2 == 0 ? RIGA_PARI : RIGA_DISPARI);
                }
                celle[r][c] = cella;
                tabella.add(cella);
            }
        }
    }
}