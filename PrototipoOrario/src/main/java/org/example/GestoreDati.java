package org.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class GestoreDati {

    //private static final String NOMEFILECSV = "ore_docente_classe.csv";
    private final List<Lezione> listaLezioni = new ArrayList<>();

    // chiave: nome del professore, valore: le sue classi
    public final HashMap<String, ArrayList<Classe>> classiPerDocente = new HashMap<>();

    public GestoreDati() /*throws IOException */{
       /* List<String> righe = Files.readAllLines(Path.of(NOMEFILECSV));
        for (int i = 1; i < righe.size(); i++) {
            if (righe.get(i).isBlank()) continue;

            String[] campi = righe.get(i).split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

            String docente = campi[0].trim();
            Classe classe = new Classe(
                    Integer.parseInt(campi[1].trim()),
                    campi[2].trim().charAt(0),
                    campi[3].trim(),
                    Integer.parseInt(campi[5].trim()));

            classiPerDocente.computeIfAbsent(docente, k -> new ArrayList<>()).add(classe);
        }*/
    }
    public void aggiungiAssegnazione(String nomeDocente, int anno, char sezione, String nomeMateria, int ore) {
        // Creiamo l'oggetto Classe secondo la struttura del tuo compagno
        Classe classe = new Classe(anno, sezione, nomeMateria, ore);

        // Salviamo nella HashMap del tuo compagno
        classiPerDocente.computeIfAbsent(nomeDocente, k -> new ArrayList<>()).add(classe);

        // --- INTEGRAZIONE PER L'ALGORITMO ---
        // Creiamo gli oggetti Docente e Materia (oppure usiamo stringhe/riferimenti)
        Docente d = new Docente(nomeDocente);
        Materia m = new Materia(nomeMateria);

        // Generiamo le N lezioni atomiche (singole ore) per la griglia oraria
        for (int i = 1; i <= ore; i++) {
            String idLezione = nomeDocente + "_" + anno + sezione + "_" + nomeMateria + "_" + i;
            listaLezioni.add(new Lezione(idLezione, d, m, classe));
        }
    }

    // Metodo fondamentale per l'algoritmo dello Step 2 e Step 3
    public List<Lezione> getListaLezioni() {
        return listaLezioni;
    }
}