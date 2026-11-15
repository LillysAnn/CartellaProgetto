package org.example;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
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
import java.util.TreeSet;
import java.util.TreeMap;

/**
 * Generatore dell'orario (Step 2/3/6).
 *
 * Struttura della giornata (le "ore" sono numerate 1..8 come le righe della griglia grafica):
 *   ore 1-5  = 8-13   mattina
 *   ora 6    = 13-14  PAUSA: non e' mai assegnabile
 *   ore 7-8  = 14-16  pomeriggio
 *
 * Vincoli obbligatori (hard):
 *  - un docente non puo' avere due lezioni nello stesso slot
 *  - una classe non puo' avere due lezioni nello stesso slot
 *  - la pausa (ora 6) resta sempre vuota
 *  - al pomeriggio un docente ha al massimo MAX_ORE_POMERIGGIO_GIORNO ore al giorno
 *  - ogni docente ha almeno un giorno senza lezioni (quello scelto da lui, oppure uno scelto dal programma)
 *
 * Preferenze (soft, pesate nel costo di ogni slot): meglio la mattina, niente ore buche
 * per docente e classe, stessa materia della stessa classe su giorni diversi, pochi giorni
 * di servizio per ogni docente.
 *
 * Vincoli dei docenti (Step 5), letti da GestoreDati:
 *  - giorno libero (OBBLIGATORIO): nessuna lezione in quel giorno
 *  - niente prima ora (OBBLIGATORIO): nessuna lezione alle 8:00
 *  - evita ultima ora (PREFERENZA): si evita l'8a ora (15:00-16:00), ma la si usa se non ci sono alternative
 *  - ore compatte (PREFERENZA): le ore buche del docente pesano di piu' nella scelta dello slot
 *
 * Compresenza teoria/laboratorio: se la stessa classe ha la stessa materia con due (o piu') docenti
 * e uno ha piu' ore degli altri, quello e' il docente di teoria e gli altri sono di laboratorio:
 * le ore di laboratorio non si sommano, ma si svolgono DENTRO le ore del docente di teoria
 * (6 ore teoria + 4 ore laboratorio = 6 ore per la classe, 4 delle quali con due docenti).
 * Se le ore sono uguali (es. 2 e 2) le lezioni si uniscono lo stesso: come "teoria" si considera
 * il primo docente comparso nel CSV, l'altro e' in compresenza con lui.
 *
 * Fa piu' tentativi e tiene il migliore (quello con meno lezioni non collocate).
 */
public class GeneratoreOrario {

    public static final int ORA_PAUSA = 6;
    public static final int PRIMA_ORA_POMERIGGIO = 7;
    public static final int ULTIMA_ORA = 8;
    public static final int MAX_ORE_POMERIGGIO_GIORNO = 2;
    // limite settimanale di ore pomeridiane per docente (MAX_VALUE = nessun limite)
    public static final int MAX_ORE_POMERIGGIO_SETTIMANA = Integer.MAX_VALUE;///vVDERE SE  TOGLIERE

    // pesi delle preferenze (piu' alto = piu' importante)
    private static final double PESO_BUCHI_DOCENTE = 10;        // per tutti i docenti
    private static final double PESO_BUCHI_PREFERENZA = 40;     // docenti con "ore compatte"
    private static final double PESO_ULTIMA_ORA = 60;           // docenti con "evita ultima ora"

    private static final double PESO_GIORNO_POMERIGGIO = 30;    // ogni pomeriggio di servizio di un docente
    private static final double PESO_ORA_POMERIGGIO = 3;
    private static final double PESO_GIORNO_SERVIZIO = 8;        // ogni giorno di servizio di un docente

    private static final int TENTATIVI = 200;
    private static final long TEMPO_MAX_MS = 8000;
    // dopo il primo orario completo si continua a cercare per un po': si tiene quello con meno preferenze violate
    private static final long TEMPO_OTTIMIZZAZIONE_MS = 2500;

