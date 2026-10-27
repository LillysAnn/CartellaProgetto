package org.example;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

    public class GestoreDati {
        public static void main(String[] args) {
            String file = "ore_docente_classe.csv";
            Set<String> righeViste = new HashSet<>();

            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String riga;
                int numeroRiga = 0;
                int errori = 0;

                while ((riga = br.readLine()) != null) {
                    numeroRiga++;
                    if (numeroRiga == 1) {
                        continue;
                    }

                String[] campi = riga.split(",", -1);

                if (campi.length != 6) {
                    System.out.println("Riga " + numeroRiga + ": numero di colonne errato");
                    errori++;
                    continue;
                }

                for (int i = 0; i < campi.length; i++) {
                    campi[i] = campi[i].trim();
                }

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

                try {
                    int anno = Integer.parseInt(campi[1]);
                    if (anno < 1 || anno > 5) {
                        System.out.println("Riga " + numeroRiga + ": anno non valido");
                        errori++;
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Riga " + numeroRiga + ": anno non numerico");
                    errori++;
                }

                try {
                    int ore = Integer.parseInt(campi[5]);
                    if (ore <= 0) {
                        System.out.println("Riga " + numeroRiga + ": ore non valide");
                        errori++;
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Riga " + numeroRiga + ": ore non numeriche");
                    errori++;
                }

                String rigaNormalizzata = String.join(",", campi);

                if (!righeViste.add(rigaNormalizzata)) {
                    System.out.println("Riga " + numeroRiga + ": record duplicato");
                    errori++;
                }
            }

            System.out.println("\nControllo terminato. Errori trovati: " + errori);

        }catch (IOException e) {
            System.out.println("Errore nella lettura del file: " + e.getMessage());
        }
    }
}
