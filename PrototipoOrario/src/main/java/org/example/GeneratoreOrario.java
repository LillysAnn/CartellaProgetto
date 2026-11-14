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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
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
 *
 * Preferenze (soft, pesate nel costo di ogni slot): meglio la mattina, niente ore buche
 * per docente e classe, stessa materia della stessa classe su giorni diversi, pochi giorni
 * di servizio per ogni docente.
 *
 * Fa piu' tentativi e tiene il migliore (quello con meno lezioni non collocate).
 */
public class GeneratoreOrario {

    public static final int ORA_PAUSA = 6;
    public static final int PRIMA_ORA_POMERIGGIO = 7;
    public static final int MAX_ORE_POMERIGGIO_GIORNO = 2;
    // limite settimanale di ore pomeridiane per docente (MAX_VALUE = nessun limite)
    public static final int MAX_ORE_POMERIGGIO_SETTIMANA = Integer.MAX_VALUE;

    private static final int TENTATIVI = 200;
    private static final long TEMPO_MAX_MS = 8000;

    /**
     * @param nomiGiorni     nomi dei giorni (es. "Lunedi", ...), se ne usano i primi numGiorni
     * @param numGiorni      giorni di lezione
     * @param oreGiornaliere righe della giornata (8: 8-13, pausa 13-14, 14-16)
     * @return le lezioni che non e' stato possibile collocare (lista vuota = orario completo)
     */
    public static List<Lezione> genera(List<Lezione> lezioni, String[] nomiGiorni, int numGiorni, int oreGiornaliere) {
        int n = lezioni.size();
        int slot = numGiorni * oreGiornaliere;

        // Numero progressivo per ogni docente, classe e "gruppo" (docente+classe+materia)
        Map<String, Integer> idDocenti = new HashMap<>();
        Map<String, Integer> idClassi = new HashMap<>();
        Map<String, Integer> idGruppi = new HashMap<>();
        List<List<Integer>> lezioniDelGruppo = new ArrayList<>();
        List<Integer> docenteDelGruppo = new ArrayList<>();
        List<Integer> classeDelGruppo = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Lezione l = lezioni.get(i);
            int d = idDocenti.computeIfAbsent(l.getDocente().getNome(), k -> idDocenti.size());
            int c = idClassi.computeIfAbsent(l.getClasse().getNome(), k -> idClassi.size());
            String chiave = l.getDocente().getNome() + "|" + l.getClasse().getNome() + "|" + l.getMateria().getNome();
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

        Random random = new Random();
        int[] migliore = new int[n];
        Arrays.fill(migliore, -1);
        int minimoNonCollocate = Integer.MAX_VALUE;
        long fine = System.currentTimeMillis() + TEMPO_MAX_MS;

        for (int t = 0; t < TENTATIVI && minimoNonCollocate > 0 && (t == 0 || System.currentTimeMillis() < fine); t++) {
            boolean[][] docenteOccupato = new boolean[nDoc][slot];
            boolean[][] classeOccupata = new boolean[nCla][slot];
            int[][] pomGiorno = new int[nDoc][numGiorni];
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
                    if (rimaste[g] == 0) {
                        continue;
                    }
                    int d = docenteDelGruppo.get(g);
                    int c = classeDelGruppo.get(g);
                    int opzioni = 0;
                    for (int s = 0; s < slot; s++) {
                        if (ammesso(docenteOccupato[d], classeOccupata[c], pomGiorno[d], pomSettimana[d], s, oreGiornaliere)) {
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
                    continue;
                }

                // 2. si sceglie lo slot ammesso con il costo piu' basso
                int d = docenteDelGruppo.get(scelto);
                int c = classeDelGruppo.get(scelto);
                int migliorSlot = -1;
                double migliorCosto = Double.MAX_VALUE;
                for (int s = 0; s < slot; s++) {
                    if (!ammesso(docenteOccupato[d], classeOccupata[c], pomGiorno[d], pomSettimana[d], s, oreGiornaliere)) {
                        continue;
                    }
                    double costo = costo(docenteOccupato[d], classeOccupata[c], s, oreGiornaliere,
                            gruppoGiorno[scelto][s / oreGiornaliere], random);
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
                gruppoGiorno[scelto][giorno]++;
                if (ora >= PRIMA_ORA_POMERIGGIO) {
                    pomGiorno[d][giorno]++;
                    pomSettimana[d]++;
                }
                rimaste[scelto]--;
                daFare--;
            }

            if (nonCollocate < minimoNonCollocate) {
                minimoNonCollocate = nonCollocate;
                migliore = posizione;
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


    // vincoli obbligatori su uno slot (s = giorno * oreGiornaliere + ora - 1)
    private static boolean ammesso(boolean[] docenteOcc, boolean[] classeOcc, int[] pomGiornoDocente,
                                   int pomSettimanaDocente, int s, int oreGiornaliere) {
        int ora = s % oreGiornaliere + 1;
        if (ora == ORA_PAUSA) {
            return false;
        }
        if (docenteOcc[s] || classeOcc[s]) {
            return false;
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
    private static double costo(boolean[] docenteOcc, boolean[] classeOcc, int s, int oreGiornaliere,
                                int stessaMateriaStessoGiorno, Random random) {
        int giorno = s / oreGiornaliere;
        int ora = s % oreGiornaliere + 1;
        double c = 0;
        if (ora >= PRIMA_ORA_POMERIGGIO) {
            c += 30;                                    // meglio la mattina
        }
        int buchiDocente = buchi(docenteOcc, giorno, oreGiornaliere);
        int buchiClasse = buchi(classeOcc, giorno, oreGiornaliere);
        boolean giornoNuovo = !haLezioni(docenteOcc, giorno, oreGiornaliere);
        docenteOcc[s] = true;
        classeOcc[s] = true;
        c += 10.0 * (buchi(docenteOcc, giorno, oreGiornaliere) - buchiDocente);   // ore buche del docente
        c += 15.0 * (buchi(classeOcc, giorno, oreGiornaliere) - buchiClasse);     // ore buche della classe
        docenteOcc[s] = false;
        classeOcc[s] = false;
        if (giornoNuovo) {
            c += 8;                                     // meno giorni di servizio
        }
        c += 40.0 * stessaMateriaStessoGiorno;          // distribuzione settimanale della materia
        return c + random.nextDouble() * 6;             // un po' di casualita' tra i tentativi
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
        List<String> errori = new ArrayList<>();
        Map<String, Lezione> docenteSlot = new HashMap<>();
        Map<String, Lezione> classeSlot = new HashMap<>();
        Map<String, Integer> pomGiorno = new HashMap<>();
        Map<String, Integer> pomSettimana = new HashMap<>();
        for (Lezione l : lezioni) {
            if (l.getGiorno() == null) {
                continue;
            }
            String quando = l.getGiorno() + " ora " + l.getOra();
            if (l.getOra() == ORA_PAUSA) {
                errori.add("Lezione nella pausa 13-14: " + l.getId());
            }
            Lezione altra = docenteSlot.put(l.getDocente().getNome() + "|" + quando, l);
            if (altra != null) {
                errori.add("Docente " + l.getDocente().getNome() + " ha due lezioni insieme (" + quando + ")");
            }
            altra = classeSlot.put(l.getClasse().getNome() + "|" + quando, l);
            if (altra != null) {
                errori.add("Classe " + l.getClasse().getNome() + " ha due lezioni insieme (" + quando + ")");
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
        return errori;
    }

    /** Spiega in anticipo perche' un orario completo potrebbe essere impossibile (docenti o classi con troppe ore). */
    public static List<String> controllaCapacita(List<Lezione> lezioni, int numGiorni, int oreGiornaliere) {
        List<String> avvisi = new ArrayList<>();
        Map<String, Integer> perDocente = new TreeMap<>();
        Map<String, Integer> perClasse = new TreeMap<>();
        for (Lezione l : lezioni) {
            perDocente.merge(l.getDocente().getNome(), 1, Integer::sum);
            perClasse.merge(l.getClasse().getNome(), 1, Integer::sum);
        }
        int mattina = ORA_PAUSA - 1;
        int pomeriggio = oreGiornaliere - ORA_PAUSA;
        long capacitaClasse = (long) numGiorni * (mattina + pomeriggio);
        long pomDocente = Math.min((long) numGiorni * Math.min(pomeriggio, MAX_ORE_POMERIGGIO_GIORNO),
                (long) MAX_ORE_POMERIGGIO_SETTIMANA);
        long capacitaDocente = (long) numGiorni * mattina + pomDocente;
        for (Map.Entry<String, Integer> e : perDocente.entrySet()) {
            if (e.getValue() > capacitaDocente) {
                avvisi.add("Docente " + e.getKey() + ": " + e.getValue() + " ore richieste, massimo possibile " + capacitaDocente);
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