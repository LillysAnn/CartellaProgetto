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

    // chiave: nome del professore, valore: le sue classi
    public final HashMap<String, ArrayList<Classe>> DocenteClassi = new HashMap<>();


    // OBBLIGATORI: il generatore non li viola mai (se non c'e' posto la lezione resta non collocata)
    public final HashMap<String, String> giornoLibero = new HashMap<>();
    public final HashSet<String> senzaPrimaOra = new HashSet<>();
    // PREFERENZE: il generatore le ottimizza, ma se non puo' le viola e lo segnala
    public final HashSet<String> evitaUltimaOra = new HashSet<>();          // docenti che preferiscono non fare l'8a ora (15:00)//VEDIII
    public final HashSet<String> oreCompatte = new HashSet<>();             // docenti che preferiscono niente ore buche

   /* public GestoreDati() {
    }//????????? costruttore
*/
    public GestoreDati(String nomeDocente, int anno, char sezione, String indirizzo, String nomeMateria, int ore) {
        // Classe con indirizzo e materia nei campi giusti (prima la materia finiva nel campo indirizzo)
        Classe classe = new Classe(anno, sezione, indirizzo, ore);

        // Salviamo nella HashMap dei docenti
        DocenteClassi.computeIfAbsent(nomeDocente, k -> new ArrayList<>()).add(classe);

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

   /* public void aggiungiAssegnazione(String nomeDocente, int anno, char sezione, String indirizzo, String nomeMateria, int ore) {
        // Classe con indirizzo e materia nei campi giusti (prima la materia finiva nel campo indirizzo)
        Classe classe = new Classe(anno, sezione, indirizzo, ore);

        // Salviamo nella HashMap dei docenti
        DocenteClassi.computeIfAbsent(nomeDocente, k -> new ArrayList<>()).add(classe);

        Docente d = new Docente(nomeDocente);
        Materia m = new Materia(nomeMateria);

        // Generiamo le N lezioni atomiche (singole ore) per la griglia oraria
        for (int i = 1; i <= ore; i++) {
            String idLezione = nomeDocente + "_" + anno + sezione + indirizzo + "_" + nomeMateria + "_" + i;
            listaLezioni.add(new Lezione(idLezione, d, m, classe));
        }

        oreTotali += ore;
        numeroAssegnazioni++;
    }//FINE ASSEGNAZIONE, SPOSTARE A GESTORE DATI???*/

    // Nomi dei docenti in ordine alfabetico (per riempire il menu a tendina)
    public ArrayList<String> docentiOrdinati() {
        ArrayList<String> nomi = new ArrayList<>(DocenteClassi.keySet());//prende eleco docenti dalle chiavi docenteclassi
        Collections.sort(nomi, String.CASE_INSENSITIVE_ORDER);//ordine alfabetico
        return nomi;
    }

    public int getOreTotali() {
        return oreTotali;
    }

    public int getNumeroAssegnazioni() {
        return numeroAssegnazioni;//?????
    }

    // Metodo fondamentale per l'algoritmo dello Step 2 e Step 3
    public List<Lezione> getListaLezioni() {
        return listaLezioni;//contiene lezioni atomiche
    }
}
