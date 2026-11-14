package org.example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class GestoreDati {

    private final List<Lezione> listaLezioni = new ArrayList<>();
    private int oreTotali = 0;
    private int numeroAssegnazioni = 0;

    // chiave: nome del professore, valore: le sue classi
    public final HashMap<String, ArrayList<Classe>> classiPerDocente = new HashMap<>();

    public GestoreDati() {
    }

    public void aggiungiAssegnazione(String nomeDocente, int anno, char sezione, String indirizzo, String nomeMateria, int ore) {
        // Classe con indirizzo e materia nei campi giusti (prima la materia finiva nel campo indirizzo)
        Classe classe = new Classe(anno, sezione, indirizzo, ore);

        // Salviamo nella HashMap dei docenti
        classiPerDocente.computeIfAbsent(nomeDocente, k -> new ArrayList<>()).add(classe);

        Docente d = new Docente(nomeDocente);
        Materia m = new Materia(nomeMateria);

        // Generiamo le N lezioni atomiche (singole ore) per la griglia oraria
        for (int i = 1; i <= ore; i++) {
            String idLezione = nomeDocente + "_" + anno + sezione + indirizzo + "_" + nomeMateria + "_" + i;
            listaLezioni.add(new Lezione(idLezione, d, m, classe));
        }

        oreTotali += ore;
        numeroAssegnazioni++;
    }

    // Nomi dei docenti in ordine alfabetico (per riempire il menu a tendina)
    public List<String> getDocenti() {
        List<String> nomi = new ArrayList<>(classiPerDocente.keySet());
        Collections.sort(nomi, String.CASE_INSENSITIVE_ORDER);
        return nomi;
    }

    public int getOreTotali() {
        return oreTotali;
    }

    public int getNumeroAssegnazioni() {
        return numeroAssegnazioni;
    }

    // Metodo fondamentale per l'algoritmo dello Step 2 e Step 3
    public List<Lezione> getListaLezioni() {
        return listaLezioni;
    }
}
//14/11