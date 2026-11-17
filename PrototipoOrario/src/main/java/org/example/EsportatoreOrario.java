package org.example;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EsportatoreOrario {

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
                    if (ora == GeneratoreOrario.ORA_PAUSA) {
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
            if (ora == GeneratoreOrario.ORA_PAUSA) {
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