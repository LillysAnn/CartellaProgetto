package org.example;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class ValidatoreOrario {

    public static List<String> verifica(List<Lezione> lezioni, GestoreDati vincoli, String[] nomiGiorni) {
        List<String> errori = new ArrayList<>();
        Map<String, Set<String>> giorniDocente = new TreeMap<>();
        Map<String, Lezione> docenteSlot = new HashMap<>();
        Map<String, Lezione> classeSlot = new HashMap<>();
        Map<String, Integer> pomGiorno = new HashMap<>();
        Map<String, String> teoria = GeneratoreOrario.docenteTeoria(lezioni);

        for (Lezione l : lezioni) {
            if (l.getGiorno() == null) {
                continue;
            }
            giorniDocente.computeIfAbsent(l.getDocente().getNome(), k -> new HashSet<>()).add(l.getGiorno());
            String quando = l.getGiorno() + " ora " + l.getOra();
            if (l.getOra() == GeneratoreOrario.ORA_PAUSA) {
                errori.add("Lezione nella pausa 13-14: " + l.getId());
            }
            if ("Sabato".equalsIgnoreCase(l.getGiorno()) && l.getOra() >= GeneratoreOrario.PRIMA_ORA_POMERIGGIO) {
                errori.add("Lezione al pomeriggio di sabato: " + l.getId());
            }
            if (vincoli != null) {
                String giornoLibero = vincoli.giornoLibero.get(l.getDocente().getNome());
                if (giornoLibero != null && giornoLibero.equals(l.getGiorno())) {
                    errori.add("Docente " + l.getDocente().getNome() + " ha una lezione nel suo giorno libero (" + giornoLibero + ")");
                }
                if (l.getOra() == 1 && vincoli.senzaPrimaOra.contains(l.getDocente().getNome())) {
                    errori.add("Docente " + l.getDocente().getNome() + " ha una lezione alla prima ora (" + l.getGiorno() + ")");
                }
            }
            Lezione altra = docenteSlot.put(l.getDocente().getNome() + "|" + quando, l);
            if (altra != null) {
                errori.add("Docente " + l.getDocente().getNome() + " ha due lezioni insieme (" + quando + ")");
            }
            altra = classeSlot.put(l.getClasse().toString() + "|" + quando, l);
            boolean compresenza = altra != null && altra.getMateria().getNome().equals(l.getMateria().getNome())
                    && teoria.containsKey(l.getClasse().toString() + "|" + l.getMateria().getNome());
            if (altra != null && !compresenza) {
                errori.add("Classe " + l.getClasse().toString() + " ha due lezioni insieme (" + quando + ")");
            }
            if (l.getOra() >= GeneratoreOrario.PRIMA_ORA_POMERIGGIO) {
                int g = pomGiorno.merge(l.getDocente().getNome() + "|" + l.getGiorno(), 1, Integer::sum);
                if (g == GeneratoreOrario.MAX_ORE_POMERIGGIO_GIORNO + 1) {
                    errori.add("Docente " + l.getDocente().getNome() + " ha piu' di " + GeneratoreOrario.MAX_ORE_POMERIGGIO_GIORNO
                            + " ore al pomeriggio di " + l.getGiorno());
                }
            }
        }
        if (nomiGiorni != null) {
            for (Map.Entry<String, Set<String>> e : giorniDocente.entrySet()) {
                if (e.getValue().size() >= nomiGiorni.length) {
                    errori.add("Docente " + e.getKey() + " ha lezione in tutti i giorni: manca il giorno libero");
                }
            }
        }
        return errori;
    }

    public static List<String> preferenzeNonRispettate(List<Lezione> lezioni, GestoreDati vincoli, String[] nomiGiorni) {
        List<String> report = new ArrayList<>();
        if (vincoli == null) {
            return report;
        }
        Map<String, Integer> indiceGiorno = new HashMap<>();
        for (int i = 0; i < nomiGiorni.length; i++) {
            indiceGiorno.put(nomiGiorni[i], i);
        }
        Map<String, boolean[]> occ = new TreeMap<>();
        for (Lezione l : lezioni) {
            Integer gi = l.getGiorno() == null ? null : indiceGiorno.get(l.getGiorno());
            if (gi != null && l.getOra() >= 1 && l.getOra() <= GeneratoreOrario.MAX_ORE_GIORNATA) {
                occ.computeIfAbsent(l.getDocente().getNome(), k -> new boolean[nomiGiorni.length * GeneratoreOrario.MAX_ORE_GIORNATA])[gi * GeneratoreOrario.MAX_ORE_GIORNATA + l.getOra() - 1] = true;
            }
        }
        for (Map.Entry<String, boolean[]> e : occ.entrySet()) {
            String docente = e.getKey();
            boolean[] o = e.getValue();
            if (vincoli.evitaUltimaOra.contains(docente)) {
                StringBuilder giorni = new StringBuilder();
                for (int gi = 0; gi < nomiGiorni.length; gi++) {
                    if (o[gi * GeneratoreOrario.MAX_ORE_GIORNATA + GeneratoreOrario.ULTIMA_ORA - 1]) {
                        giorni.append(giorni.length() > 0 ? ", " : "").append(nomiGiorni[gi]);
                    }
                }
                if (giorni.length() > 0) {
                    report.add("Docente " + docente + ": preferiva non fare l'ultima ora (12:00) ma l'ha in " + giorni
                            + " (nessuno slot alternativo libero compatibile con gli altri vincoli)");
                }
            }
            if (vincoli.oreCompatte.contains(docente)) {
                StringBuilder dettaglioBuchi = new StringBuilder();
                int totale = 0;
                for (int gi = 0; gi < nomiGiorni.length; gi++) {
                    int b = GeneratoreOrario.buchi(o, gi, GeneratoreOrario.MAX_ORE_GIORNATA);
                    if (b > 0) {
                        totale += b;
                        dettaglioBuchi.append(dettaglioBuchi.length() > 0 ? ", " : "").append(nomiGiorni[gi]).append(": ").append(b);
                    }
                }
                if (totale > 0) {
                    report.add("Docente " + docente + ": preferiva ore compatte ma ha " + totale + " ore buche (" + dettaglioBuchi
                            + "); non c'era un'alternativa migliore con gli altri vincoli");
                }
            }
        }
        return report;
    }

    public static List<String> vincoliDocentiCoinvolti(List<Lezione> nonCollocate, GestoreDati vincoli) {
        List<String> righe = new ArrayList<>();
        if (vincoli == null) {
            return righe;
        }
        Set<String> visti = new TreeSet<>();
        for (Lezione l : nonCollocate) {
            visti.add(l.getDocente().getNome());
        }
        for (String d : visti) {
            String giorno = vincoli.giornoLibero.get(d);
            boolean prima = vincoli.senzaPrimaOra.contains(d);
            if (giorno != null || prima) {
                righe.add("Docente " + d + ": " + (giorno != null ? "giorno libero " + giorno : "")
                        + (giorno != null && prima ? " e " : "") + (prima ? "niente prima ora" : ""));
            }
        }
        return righe;
    }

    public static List<String> controllaCapacita(List<Lezione> lezioni, int numGiorni, int oreGiornaliere,
                                                 GestoreDati vincoli) {
        List<String> avvisi = new ArrayList<>();
        Map<String, Integer> perDocente = new TreeMap<>();
        Map<String, Integer> perClasse = new TreeMap<>();
        Map<String, String> teoria = GeneratoreOrario.docenteTeoria(lezioni);
        for (Lezione l : lezioni) {
            perDocente.merge(l.getDocente().getNome(), 1, Integer::sum);
            String t = teoria.get(l.getClasse().toString() + "|" + l.getMateria().getNome());
            if (t == null || t.equals(l.getDocente().getNome())) {
                perClasse.merge(l.getClasse().toString(), 1, Integer::sum);
            }
        }
        int mattina = GeneratoreOrario.ORA_PAUSA - 1;
        int pomeriggio = oreGiornaliere - GeneratoreOrario.ORA_PAUSA;
        int giorniPom = numGiorni >= 6 ? numGiorni - 1 : numGiorni;
        long capacitaClasse = (long) numGiorni * mattina + (long) giorniPom * pomeriggio;
        for (Map.Entry<String, Integer> e : perDocente.entrySet()) {
            String gl = vincoli != null ? vincoli.giornoLibero.get(e.getKey()) : null;
            boolean senzaPrima = vincoli != null && vincoli.senzaPrimaOra.contains(e.getKey());
            int giorniAttivi = numGiorni - 1;
            int giorniPomDoc = (numGiorni >= 6 && !"Sabato".equalsIgnoreCase(gl)) ? giorniAttivi - 1 : giorniAttivi;
            long pomDocente = (long) giorniPomDoc * Math.min(pomeriggio, GeneratoreOrario.MAX_ORE_POMERIGGIO_GIORNO);
            long capacitaDocente = (long) giorniAttivi * (mattina - (senzaPrima ? 1 : 0)) + pomDocente;
            if (e.getValue() > capacitaDocente) {
                avvisi.add("Docente " + e.getKey() + ": " + e.getValue() + " ore richieste, massimo possibile " + capacitaDocente
                        + " (ogni docente ha almeno un giorno libero" + (senzaPrima || gl != null ? " e questo docente ha altri vincoli obbligatori" : "") + ")");
            }
        }
        for (Map.Entry<String, Integer> e : perClasse.entrySet()) {
            if (e.getValue() > capacitaClasse) {
                avvisi.add("Classe " + e.getKey() + ": " + e.getValue() + " ore richieste, slot disponibili " + capacitaClasse);
            }
        }
        return avvisi;
    }
}