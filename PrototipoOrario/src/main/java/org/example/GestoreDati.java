package org.example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class GestoreDati {

    private final ArrayList<Lezione> listaLezioni = new ArrayList<>();
    private int oreTotali = 0;
    private int numeroAssegnazioni = 0;

    public final HashMap<String, ArrayList<Classe>> DocenteClassi = new HashMap<>();

    public final HashMap<String, String> giornoLibero = new HashMap<>();
    public final HashSet<String> senzaPrimaOra = new HashSet<>();

    public final HashSet<String> evitaUltimaOra = new HashSet<>();
    public final HashSet<String> oreCompatte = new HashSet<>();

    public GestoreDati() {
    }

   public void aggiungiAssegnazione(String nomeDocente, int anno, char sezione, String indirizzo, String nomeMateria, int ore) {

        Classe classe = new Classe(anno, sezione, indirizzo, ore);

        DocenteClassi.computeIfAbsent(nomeDocente, k -> new ArrayList<>()).add(classe);

        Docente d = new Docente(nomeDocente);
        Materia m = new Materia(nomeMateria);

        // generazione lezioni atomiche
        for (int i = 1; i <= ore; i++) {
            String idLezione = nomeDocente + "_" + anno + sezione + indirizzo + "_" + nomeMateria + "_" + i;
            listaLezioni.add(new Lezione(idLezione, d, m, classe));
        }

        oreTotali += ore;
        numeroAssegnazioni++;
    }

    // Nomi dei docenti in ordine alfabetico
    public ArrayList<String> docentiOrdinati() {
        ArrayList<String> nomi = new ArrayList<>(DocenteClassi.keySet());//prende eleco docenti dalle chiavi docenteclassi
        Collections.sort(nomi, String.CASE_INSENSITIVE_ORDER);//ordine alfabetico
        return nomi;
    }

    public int getOreTotali() {
        return oreTotali;
    }

    public int getNumeroAssegnazioni() {
        return numeroAssegnazioni;
    }

    public List<Lezione> getListaLezioni() {
        return listaLezioni;//contiene lezioni atomiche
    }
}
