package org.example;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

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

        // Vincoli dei docenti
        boolean[][] bloccato = new boolean[nDoc][slot];
        boolean[] evitaUltima = new boolean[nDoc];
        boolean[] compatte = new boolean[nDoc];
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
    private static int buchi(boolean[] occ, int giorno, int oreGiornaliere) {
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

    public static List<String> verifica(List<Lezione> lezioni, GestoreDati vincoli, String[] nomiGiorni) {
        List<String> errori = new ArrayList<>();
        Map<String, Set<String>> giorniDocente = new TreeMap<>();
        Map<String, Lezione> docenteSlot = new HashMap<>();
        Map<String, Lezione> classeSlot = new HashMap<>();
        Map<String, Integer> pomGiorno = new HashMap<>();
        Map<String, String> teoria = docenteTeoria(lezioni);

        for (Lezione l : lezioni) {
            if (l.getGiorno() == null) {
                continue;
            }
            giorniDocente.computeIfAbsent(l.getDocente().getNome(), k -> new HashSet<>()).add(l.getGiorno());
            String quando = l.getGiorno() + " ora " + l.getOra();
            if (l.getOra() == ORA_PAUSA) {
                errori.add("Lezione nella pausa 13-14: " + l.getId());
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
            if (l.getOra() >= PRIMA_ORA_POMERIGGIO) {
                int g = pomGiorno.merge(l.getDocente().getNome() + "|" + l.getGiorno(), 1, Integer::sum);
                if (g == MAX_ORE_POMERIGGIO_GIORNO + 1) {
                    errori.add("Docente " + l.getDocente().getNome() + " ha piu' di " + MAX_ORE_POMERIGGIO_GIORNO
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
            if (gi != null && l.getOra() >= 1 && l.getOra() <= MAX_ORE_GIORNATA) {
                occ.computeIfAbsent(l.getDocente().getNome(), k -> new boolean[nomiGiorni.length * MAX_ORE_GIORNATA])[gi * MAX_ORE_GIORNATA + l.getOra() - 1] = true;
            }
        }
        for (Map.Entry<String, boolean[]> e : occ.entrySet()) {
            String docente = e.getKey();
            boolean[] o = e.getValue();
            if (vincoli.evitaUltimaOra.contains(docente)) {
                StringBuilder giorni = new StringBuilder();
                for (int gi = 0; gi < nomiGiorni.length; gi++) {
                    if (o[gi * MAX_ORE_GIORNATA + ULTIMA_ORA - 1]) {
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
                    int b = buchi(o, gi, MAX_ORE_GIORNATA);
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
        Map<String, String> teoria = docenteTeoria(lezioni);
        for (Lezione l : lezioni) {
            perDocente.merge(l.getDocente().getNome(), 1, Integer::sum);
            String t = teoria.get(l.getClasse().toString() + "|" + l.getMateria().getNome());
            if (t == null || t.equals(l.getDocente().getNome())) {
                perClasse.merge(l.getClasse().toString(), 1, Integer::sum);
            }
        }
        int mattina = ORA_PAUSA - 1;
        int pomeriggio = oreGiornaliere - ORA_PAUSA;
        long capacitaClasse = (long) numGiorni * (mattina + pomeriggio);
        for (Map.Entry<String, Integer> e : perDocente.entrySet()) {
            boolean giornoLibero = vincoli != null && vincoli.giornoLibero.containsKey(e.getKey());
            boolean senzaPrima = vincoli != null && vincoli.senzaPrimaOra.contains(e.getKey());
            int giorniAttivi = numGiorni - 1;
            long pomDocente = (long) giorniAttivi * Math.min(pomeriggio, MAX_ORE_POMERIGGIO_GIORNO);
            long capacitaDocente = (long) giorniAttivi * (mattina - (senzaPrima ? 1 : 0)) + pomDocente;
            if (e.getValue() > capacitaDocente) {
                avvisi.add("Docente " + e.getKey() + ": " + e.getValue() + " ore richieste, massimo possibile " + capacitaDocente
                        + " (ogni docente ha almeno un giorno libero" + (senzaPrima || giornoLibero ? " e questo docente ha altri vincoli obbligatori" : "") + ")");
            }
        }
        for (Map.Entry<String, Integer> e : perClasse.entrySet()) {
            if (e.getValue() > capacitaClasse) {
                avvisi.add("Classe " + e.getKey() + ": " + e.getValue() + " ore richieste, slot disponibili " + capacitaClasse);
            }
        }
        return avvisi;
    }

    public static void esportaCsv(Path file, List<String> titoli, List<String[][]> tabelle, String[] nomiGiorni)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF'); // BOM UTF-8 per apertura corretta in Excel
        for (int i = 0; i < tabelle.size(); i++) {
            sb.append(escCsv(titoli.get(i))).append("\r\n");
            sb.append("Ora");
            for (String g : nomiGiorni) {
                sb.append(';').append(escCsv(g));
            }
            sb.append("\r\n");

            String[][] celle = tabelle.get(i);
            for (int o = 0; o < celle.length; o++) {
                int ora = o + 1;
                sb.append((7 + ora)).append(":00-").append((8 + ora)).append(":00");
                for (int d = 0; d < nomiGiorni.length; d++) {
                    sb.append(';');
                    if (ora == ORA_PAUSA) {
                        sb.append("PAUSA");
                    } else {
                        String val = celle[o][d] == null ? "" : celle[o][d];
                        sb.append(escCsv(val));
                    }
                }
                sb.append("\r\n");
            }
            if (i < tabelle.size() - 1) {
                sb.append("\r\n");
            }
        }
        Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String escCsv(String s) {
        if (s == null) {
            return "";
        }
        if (s.contains(";") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static final Charset WIN = Charset.forName("windows-1252");
    private static final double PDF_W = 842;
    private static final double PDF_H = 595;

    public static void esportaPdf(Path file, List<String> titoli, List<String[][]> tabelle, String[] nomiGiorni)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int pagine = tabelle.size();
        int nObj = 5 + 2 * pagine;
        int[] off = new int[nObj];
        scrivi(out, "%PDF-1.4\n");

        off[1] = out.size();
        scrivi(out, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pagine; i++) {
            kids.append(5 + 2 * i).append(" 0 R ");
        }
        off[2] = out.size();
        scrivi(out, "2 0 obj\n<< /Type /Pages /Kids [" + kids + "] /Count " + pagine + " >>\nendobj\n");
        off[3] = out.size();
        scrivi(out, "3 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>\nendobj\n");
        off[4] = out.size();
        scrivi(out, "4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>\nendobj\n");

        for (int i = 0; i < pagine; i++) {
            int pg = 5 + 2 * i;
            int ct = 6 + 2 * i;
            byte[] contenuto = contenutoPagina(titoli.get(i), tabelle.get(i), nomiGiorni).getBytes(WIN);
            off[pg] = out.size();
            scrivi(out, pg + " 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + (int) PDF_W + " " + (int) PDF_H
                    + "] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents " + ct + " 0 R >>\nendobj\n");
            off[ct] = out.size();
            scrivi(out, ct + " 0 obj\n<< /Length " + contenuto.length + " >>\nstream\n");
            out.write(contenuto);
            scrivi(out, "\nendstream\nendobj\n");
        }

        int xref = out.size();
        scrivi(out, "xref\n0 " + nObj + "\n0000000000 65535 f \n");
        for (int i = 1; i < nObj; i++) {
            scrivi(out, String.format(Locale.ROOT, "%010d 00000 n \n", off[i]));
        }
        scrivi(out, "trailer\n<< /Size " + nObj + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        Files.write(file, out.toByteArray());
    }

    private static void scrivi(OutputStream o, String s) throws IOException {
        o.write(s.getBytes(WIN));
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private static String contenutoPagina(String titolo, String[][] celle, String[] nomiGiorni) {
        StringBuilder sb = new StringBuilder();
        int nOre = celle.length;
        int nGiorni = nomiGiorni.length;
        double x0 = 40;
        double wOra = 62;
        double wGiorno = (PDF_W - 2 * x0 - wOra) / nGiorni;
        double hRiga = 46;
        double hPausa = 22;
        double hIntest = 24;
        double y = PDF_H - 70;

        testo(sb, "F2", 16, x0, PDF_H - 45, titolo);

        cella(sb, x0, y - hIntest, wOra, hIntest, 0.80, "F2", 9, "Ora", true);
        for (int d = 0; d < nGiorni; d++) {
            cella(sb, x0 + wOra + d * wGiorno, y - hIntest, wGiorno, hIntest, 0.80, "F2", 9, nomiGiorni[d], true);
        }
        y -= hIntest;

        for (int o = 0; o < nOre; o++) {
            int ora = o + 1;
            if (ora == ORA_PAUSA) {
                cella(sb, x0, y - hPausa, wOra + nGiorni * wGiorno, hPausa, 0.90, "F1", 8, "13-14  pausa", false);
                y -= hPausa;
                continue;
            }
            cella(sb, x0, y - hRiga, wOra, hRiga, 0.92, "F2", 9, (7 + ora) + "-" + (8 + ora), true);
            for (int d = 0; d < nGiorni; d++) {
                String t = celle[o][d] == null ? "" : celle[o][d];
                cella(sb, x0 + wOra + d * wGiorno, y - hRiga, wGiorno, hRiga, t.isEmpty() ? 1.0 : -1, "F1", 8, t, false);
            }
            y -= hRiga;
        }
        return sb.toString();
    }

    private static void cella(StringBuilder sb, double x, double y, double w, double h, double gray,
                              String font, int size, String testo, boolean centrato) {
        if (gray < 0) {
            int hash = Math.abs(testo.split(" - ")[0].hashCode());
            double r = 0.75 + (hash % 25) / 100.0;
            double g = 0.75 + ((hash / 25) % 25) / 100.0;
            double b = 0.75 + ((hash / 625) % 25) / 100.0;
            sb.append(String.format(Locale.ROOT, "%.2f %.2f %.2f rg\n", r, g, b));
        } else {
            sb.append(String.format(Locale.ROOT, "%.2f g\n", gray));
        }
        sb.append("0.5 G 0.5 w\n");
        sb.append(String.format(Locale.ROOT, "%.1f %.1f %.1f %.1f re B\n", x, y, w, h));
        sb.append("0 g\n");
        if (testo.isEmpty()) {
            return;
        }
        int maxCar = Math.max(4, (int) ((w - 8) / (size * 0.5)));
        List<String> righe = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String parola : testo.split(" ")) {
            if (cur.length() > 0 && cur.length() + 1 + parola.length() > maxCar) {
                righe.add(cur.toString());
                cur.setLength(0);
            }
            if (cur.length() > 0) {
                cur.append(' ');
            }
            cur.append(parola);
        }
        if (cur.length() > 0) {
            righe.add(cur.toString());
        }
        double interlinea = size + 2;
        double ty = y + h / 2 + (righe.size() - 1) * interlinea / 2 - size * 0.35;
        for (String r : righe) {
            double tx = centrato ? x + w / 2 - r.length() * size * 0.25 : x + 4;
            testo(sb, font, size, tx, ty, r);
            ty -= interlinea;
        }
    }

    private static void testo(StringBuilder sb, String font, int size, double x, double y, String t) {
        sb.append(String.format(Locale.ROOT, "BT /%s %d Tf %.1f %.1f Td (%s) Tj ET\n", font, size, x, y, esc(t)));
    }
}