    /**
     * @param nomiGiorni     nomi dei giorni (es. "Lunedi", ...), se ne usano i primi numGiorni
     * @param numGiorni      giorni di lezione
     * @param oreGiornaliere righe della giornata (8: 8-13, pausa 13-14, 14-16)
     * @return le lezioni che non e' stato possibile collocare (lista vuota = orario completo)
     */
    public static List<Lezione> genera(List<Lezione> lezioni, String[] nomiGiorni, int numGiorni, int oreGiornaliere) {
        return genera(lezioni, nomiGiorni, numGiorni, oreGiornaliere, null);
    }

    /** Come sopra, con i vincoli dei docenti (giorno libero, niente prima ora, ultima ora, ore compatte). */
    public static List<Lezione> genera(List<Lezione> lezioni, String[] nomiGiorni, int numGiorni, int oreGiornaliere, GestoreDati vincoli) {
        int n = lezioni.size();
        int slot = numGiorni * oreGiornaliere;

        // Numero progressivo per ogni docente, classe e "gruppo" (docente+classe+materia)
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

        // Compresenze: per ogni gruppo di teoria, i gruppi di laboratorio che si svolgono insieme a lui
        Map<String, String> teoria = docenteTeoria(lezioni);
        List<List<Integer>> compagni = new ArrayList<>();
        for (int g = 0; g < idGruppi.size(); g++) {
            compagni.add(new ArrayList<>());
        }
        boolean[] eLab = new boolean[idGruppi.size()];
        for (int g = 0; g < idGruppi.size(); g++) {
            Lezione l = lezioni.get(lezioniDelGruppo.get(g).get(0));
            String dt = teoria.get(l.getClasse().toString() + "|" + l.getMateria().getNome());
            if (dt != null && !dt.equals(l.getDocente().getNome())) {
                int gt = idGruppi.get(dt + "|" + l.getClasse().toString() + "|" + l.getMateria().getNome());
                compagni.get(gt).add(g);
                eLab[g] = true;
            }
        }
        int nDoc = idDocenti.size();
        int nCla = idClassi.size();
        int nGruppi = idGruppi.size();

        // Vincoli dei docenti: bloccato[d][slot] = slot vietato (obbligatorio); le preferenze pesano nel costo
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
        long fineOttimizzazione = Long.MAX_VALUE;   // si fissa quando si trova il primo orario completo

        for (int t = 0; t < TENTATIVI && (t == 0 || System.currentTimeMillis() < fine)
                && System.currentTimeMillis() < fineOttimizzazione; t++) {
            boolean[][] docenteOccupato = new boolean[nDoc][slot];
            boolean[][] classeOccupata = new boolean[nCla][slot];
            int[][] pomGiorno = new int[nDoc][numGiorni];
            int[][] lezioniGiorno = new int[nDoc][numGiorni];     // lezioni di ogni docente in ogni giorno
            int[] pomSettimana = new int[nDoc];
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
                // 1. si sceglie il gruppo piu' vincolato (meno slot ammessi); a parita' a caso
                int scelto = -1;
                int minOpzioni = Integer.MAX_VALUE;
                int pari = 0;
                for (int g = 0; g < nGruppi; g++) {
                    if (rimaste[g] == 0 || eLab[g]) {      // il laboratorio si colloca insieme alla teoria
                        continue;
                    }
                    int opzioni = 0;
                    for (int s = 0; s < slot; s++) {
                        if (slotOk(g, s, oreGiornaliere, docenteDelGruppo, classeDelGruppo, compagni, rimaste,
                                docenteOccupato, bloccato, classeOccupata, lezioniGiorno, pomGiorno, pomSettimana)) {
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

                // nessuno slot ammesso: le lezioni rimaste di questo gruppo restano non collocate
                if (minOpzioni == 0) {
                    nonCollocate += rimaste[scelto];
                    daFare -= rimaste[scelto];
                    rimaste[scelto] = 0;
                    for (int h : compagni.get(scelto)) {      // anche il laboratorio collegato resta da collocare
                        nonCollocate += rimaste[h];
                        daFare -= rimaste[h];
                        rimaste[h] = 0;
                    }
                    continue;
                }

                // 2. si sceglie lo slot ammesso con il costo piu' basso
                int d = docenteDelGruppo.get(scelto);
                int c = classeDelGruppo.get(scelto);
                int migliorSlot = -1;
                double migliorCosto = Double.MAX_VALUE;
                for (int s = 0; s < slot; s++) {
                    if (!slotOk(scelto, s, oreGiornaliere, docenteDelGruppo, classeDelGruppo, compagni, rimaste,
                            docenteOccupato, bloccato, classeOccupata, lezioniGiorno, pomGiorno, pomSettimana)) {
                        continue;
                    }
                    double costo = costo(docenteOccupato[d], classeOccupata[c], pomGiorno[d], s, oreGiornaliere,
                            gruppoGiorno[scelto][s / oreGiornaliere], compatte[d], evitaUltima[d], random);
                    // anche il docente di laboratorio che entra in compresenza deve avere pomeriggi ordinati
                    for (int h : compagni.get(scelto)) {
                        int dh = docenteDelGruppo.get(h);
                        if (rimaste[h] > 0 && ammessoDocente(docenteOccupato[dh], bloccato[dh], lezioniGiorno[dh], pomGiorno[dh], pomSettimana[dh], s, oreGiornaliere)) {
                            costo += costoPomeriggio(pomGiorno[dh], s / oreGiornaliere, s % oreGiornaliere + 1);
                            if (evitaUltima[dh] && s % oreGiornaliere + 1 == ULTIMA_ORA) {
                                costo += PESO_ULTIMA_ORA;
                            }
                        }
                    }
                    if (costo < migliorCosto) {
                        migliorCosto = costo;
                        migliorSlot = s;
                    }
                }

                // 3. si colloca la lezione
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
                    pomSettimana[d]++;
                }
                rimaste[scelto]--;
                daFare--;

                // compresenza: il docente di laboratorio entra nella stessa ora (se e' libero)
                for (int h : compagni.get(scelto)) {
                    int dh = docenteDelGruppo.get(h);
                    if (rimaste[h] > 0 && ammessoDocente(docenteOccupato[dh], bloccato[dh], lezioniGiorno[dh], pomGiorno[dh], pomSettimana[dh], migliorSlot, oreGiornaliere)) {
                        int kh = lezioniDelGruppo.get(h).size() - rimaste[h];
                        posizione[lezioniDelGruppo.get(h).get(kh)] = migliorSlot;
                        docenteOccupato[dh][migliorSlot] = true;
                        lezioniGiorno[dh][giorno]++;
                        if (ora >= PRIMA_ORA_POMERIGGIO) {
                            pomGiorno[dh][giorno]++;
                            pomSettimana[dh]++;
                        }
                        rimaste[h]--;
                        daFare--;
                    }
                }
            }

            // penalita' = quanto questo orario viola le preferenze (ore buche, ultima ora, pomeriggi, giorni di servizio)
            double penalita = 0;
            for (int d = 0; d < nDoc; d++) {
                int giorniPomeriggio = 0;
                for (int gi = 0; gi < numGiorni; gi++) {
                    penalita += (compatte[d] ? PESO_BUCHI_PREFERENZA : PESO_BUCHI_DOCENTE) * buchi(docenteOccupato[d], gi, oreGiornaliere);
                    if (evitaUltima[d] && docenteOccupato[d][gi * oreGiornaliere + ULTIMA_ORA - 1]) {
                        penalita += PESO_ULTIMA_ORA;
                    }
                    if (pomGiorno[d][gi] > 0) {
                        giorniPomeriggio++;
                        penalita += PESO_ORA_POMERIGGIO * pomGiorno[d][gi];
                    }
                    if (lezioniGiorno[d][gi] > 0) {
                        penalita += PESO_GIORNO_SERVIZIO;
                    }
                }
                penalita += PESO_GIORNO_POMERIGGIO * giorniPomeriggio;
            }
            if (nonCollocate < minimoNonCollocate || (nonCollocate == minimoNonCollocate && penalita < minimaPenalita)) {
                minimoNonCollocate = nonCollocate;
                minimaPenalita = penalita;
                migliore = posizione;
            }
            if (nonCollocate == 0 && fineOttimizzazione == Long.MAX_VALUE) {
                fineOttimizzazione = System.currentTimeMillis() + TEMPO_OTTIMIZZAZIONE_MS;
            }
        }

        // Applichiamo la soluzione migliore alle lezioni
        List<Lezione> nonCollocate = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Lezione l = lezioni.get(i);
            if (migliore[i] < 0) {
                l.setGiorno(null);
                l.setOra(-1);
                nonCollocate.add(l);
            } else {
                l.setGiorno(nomiGiorni[migliore[i] / oreGiornaliere]);
                l.setOra(migliore[i] % oreGiornaliere + 1);   // 1 = prima ora, 6 = pausa (mai usata)
            }
        }
        return nonCollocate;
    }


