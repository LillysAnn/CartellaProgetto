package org.example;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Primo generatore (Step 2/3): colloca a caso le lezioni negli slot liberi,
 * rispettando due vincoli: un docente non puo' avere due lezioni nello stesso slot
 * e una classe non puo' avere due lezioni nello stesso slot.
 * Fa piu' tentativi e tiene il migliore (quello con meno lezioni non collocate).
 */
public class GeneratoreOrario {

    private static final int TENTATIVI = 200;

    /**
     * @param nomiGiorni     nomi dei giorni (es. "Lunedi", ...), se ne usano i primi numGiorni
     * @param numGiorni      giorni di lezione
     * @param oreGiornaliere ore di lezione per giorno
     * @return le lezioni che non e' stato possibile collocare (lista vuota = orario completo)
     */
    public static List<Lezione> genera(List<Lezione> lezioni, String[] nomiGiorni, int numGiorni, int oreGiornaliere) {
        int n = lezioni.size();
        int slot = numGiorni * oreGiornaliere;

        // Numero progressivo per ogni docente e ogni classe (piu' veloce delle stringhe)
        Map<String, Integer> idDocenti = new HashMap<>();
        Map<String, Integer> idClassi = new HashMap<>();
        int[] docenteDi = new int[n];
        int[] classeDi = new int[n];
        for (int i = 0; i < n; i++) {
            Lezione l = lezioni.get(i);
            docenteDi[i] = idDocenti.computeIfAbsent(l.getDocente().getNome(), k -> idDocenti.size());
            classeDi[i] = idClassi.computeIfAbsent(l.getClasse().getNome(), k -> idClassi.size());
        }

        Random random = new Random();
        List<Integer> indici = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            indici.add(i);
        }

        int[] migliore = new int[n];
        Arrays.fill(migliore, -1);
        int minimoNonCollocate = Integer.MAX_VALUE;

        for (int t = 0; t < TENTATIVI && minimoNonCollocate > 0; t++) {
            Collections.shuffle(indici, random);
            boolean[][] docenteOccupato = new boolean[idDocenti.size()][slot];
            boolean[][] classeOccupata = new boolean[idClassi.size()][slot];
            int[] posizione = new int[n];
            Arrays.fill(posizione, -1);
            int nonCollocate = 0;

            int[] liberi = new int[slot];
            for (int idx : indici) {
                int nLiberi = 0;
                for (int s = 0; s < slot; s++) {
                    if (!docenteOccupato[docenteDi[idx]][s] && !classeOccupata[classeDi[idx]][s]) {
                        liberi[nLiberi++] = s;
                    }
                }
                if (nLiberi == 0) {
                    nonCollocate++;
                    continue;
                }
                int scelto = liberi[random.nextInt(nLiberi)];
                docenteOccupato[docenteDi[idx]][scelto] = true;
                classeOccupata[classeDi[idx]][scelto] = true;
                posizione[idx] = scelto;
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
                l.setOra(migliore[i] % oreGiornaliere + 1);   // 1 = prima ora
            }
        }
        return nonCollocate;
    }
}
