package org.example;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;


public class LettoreCSV {
    //spostare qui i controlli del file csv, vedere ultimo prompt di gemini per gestire queste classi
    //compito: controlli file, se la riga non ha errori la invia a gestore dati
    public void leggiEValida(/*String filePercorso*/) {

        String filePercorso = "ore_docente_classe.csv";
        Set<String> righeViste = new HashSet<>();
        int errori = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(filePercorso))) {
            String riga;
            int numeroRiga = 0;

            while ((riga = br.readLine()) != null) {
                numeroRiga++;

                // Salta l'intestazione
                if (numeroRiga == 1) {
                    continue;
                }

                // Salta righe totalmente vuote nel file
               /* if (riga.trim().isEmpty()) {
                    continue;
                }*/

                String[] campi = riga.split(",", -1);

                // 1. Controllo numero di colonne
                if (campi.length != 6) {
                    System.out.println("Riga " + numeroRiga + ": numero di colonne errato");
                    errori++;
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
                    System.out.println("Riga " + numeroRiga + ": campo vuoto");
                    errori++;
                    continue;
                }

                boolean rigaValida = true;//VEDI

                // 3. Controllo Anno (deve essere tra 1 e 5)
                try {
                    int anno = Integer.parseInt(campi[1]);
                    if (anno < 1 || anno > 5) {
                        System.out.println("Riga " + numeroRiga + ": anno non valido (deve essere tra 1 e 5)");
                        errori++;
                        rigaValida = false;
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Riga " + numeroRiga + ": anno non numerico");
                    errori++;
                    rigaValida = false;//VEDI
                }

                // 4. Controllo Ore (deve essere > 0)
                try {
                    int ore = Integer.parseInt(campi[5]);
                    if (ore <= 0) {
                        System.out.println("Riga " + numeroRiga + ": ore non valide (devono essere > 0)");
                        errori++;
                        rigaValida = false;//VEDI
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Riga " + numeroRiga + ": ore non numeriche");
                    errori++;
                    rigaValida = false;//VEDI
                }

                // Se anno o ore non sono validi, passa alla prossima riga senza registrarla o inviarla
                if (!rigaValida) {
                    continue;
                }

                // 5. Controllo Duplicati
                String rigaNormalizzata = String.join(",", campi);
                if (!righeViste.add(rigaNormalizzata)) {
                    System.out.println("Riga " + numeroRiga + ": record duplicato");
                    errori++;
                    continue;
                }

                // --- SE LA RIGA È VALIDA, LA Invii AL GESTORE DATI ---
                // gestoreDati.inviaRiga(campi);
            }

            System.out.println("\nControllo terminato. Errori trovati: " + errori);

        } catch (IOException e) {
            System.out.println("Errore nella lettura del file: " + e.getMessage());
        }
    }
}
