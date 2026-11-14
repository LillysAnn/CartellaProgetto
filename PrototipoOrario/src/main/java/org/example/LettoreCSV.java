package org.example;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LettoreCSV {

    private GestoreDati g = new GestoreDati();
    private int errori = 0;
    private final List<String> messaggiErrore = new ArrayList<>();

    public GestoreDati getGestoreDati() {
        return g;
    }

    public int getErrori() {
        return errori;
    }

    public List<String> getMessaggiErrore() {
        return messaggiErrore;
    }

    private void segnala(int numeroRiga, String messaggio) {
        String testo = "Riga " + numeroRiga + ": " + messaggio;
        System.out.println(testo);
        messaggiErrore.add(testo);
        errori++;
    }

    // Ritorna true se il file e' stato letto (anche con righe scartate), false se non e' stato possibile aprirlo
    public boolean leggiEValida(String filePercorso) {
        g = new GestoreDati();
        errori = 0;
        messaggiErrore.clear();
        Set<String> righeViste = new HashSet<>();

        try (BufferedReader br = Files.newBufferedReader(Path.of(filePercorso), StandardCharsets.UTF_8)) {
            String riga;
            int numeroRiga = 0;

            while ((riga = br.readLine()) != null) {
                numeroRiga++;

                // Salta l'intestazione
                if (numeroRiga == 1) {
                    continue;
                }

                // Salta righe totalmente vuote nel file
                if (riga.trim().isEmpty()) {
                    continue;
                }

                String[] campi = riga.split(",", -1);

                // 1. Controllo numero di colonne
                if (campi.length != 6) {
                    segnala(numeroRiga, "numero di colonne errato");
                    continue;
                }

                // Pulizia spazi bianchi
                for (int i = 0; i < campi.length; i++) {
                    campi[i] = campi[i].trim();
                }

                // 2. Controllo campi vuoti
                boolean vuoto = false;
                for (String campo : campi) {
                    if (campo.isEmpty()) {
                        vuoto = true;
                        break;
                    }
                }

                if (vuoto) {
                    segnala(numeroRiga, "campo vuoto");
                    continue;
                }

                boolean rigaValida = true;

                // 3. Controllo Anno (deve essere tra 1 e 5)
                try {
                    int anno = Integer.parseInt(campi[1]);
                    if (anno < 1 || anno > 5) {
                        segnala(numeroRiga, "anno non valido (deve essere tra 1 e 5)");
                        rigaValida = false;
                    }
                } catch (NumberFormatException e) {
                    segnala(numeroRiga, "anno non numerico");
                    rigaValida = false;
                }

                // 4. Controllo Ore (deve essere > 0)
                try {
                    int ore = Integer.parseInt(campi[5]);
                    if (ore <= 0) {
                        segnala(numeroRiga, "ore non valide (devono essere > 0)");
                        rigaValida = false;
                    }
                } catch (NumberFormatException e) {
                    segnala(numeroRiga, "ore non numeriche");
                    rigaValida = false;
                }

                // Se anno o ore non sono validi, passa alla prossima riga senza registrarla o inviarla
                if (!rigaValida) {
                    continue;
                }

                // 5. Controllo Duplicati
                String rigaNormalizzata = String.join(",", campi);
                if (!righeViste.add(rigaNormalizzata)) {
                    segnala(numeroRiga, "record duplicato");
                    continue;
                }

                // --- RIGA VALIDA: la inviamo al GestoreDati ---
                // colonne: docente,anno,sezione,indirizzo,materia,ore
                String docente = campi[0];
                int anno = Integer.parseInt(campi[1]);
                char sezione = campi[2].charAt(0);
                String indirizzo = campi[3];
                String materia = campi[4];
                int ore = Integer.parseInt(campi[5]);

                g.aggiungiAssegnazione(docente, anno, sezione, indirizzo, materia, ore);
            }

            System.out.println("\nControllo terminato. Errori trovati: " + errori);
            return true;

        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Impossibile leggere il file.\nMessaggio: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }
}
//14/11