    /**
     * Per ogni coppia "classe|materia" con piu' docenti ritorna il nome del docente di teoria: quello con
     * piu' ore (a parita' di ore il primo comparso nel CSV). Gli altri sono di laboratorio, in compresenza con lui.
     */
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
                if (d.getValue() > max) {        // a parita' resta il primo comparso
                    max = d.getValue();
                    migliore = d.getKey();
                }
            }
            teoria.put(e.getKey(), migliore);
        }
        return teoria;
    }

    // slot ammesso per una lezione di teoria: valgono i vincoli del docente di teoria e della classe e,
    // se le ore di teoria rimaste sono tutte necessarie al laboratorio, anche quelli del docente di laboratorio
    private static boolean slotOk(int g, int s, int oreGiornaliere, List<Integer> docenteDelGruppo,
                                  List<Integer> classeDelGruppo, List<List<Integer>> compagni, int[] rimaste,
                                  boolean[][] docenteOccupato, boolean[][] bloccato, boolean[][] classeOccupata,
                                  int[][] lezioniGiorno, int[][] pomGiorno, int[] pomSettimana) {
        int d = docenteDelGruppo.get(g);
        int c = classeDelGruppo.get(g);
        if (!ammesso(docenteOccupato[d], bloccato[d], classeOccupata[c], lezioniGiorno[d], pomGiorno[d], pomSettimana[d], s, oreGiornaliere)) {
            return false;
        }
        for (int h : compagni.get(g)) {
            if (rimaste[h] == 0 || rimaste[g] > rimaste[h]) {
                continue;     // laboratorio finito, oppure c'e' ancora margine per farlo in un altro slot
            }
            int dh = docenteDelGruppo.get(h);
            if (!ammessoDocente(docenteOccupato[dh], bloccato[dh], lezioniGiorno[dh], pomGiorno[dh], pomSettimana[dh], s, oreGiornaliere)) {
                return false;
            }
        }
        return true;
    }

    // vincoli obbligatori su uno slot (s = giorno * oreGiornaliere + ora - 1)
    private static boolean ammesso(boolean[] docenteOcc, boolean[] bloccatoDocente, boolean[] classeOcc,
                                   int[] lezioniGiornoDocente, int[] pomGiornoDocente, int pomSettimanaDocente,
                                   int s, int oreGiornaliere) {
        return !classeOcc[s] && ammessoDocente(docenteOcc, bloccatoDocente, lezioniGiornoDocente, pomGiornoDocente,
                pomSettimanaDocente, s, oreGiornaliere);
    }

    // vincoli che riguardano solo il docente
    private static boolean ammessoDocente(boolean[] docenteOcc, boolean[] bloccatoDocente, int[] lezioniGiornoDocente,
                                          int[] pomGiornoDocente, int pomSettimanaDocente, int s, int oreGiornaliere) {
        int ora = s % oreGiornaliere + 1;
        if (ora == ORA_PAUSA) {
            return false;
        }
        if (docenteOcc[s] || bloccatoDocente[s]) {      // gia' occupato, oppure giorno libero / prima ora esclusa
            return false;
        }
        // ogni docente deve avere almeno un giorno libero: non si puo' iniziare l'ultimo giorno ancora vuoto
        if (lezioniGiornoDocente[s / oreGiornaliere] == 0) {
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
        if (ora >= PRIMA_ORA_POMERIGGIO) {
            if (pomGiornoDocente[s / oreGiornaliere] >= MAX_ORE_POMERIGGIO_GIORNO) {
                return false;
            }
            if (pomSettimanaDocente >= MAX_ORE_POMERIGGIO_SETTIMANA) {
                return false;
            }
        }
        return true;
    }

    // preferenze: costo piu' basso = slot migliore
    private static double costo(boolean[] docenteOcc, boolean[] classeOcc, int[] pomGiornoDocente, int s, int oreGiornaliere,
                                int stessaMateriaStessoGiorno, boolean docenteCompatto, boolean docenteEvitaUltima,
                                Random random) {
        int giorno = s / oreGiornaliere;
        int ora = s % oreGiornaliere + 1;
        double c = costoPomeriggio(pomGiornoDocente, giorno, ora);
        if (docenteEvitaUltima && ora == ULTIMA_ORA) {
            c += PESO_ULTIMA_ORA;                       // preferenza: non finire alle 16
        }
        int buchiDocente = buchi(docenteOcc, giorno, oreGiornaliere);
        int buchiClasse = buchi(classeOcc, giorno, oreGiornaliere);
        boolean giornoNuovo = !haLezioni(docenteOcc, giorno, oreGiornaliere);
        docenteOcc[s] = true;
        classeOcc[s] = true;
        double pesoBuchi = docenteCompatto ? PESO_BUCHI_PREFERENZA : PESO_BUCHI_DOCENTE;
        c += pesoBuchi * (buchi(docenteOcc, giorno, oreGiornaliere) - buchiDocente);   // ore buche del docente
        c += 15.0 * (buchi(classeOcc, giorno, oreGiornaliere) - buchiClasse);     // ore buche della classe
        docenteOcc[s] = false;
        classeOcc[s] = false;
        if (giornoNuovo) {
            c += 8;                                     // meno giorni di servizio
        }
        c += 40.0 * stessaMateriaStessoGiorno;          // distribuzione settimanale della materia
        return c + random.nextDouble() * 6;             // un po' di casualita' tra i tentativi
    }

    // Pomeriggi: meglio la mattina; se un docente deve fare il pomeriggio, meglio tutte le sue ore pomeridiane
    // nello stesso giorno (nessun costo se ha gia' un'ora al pomeriggio quel giorno) e in pochi pomeriggi
    // (ogni pomeriggio in piu' costa di piu': tipicamente 1-2 pomeriggi a settimana per docente)
    private static double costoPomeriggio(int[] pomGiornoDocente, int giorno, int ora) {
        if (ora < PRIMA_ORA_POMERIGGIO) {
            return 0;
        }
        if (pomGiornoDocente[giorno] > 0) {
            return 0;                                   // stesso pomeriggio: ore vicine
        }
        int giorniConPomeriggio = 0;
        for (int p : pomGiornoDocente) {
            if (p > 0) {
                giorniConPomeriggio++;
            }
        }
        return 30 + 40.0 * giorniConPomeriggio;         // nuovo pomeriggio: il secondo costa 70, il terzo 110...
    }

    private static boolean haLezioni(boolean[] occ, int giorno, int oreGiornaliere) {
        for (int ora = 1; ora <= oreGiornaliere; ora++) {
            if (occ[giorno * oreGiornaliere + ora - 1]) {
                return true;
            }
        }
        return false;
    }

    // ore vuote tra la prima e l'ultima lezione del giorno (la pausa 13-14 non e' un'ora buca)
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

    // ------------------------------------------------------------------------------------
    // Controlli
    // ------------------------------------------------------------------------------------

    /** Controllo indipendente dell'orario prodotto: lista vuota = nessun vincolo obbligatorio violato. */
    public static List<String> verifica(List<Lezione> lezioni) {
        return verifica(lezioni, null);
    }

    /** Come sopra, controllando anche i vincoli obbligatori dei docenti (giorno libero, niente prima ora). */
    public static List<String> verifica(List<Lezione> lezioni, GestoreDati vincoli) {
        return verifica(lezioni, vincoli, null);
    }

    /** Come sopra; con i nomi dei giorni controlla anche che ogni docente abbia almeno un giorno libero. */
    public static List<String> verifica(List<Lezione> lezioni, GestoreDati vincoli, String[] nomiGiorni) {
        List<String> errori = new ArrayList<>();
        Map<String, Set<String>> giorniDocente = new TreeMap<>();
        Map<String, Lezione> docenteSlot = new HashMap<>();
        Map<String, Lezione> classeSlot = new HashMap<>();
        Map<String, Integer> pomGiorno = new HashMap<>();
        Map<String, Integer> pomSettimana = new HashMap<>();
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
            // due lezioni insieme nella stessa classe sono ammesse solo se sono teoria + laboratorio della stessa materia
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
                int w = pomSettimana.merge(l.getDocente().getNome(), 1, Integer::sum);
                if (w == MAX_ORE_POMERIGGIO_SETTIMANA + 1) {
                    errori.add("Docente " + l.getDocente().getNome() + " supera il limite settimanale di ore al pomeriggio");
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

    /** Preferenze dei docenti che l'orario generato non ha potuto rispettare, con una motivazione sintetica. */
    public static List<String> preferenzeNonRispettate(List<Lezione> lezioni, GestoreDati vincoli, String[] nomiGiorni) {
        List<String> report = new ArrayList<>();
        if (vincoli == null) {
            return report;
        }
        // occupazione di ogni docente: nome -> giorno -> ora (1..8)
        Map<String, Map<String, boolean[]>> occ = new TreeMap<>();
        for (Lezione l : lezioni) {
            if (l.getGiorno() == null) {
                continue;
            }
            occ.computeIfAbsent(l.getDocente().getNome(), k -> new HashMap<>())
                    .computeIfAbsent(l.getGiorno(), k -> new boolean[ULTIMA_ORA + 1])[l.getOra()] = true;
        }
        for (Map.Entry<String, Map<String, boolean[]>> e : occ.entrySet()) {
            String docente = e.getKey();
            if (vincoli.evitaUltimaOra.contains(docente)) {
                StringBuilder giorni = new StringBuilder();
                for (String g : nomiGiorni) {
                    boolean[] o = e.getValue().get(g);
                    if (o != null && o[ULTIMA_ORA]) {
                        giorni.append(giorni.length() > 0 ? ", " : "").append(g);
                    }
                }
                if (giorni.length() > 0) {
                    report.add("Docente " + docente + ": preferiva non fare l'ultima ora (15:00) ma l'ha in " + giorni
                            + " (nessuno slot alternativo libero compatibile con gli altri vincoli)");
                }
            }
            if (vincoli.oreCompatte.contains(docente)) {
                StringBuilder buchi = new StringBuilder();
                int totale = 0;
                for (String g : nomiGiorni) {
                    boolean[] o = e.getValue().get(g);
                    if (o == null) {
                        continue;
                    }
                    int prima = -1;
                    int ultima = -1;
                    for (int ora = 1; ora <= ULTIMA_ORA; ora++) {
                        if (o[ora]) {
                            if (prima < 0) {
                                prima = ora;
                            }
                            ultima = ora;
                        }
                    }
                    int b = 0;
                    for (int ora = prima; prima > 0 && ora <= ultima; ora++) {
                        if (ora != ORA_PAUSA && !o[ora]) {
                            b++;
                        }
                    }
                    if (b > 0) {
                        totale += b;
                        buchi.append(buchi.length() > 0 ? ", " : "").append(g).append(": ").append(b);
                    }
                }
                if (totale > 0) {
                    report.add("Docente " + docente + ": preferiva ore compatte ma ha " + totale + " ore buche (" + buchi
                            + "); non c'era un'alternativa migliore con gli altri vincoli");
                }
            }
        }
        return report;
    }

    /** Per le lezioni non collocate: quali docenti coinvolti hanno vincoli obbligatori che riducono i loro slot. */
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

    /** Spiega in anticipo perche' un orario completo potrebbe essere impossibile (docenti o classi con troppe ore). */
    public static List<String> controllaCapacita(List<Lezione> lezioni, int numGiorni, int oreGiornaliere) {
        return controllaCapacita(lezioni, numGiorni, oreGiornaliere, null);
    }

    /** Come sopra, togliendo agli slot dei docenti quelli esclusi dai loro vincoli obbligatori. */
    public static List<String> controllaCapacita(List<Lezione> lezioni, int numGiorni, int oreGiornaliere,
                                                 GestoreDati vincoli) {
        List<String> avvisi = new ArrayList<>();
        Map<String, Integer> perDocente = new TreeMap<>();
        Map<String, Integer> perClasse = new TreeMap<>();
        Map<String, String> teoria = docenteTeoria(lezioni);
        for (Lezione l : lezioni) {
            perDocente.merge(l.getDocente().getNome(), 1, Integer::sum);
            String t = teoria.get(l.getClasse().toString() + "|" + l.getMateria().getNome());
            if (t == null || t.equals(l.getDocente().getNome())) {   // il laboratorio e' dentro le ore della teoria
                perClasse.merge(l.getClasse().toString(), 1, Integer::sum);
            }
        }
        int mattina = ORA_PAUSA - 1;
        int pomeriggio = oreGiornaliere - ORA_PAUSA;
        long capacitaClasse = (long) numGiorni * (mattina + pomeriggio);
        for (Map.Entry<String, Integer> e : perDocente.entrySet()) {
            boolean giornoLibero = vincoli != null && vincoli.giornoLibero.containsKey(e.getKey());
            boolean senzaPrima = vincoli != null && vincoli.senzaPrimaOra.contains(e.getKey());
            int giorniAttivi = numGiorni - 1;     // ogni docente ha comunque almeno un giorno libero
            long pomDocente = Math.min((long) giorniAttivi * Math.min(pomeriggio, MAX_ORE_POMERIGGIO_GIORNO),
                    (long) MAX_ORE_POMERIGGIO_SETTIMANA);
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

    // ------------------------------------------------------------------------------------
    // Esportazione PDF (senza librerie esterne): una pagina A4 orizzontale per ogni tabella
    // ------------------------------------------------------------------------------------

    private static final Charset WIN = Charset.forName("windows-1252");
    private static final double PDF_W = 842;
    private static final double PDF_H = 595;

    /**
     * @param titoli    titolo di ogni pagina
     * @param tabelle   una per pagina, indicizzata [ora - 1][giorno] (testo vuoto = cella libera)
     * @param nomiGiorni nomi delle colonne
     */
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
            if (ora == ORA_PAUSA) {                         // riga grigia: 13-14 non assegnabile
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

    // gray >= 0: grigio uniforme; gray < 0: colore pastello derivato dal testo prima di " - "
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
        // a capo semplice: circa 0.5 * size punti per carattere
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
//14/11