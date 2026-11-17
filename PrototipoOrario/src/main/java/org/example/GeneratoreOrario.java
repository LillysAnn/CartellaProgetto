package org.example;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class GeneratoreOrario {

    public static final int ORA_PAUSA = 6;
    public static final int PRIMA_ORA_POMERIGGIO = 7;
    public static final int ULTIMA_ORA = 5;
    public static final int MAX_ORE_GIORNATA = 8;
    public static final int MAX_ORE_POMERIGGIO_GIORNO = 2;
    public static final int MAX_ORE_GIORNO_DOCENTE = 5;

    // Pesi delle preferenze (piu' alto = piu' importante)
    private static final double PESO_BUCHI_BASE = 10;
    private static final double PESO_BUCHI_PREFERENZA = 40;
    private static final double PESO_BUCHI_CLASSE = 25;
    private static final double PESO_BUCHI_CLASSE_FINALE = 120;
    private static final double PESO_INGRESSO_RITARDATO_CLASSE = 80;
    private static final double PESO_ULTIMA_ORA = 60;
    private static final double PESO_GIORNO_POMERIGGIO = 30;
    private static final double PESO_STESSA_MATERIA_GIORNO = 40;

    private static final int TENTATIVI = 200;
    private static final long TEMPO_MAX_MS = 8000;
    private static final long TEMPO_OTTIMIZZAZIONE_MS = 2500;

    public static List<Lezione> genera(List<Lezione> lezioni, String[] nomiGiorni, int numGiorni, int oreGiornaliere, GestoreDati vincoli) {
        int n = lezioni.size();
        int slot = numGiorni * oreGiornaliere;

        HashMap<String, Integer> idDocenti = new HashMap<>();
        HashMap<String, Integer> idClassi = new HashMap<>();
        HashMap<String, Integer> idGruppi = new HashMap<>();
        List<List<Integer>> lezioniDelGruppo = new ArrayList<>();
        List<Integer> docenteDelGruppo = new ArrayList<>();
        List<Integer> classeDelGruppo = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            Lezione l = lezioni.get(i);
            int d = idDocenti.computeIfAbsent(l.getDocente().getNome(), k -> idDocenti.size());
            int c = idClassi.computeIfAbsent(l.getClasse().toString(), k -> idClassi.size());
            String chiave = l.getDocente().getNome() + "|" + l.getClasse().toString() + "|" + l.getMateria().getNome();
            Integer g = idGruppi.get(chiave);
            if (g == null) {
                g = idGruppi.size();
                idGruppi.put(chiave, g);
                lezioniDelGruppo.add(new ArrayList<>());
                docenteDelGruppo.add(d);
                classeDelGruppo.add(c);
            }
            lezioniDelGruppo.get(g).add(i);
        }

        int nDoc = idDocenti.size();
        int nCla = idClassi.size();
        int nGruppi = idGruppi.size();

        // Limite ore giornaliere per docente (normalmente 5, si adatta solo se un docente ha > 25 ore)
        int[] oreTotaliDocente = new int[nDoc];
        for (int g = 0; g < nGruppi; g++) {
            oreTotaliDocente[docenteDelGruppo.get(g)] += lezioniDelGruppo.get(g).size();
        }
        int[] maxOreGiornoDoc = new int[nDoc];
        int giorniAttiviMinimi = Math.max(1, numGiorni - 1);
        for (int d = 0; d < nDoc; d++) {
            maxOreGiornoDoc[d] = Math.max(MAX_ORE_GIORNO_DOCENTE, (int) Math.ceil((double) oreTotaliDocente[d] / giorniAttiviMinimi));
        }

        // Massimo ore nello stesso giorno per lo stesso gruppo materia-classe (normalmente 2)
        int[] maxOreGiornoGruppo = new int[nGruppi];
        for (int g = 0; g < nGruppi; g++) {
            maxOreGiornoGruppo[g] = Math.max(2, (int) Math.ceil((double) lezioniDelGruppo.get(g).size() / giorniAttiviMinimi));
        }

        // Compresenze teoria/laboratorio
        Map<String, String> teoria = docenteTeoria(lezioni);
        List<List<Integer>> compagni = new ArrayList<>();
        for (int g = 0; g < nGruppi; g++) {
            compagni.add(new ArrayList<>());
        }
        boolean[] eLab = new boolean[nGruppi];
        for (int g = 0; g < nGruppi; g++) {
            Lezione l = lezioni.get(lezioniDelGruppo.get(g).get(0));
            String dt = teoria.get(l.getClasse().toString() + "|" + l.getMateria().getNome());
            if (dt != null && !dt.equals(l.getDocente().getNome())) {
                int gt = idGruppi.get(dt + "|" + l.getClasse().toString() + "|" + l.getMateria().getNome());
                compagni.get(gt).add(g);
                eLab[g] = true;
            }
        }

        // Vincoli dei docenti (incluso divieto pomeriggio di sabato)
        boolean[][] bloccato = new boolean[nDoc][slot];
        boolean[] evitaUltima = new boolean[nDoc];
        boolean[] compatte = new boolean[nDoc];
        for (int gi = 0; gi < numGiorni; gi++) {
            if ("Sabato".equalsIgnoreCase(nomiGiorni[gi])) {
                for (int d = 0; d < nDoc; d++) {
                    for (int o = PRIMA_ORA_POMERIGGIO - 1; o < oreGiornaliere; o++) {
                        bloccato[d][gi * oreGiornaliere + o] = true;
                    }
                }
            }
        }
        if (vincoli != null) {
            for (Map.Entry<String, Integer> e : idDocenti.entrySet()) {
                String nome = e.getKey();
                int d = e.getValue();
                String giornoLibero = vincoli.giornoLibero.get(nome);
                for (int gi = 0; gi < numGiorni; gi++) {
                    if (giornoLibero != null && giornoLibero.equals(nomiGiorni[gi])) {
                        for (int o = 0; o < oreGiornaliere; o++) {
                            bloccato[d][gi * oreGiornaliere + o] = true;
                        }
                    }
                    if (vincoli.senzaPrimaOra.contains(nome)) {
                        bloccato[d][gi * oreGiornaliere] = true;
                    }
                }
                evitaUltima[d] = vincoli.evitaUltimaOra.contains(nome);
                compatte[d] = vincoli.oreCompatte.contains(nome);
            }
        }

        Random random = new Random();
        int[] migliore = new int[n];
        Arrays.fill(migliore, -1);
        int minimoNonCollocate = Integer.MAX_VALUE;
        double minimaPenalita = Double.MAX_VALUE;
        long fine = System.currentTimeMillis() + TEMPO_MAX_MS;
        long fineOttimizzazione = Long.MAX_VALUE;

        for (int t = 0; t < TENTATIVI && (t == 0 || System.currentTimeMillis() < fine)
                && System.currentTimeMillis() < fineOttimizzazione; t++) {
            boolean[][] docenteOccupato = new boolean[nDoc][slot];
            boolean[][] classeOccupata = new boolean[nCla][slot];
            int[][] pomGiorno = new int[nDoc][numGiorni];
            int[][] lezioniGiorno = new int[nDoc][numGiorni];
            int[][] gruppoGiorno = new int[nGruppi][numGiorni];
            int[] rimaste = new int[nGruppi];
            int[] posizione = new int[n];
            Arrays.fill(posizione, -1);
            int daFare = n;
            int nonCollocate = 0;
            for (int g = 0; g < nGruppi; g++) {
                rimaste[g] = lezioniDelGruppo.get(g).size();
            }

            while (daFare > 0) {
                // 1. Scelta del gruppo piu' vincolato (meno slot ammessi)
                int scelto = -1;
                int minOpzioni = Integer.MAX_VALUE;
                int pari = 0;
                for (int g = 0; g < nGruppi; g++) {
                    if (rimaste[g] == 0 || eLab[g]) {
                        continue;
                    }
                    int opzioni = 0;
                    for (int s = 0; s < slot; s++) {
                        if (slotOk(g, s, oreGiornaliere, docenteDelGruppo, classeDelGruppo, compagni, rimaste,
                                docenteOccupato, bloccato, classeOccupata, lezioniGiorno, pomGiorno,
                                maxOreGiornoDoc, gruppoGiorno, maxOreGiornoGruppo)) {
                            opzioni++;
                        }
                    }
                    if (opzioni < minOpzioni) {
                        minOpzioni = opzioni;
                        scelto = g;
                        pari = 1;
                    } else if (opzioni == minOpzioni && random.nextInt(++pari) == 0) {
                        scelto = g;
                    }
                }

                if (minOpzioni == 0) {
                    nonCollocate += rimaste[scelto];
                    daFare -= rimaste[scelto];
                    rimaste[scelto] = 0;
                    for (int h : compagni.get(scelto)) {
                        nonCollocate += rimaste[h];
                        daFare -= rimaste[h];
                        rimaste[h] = 0;
                    }
                    continue;
                }

                // 2. Scelta dello slot ammesso con costo minore
                int d = docenteDelGruppo.get(scelto);
                int c = classeDelGruppo.get(scelto);
                int migliorSlot = -1;
                double migliorCosto = Double.MAX_VALUE;
                for (int s = 0; s < slot; s++) {
                    if (!slotOk(scelto, s, oreGiornaliere, docenteDelGruppo, classeDelGruppo, compagni, rimaste,
                            docenteOccupato, bloccato, classeOccupata, lezioniGiorno, pomGiorno,
                            maxOreGiornoDoc, gruppoGiorno, maxOreGiornoGruppo)) {
                        continue;
                    }
                    double costo = costoSlot(docenteOccupato[d], classeOccupata[c], pomGiorno[d], s, oreGiornaliere,
                            gruppoGiorno[scelto][s / oreGiornaliere], compatte[d], evitaUltima[d], random);

                    for (int h : compagni.get(scelto)) {
                        int dh = docenteDelGruppo.get(h);
                        if (rimaste[h] > 0 && ammessoDocente(docenteOccupato[dh], bloccato[dh], lezioniGiorno[dh],
                                pomGiorno[dh], maxOreGiornoDoc[dh], s, oreGiornaliere)) {
                            costo += costoDocenteSlot(docenteOccupato[dh], pomGiorno[dh], s, oreGiornaliere,
                                    compatte[dh], evitaUltima[dh]);
                        }
                    }
                    if (costo < migliorCosto) {
                        migliorCosto = costo;
                        migliorSlot = s;
                    }
                }

                // 3. Collocazione della lezione (e dell'eventuale compresenza)
                int giorno = migliorSlot / oreGiornaliere;
                int ora = migliorSlot % oreGiornaliere + 1;
                int k = lezioniDelGruppo.get(scelto).size() - rimaste[scelto];
                posizione[lezioniDelGruppo.get(scelto).get(k)] = migliorSlot;
                docenteOccupato[d][migliorSlot] = true;
                classeOccupata[c][migliorSlot] = true;
                lezioniGiorno[d][giorno]++;
                gruppoGiorno[scelto][giorno]++;
                if (ora >= PRIMA_ORA_POMERIGGIO) {
                    pomGiorno[d][giorno]++;
                }
                rimaste[scelto]--;
                daFare--;

                for (int h : compagni.get(scelto)) {
                    int dh = docenteDelGruppo.get(h);
                    if (rimaste[h] > 0 && ammessoDocente(docenteOccupato[dh], bloccato[dh], lezioniGiorno[dh],
                            pomGiorno[dh], maxOreGiornoDoc[dh], migliorSlot, oreGiornaliere)) {
                        int kh = lezioniDelGruppo.get(h).size() - rimaste[h];
                        posizione[lezioniDelGruppo.get(h).get(kh)] = migliorSlot;
                        docenteOccupato[dh][migliorSlot] = true;
                        lezioniGiorno[dh][giorno]++;
                        if (ora >= PRIMA_ORA_POMERIGGIO) {
                            pomGiorno[dh][giorno]++;
                        }
                        rimaste[h]--;
                        daFare--;
                    }
                }
            }

            double penalita = calcolaPenalita(docenteOccupato, classeOccupata, pomGiorno, gruppoGiorno,
                    compatte, evitaUltima, numGiorni, oreGiornaliere);
            if (nonCollocate < minimoNonCollocate || (nonCollocate == minimoNonCollocate && penalita < minimaPenalita)) {
                minimoNonCollocate = nonCollocate;
                minimaPenalita = penalita;
                migliore = posizione;
            }
            if (nonCollocate == 0 && fineOttimizzazione == Long.MAX_VALUE) {
                fineOttimizzazione = System.currentTimeMillis() + TEMPO_OTTIMIZZAZIONE_MS;
            }
        }

        List<Lezione> nonCollocate = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Lezione l = lezioni.get(i);
            if (migliore[i] < 0) {
                l.setGiorno(null);
                l.setOra(-1);
                nonCollocate.add(l);
            } else {
                l.setGiorno(nomiGiorni[migliore[i] / oreGiornaliere]);
                l.setOra(migliore[i] % oreGiornaliere + 1);
            }
        }
        return nonCollocate;
    }

    public static Map<String, String> docenteTeoria(List<Lezione> lezioni) {
        Map<String, Map<String, Integer>> ore = new HashMap<>();
        for (Lezione l : lezioni) {
            ore.computeIfAbsent(l.getClasse().toString() + "|" + l.getMateria().getNome(), k -> new LinkedHashMap<>())
                    .merge(l.getDocente().getNome(), 1, Integer::sum);
        }
        Map<String, String> teoria = new HashMap<>();
        for (Map.Entry<String, Map<String, Integer>> e : ore.entrySet()) {
            if (e.getValue().size() < 2) {
                continue;
            }
            String migliore = null;
            int max = 0;
            for (Map.Entry<String, Integer> d : e.getValue().entrySet()) {
                if (d.getValue() > max) {
                    max = d.getValue();
                    migliore = d.getKey();
                }
            }
            teoria.put(e.getKey(), migliore);
        }
        return teoria;
    }

    private static boolean slotOk(int g, int s, int oreGiornaliere, List<Integer> docenteDelGruppo,
                                  List<Integer> classeDelGruppo, List<List<Integer>> compagni, int[] rimaste,
                                  boolean[][] docenteOccupato, boolean[][] bloccato, boolean[][] classeOccupata,
                                  int[][] lezioniGiorno, int[][] pomGiorno, int[] maxOreGiornoDoc,
                                  int[][] gruppoGiorno, int[] maxOreGiornoGruppo) {
        int giorno = s / oreGiornaliere;
        if (gruppoGiorno[g][giorno] >= maxOreGiornoGruppo[g]) {
            return false;
        }
        int d = docenteDelGruppo.get(g);
        int c = classeDelGruppo.get(g);
        if (classeOccupata[c][s] || !ammessoDocente(docenteOccupato[d], bloccato[d], lezioniGiorno[d],
                pomGiorno[d], maxOreGiornoDoc[d], s, oreGiornaliere)) {
            return false;
        }
        for (int h : compagni.get(g)) {
            if (rimaste[h] == 0 || rimaste[g] > rimaste[h]) {
                continue;
            }
            int dh = docenteDelGruppo.get(h);
            if (!ammessoDocente(docenteOccupato[dh], bloccato[dh], lezioniGiorno[dh],
                    pomGiorno[dh], maxOreGiornoDoc[dh], s, oreGiornaliere)) {
                return false;
            }
        }
        return true;
    }

    private static boolean ammessoDocente(boolean[] docenteOcc, boolean[] bloccatoDocente, int[] lezioniGiornoDocente,
                                          int[] pomGiornoDocente, int maxOreGiorno, int s, int oreGiornaliere) {
        int giorno = s / oreGiornaliere;
        int ora = s % oreGiornaliere + 1;
        if (ora == ORA_PAUSA || docenteOcc[s] || bloccatoDocente[s]) {
            return false;
        }
        if (lezioniGiornoDocente[giorno] >= maxOreGiorno) {
            return false;
        }
        // Ogni docente deve mantenere almeno un giorno libero
        if (lezioniGiornoDocente[giorno] == 0) {
            int giorniUsati = 0;
            for (int n : lezioniGiornoDocente) {
                if (n > 0) {
                    giorniUsati++;
                }
            }
            if (giorniUsati >= lezioniGiornoDocente.length - 1) {
                return false;
            }
        }
        return ora < PRIMA_ORA_POMERIGGIO || pomGiornoDocente[giorno] < MAX_ORE_POMERIGGIO_GIORNO;
    }

    private static double costoSlot(boolean[] docenteOcc, boolean[] classeOcc, int[] pomGiornoDocente, int s,
                                    int oreGiornaliere, int stessaMateriaStessoGiorno, boolean docenteCompatto,
                                    boolean docenteEvitaUltima, Random random) {
        int giorno = s / oreGiornaliere;
        double c = costoDocenteSlot(docenteOcc, pomGiornoDocente, s, oreGiornaliere, docenteCompatto, docenteEvitaUltima);

        int buchiClassePrima = buchi(classeOcc, giorno, oreGiornaliere);
        classeOcc[s] = true;
        c += PESO_BUCHI_CLASSE * (buchi(classeOcc, giorno, oreGiornaliere) - buchiClassePrima);
        classeOcc[s] = false;

        c += PESO_STESSA_MATERIA_GIORNO * stessaMateriaStessoGiorno;
        return c + random.nextDouble() * 6;
    }

    private static double costoDocenteSlot(boolean[] docenteOcc, int[] pomGiornoDocente, int s, int oreGiornaliere,
                                           boolean docenteCompatto, boolean docenteEvitaUltima) {
        int giorno = s / oreGiornaliere;
        int ora = s % oreGiornaliere + 1;
        double c = costoPomeriggio(docenteOcc, pomGiornoDocente, giorno, ora, oreGiornaliere);
        if (docenteEvitaUltima && ora == ULTIMA_ORA) {
            c += PESO_ULTIMA_ORA;
        }
        int buchiPrima = buchi(docenteOcc, giorno, oreGiornaliere);
        docenteOcc[s] = true;
        double pesoBuchi = docenteCompatto ? PESO_BUCHI_PREFERENZA : PESO_BUCHI_BASE;
        c += pesoBuchi * (buchi(docenteOcc, giorno, oreGiornaliere) - buchiPrima);
        docenteOcc[s] = false;
        return c;
    }

    private static double costoPomeriggio(boolean[] docenteOcc, int[] pomGiornoDocente, int giorno, int ora, int oreGiornaliere) {
        if (ora < PRIMA_ORA_POMERIGGIO) {
            return 0;
        }
        if (pomGiornoDocente[giorno] > 0) {
            return 0;
        }
        int giorniConPomeriggio = 0;
        for (int p : pomGiornoDocente) {
            if (p > 0) {
                giorniConPomeriggio++;
            }
        }
        double bucoPostPausa = (ora > PRIMA_ORA_POMERIGGIO && !docenteOcc[giorno * oreGiornaliere + PRIMA_ORA_POMERIGGIO - 1]) ? 35 : 0;
        return PESO_GIORNO_POMERIGGIO + 40.0 * giorniConPomeriggio + bucoPostPausa;
    }

    private static double calcolaPenalita(boolean[][] docenteOccupato, boolean[][] classeOccupata, int[][] pomGiorno,
                                          int[][] gruppoGiorno, boolean[] compatte, boolean[] evitaUltima,
                                          int numGiorni, int oreGiornaliere) {
        double penalita = 0;
        for (int d = 0; d < docenteOccupato.length; d++) {
            int giorniPomeriggio = 0;
            for (int gi = 0; gi < numGiorni; gi++) {
                penalita += (compatte[d] ? PESO_BUCHI_PREFERENZA : PESO_BUCHI_BASE) * buchi(docenteOccupato[d], gi, oreGiornaliere);
                if (evitaUltima[d] && docenteOccupato[d][gi * oreGiornaliere + ULTIMA_ORA - 1]) {
                    penalita += PESO_ULTIMA_ORA;
                }
                if (pomGiorno[d][gi] > 0) {
                    giorniPomeriggio++;
                }
            }
            penalita += PESO_GIORNO_POMERIGGIO * giorniPomeriggio;
        }
        for (int c = 0; c < classeOccupata.length; c++) {
            for (int gi = 0; gi < numGiorni; gi++) {
                penalita += PESO_BUCHI_CLASSE_FINALE * buchi(classeOccupata[c], gi, oreGiornaliere);
                if (!classeOccupata[c][gi * oreGiornaliere] && haLezioni(classeOccupata[c], gi, oreGiornaliere)) {
                    penalita += PESO_INGRESSO_RITARDATO_CLASSE;
                }
            }
        }
        for (int[] gg : gruppoGiorno) {
            for (int gi = 0; gi < numGiorni; gi++) {
                if (gg[gi] > 1) {
                    penalita += PESO_STESSA_MATERIA_GIORNO * (gg[gi] - 1);
                }
            }
        }
        return penalita;
    }

    private static boolean haLezioni(boolean[] occ, int giorno, int oreGiornaliere) {
        for (int ora = 1; ora <= oreGiornaliere; ora++) {
            if (occ[giorno * oreGiornaliere + ora - 1]) {
                return true;
            }
        }
        return false;
    }

    // Ore vuote tra la prima e l'ultima lezione del giorno
    public static int buchi(boolean[] occ, int giorno, int oreGiornaliere) {
        int prima = -1;
        int ultima = -1;
        for (int ora = 1; ora <= oreGiornaliere; ora++) {
            if (ora != ORA_PAUSA && occ[giorno * oreGiornaliere + ora - 1]) {
                if (prima < 0) {
                    prima = ora;
                }
                ultima = ora;
            }
        }
        if (prima < 0) {
            return 0;
        }
        int b = 0;
        for (int ora = prima; ora <= ultima; ora++) {
            if (ora != ORA_PAUSA && !occ[giorno * oreGiornaliere + ora - 1]) {
                b++;
            }
        }
        return b;
    }
}