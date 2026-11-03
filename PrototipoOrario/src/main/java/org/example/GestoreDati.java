package org.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class GestoreDati {

    private static final String NOME_FILE_CSV = "ore_docente_classe.csv";

    // chiave: nome del professore, valore: le sue classi
    public final HashMap<String, ArrayList<Classe>> classiPerDocente = new HashMap<>();

    public GestoreDati() throws IOException {
        List<String> righe = Files.readAllLines(Path.of(NOME_FILE_CSV));

        for (int i = 1; i < righe.size(); i++) {
            String[] campi = righe.get(i).split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

            String docente = campi[0].trim();
            Classe classe = new Classe()
                    campi[1].trim() + campi[2].trim() + " " + campi[3].trim()
                    + " | " + campi[4].trim().replace("\"", "")
                    + " | " + campi[5].trim() + " ore";

            classiPerDocente.computeIfAbsent(docente, k -> new ArrayList<>()).add(classe);
        }
    }
}