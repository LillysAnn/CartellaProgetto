package org.example;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
public class csvLayout extends JFrame{

    JPanel riga1 = new JPanel();
    JLabel percorso = new JLabel("Percorso file csv:");
    JTextField percorsoText = new JTextField();
    JButton sfoglia = new JButton("Sfoglia...");
    JButton ricerca = new JButton("Invio");
    Container cont = getContentPane();

    // finestra principale a cui consegnare i dati letti
    private final EsempioGridlayout finestraPrincipale;

    public csvLayout(EsempioGridlayout finestraPrincipale){
        super("Carica CSV");
        this.finestraPrincipale = finestraPrincipale;

        riga1.setLayout(new FlowLayout(FlowLayout.LEFT));
        riga1.add(percorso);
        percorsoText.setPreferredSize(new Dimension(220,24));
        riga1.add(percorsoText);
        riga1.add(sfoglia);
        riga1.add(ricerca);

        add(riga1,BorderLayout.CENTER);
        setSize(560,100);
        setLocationRelativeTo(finestraPrincipale);
        setResizable(false);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        sfoglia.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JFileChooser scelta = new JFileChooser();
                scelta.setFileFilter(new FileNameExtensionFilter("File CSV", "csv"));
                if (scelta.showOpenDialog(csvLayout.this) == JFileChooser.APPROVE_OPTION) {
                    percorsoText.setText(scelta.getSelectedFile().getAbsolutePath());
                }
            }
        });

        ricerca.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                caricaFile();
            }
        });

        percorsoText.addActionListener(new ActionListener() {   // Invio da tastiera
            public void actionPerformed(ActionEvent e) {
                caricaFile();
            }
        });

        setVisible(true);
    }

    private void caricaFile() {
        String p = percorsoText.getText().trim();
        // se il percorso e' stato copiato con le virgolette (Windows: "Copia come percorso") le togliamo
        if (p.length() >= 2 && p.startsWith("\"") && p.endsWith("\"")) {
            p = p.substring(1, p.length() - 1);
        }
        if (p.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Inserisci il percorso del file CSV.", "Attenzione", JOptionPane.WARNING_MESSAGE);
            return;
        }

        LettoreCSV lettore = new LettoreCSV();
        if (lettore.leggiEValida(p)) {
            finestraPrincipale.caricaDati(lettore.getGestoreDati(), lettore.getErrori(), lettore.getMessaggiErrore());
            dispose();
        }
    }

}

//14/11