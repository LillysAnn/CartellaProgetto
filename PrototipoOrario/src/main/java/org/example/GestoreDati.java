package org.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class GestoreDati {

    private static final String NOMEFILECSV = "ore_docente_classe.csv";

    // chiave: nome del professore, valore: le sue classi
    public final HashMap<String, ArrayList<Classe>> classiPerDocente = new HashMap<>();

    public GestoreDati() throws IOException {
        List<String> righe = Files.readAllLines(Path.of(NOMEFILECSV));
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
        }
    }
}