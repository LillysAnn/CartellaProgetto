package org.example;

public class Classe {
    private int anno;
    private char sezione;
    private String indirizzo;
    private int oreSettimanali;

    public Classe(int anno, char sezione, String indirizzo, int oreSettimanali) {
        this.anno = anno;
        this.sezione = sezione;
        this.indirizzo = indirizzo;
        this.oreSettimanali = oreSettimanali;
    }

    public int getAnno() {
        return anno;
    }

    public char getSezione() {
        return sezione;
    }

    public String getIndirizzo() {
        return indirizzo;
    }

    public int getOreSettimanali() {
        return oreSettimanali;
    }

    // Nome breve che identifica la classe (es. "5A INFO"): serve per la tabella e per i controlli di sovrapposizione
    public String getNome() {
        return anno + "" + sezione + " " + indirizzo;
    }

    public String toString(){
        return "Classe " + anno + " sezione " + sezione + " indirizzo " + indirizzo+ " oreSettimanali " + oreSettimanali;
        //return "" + anno + sezione + indirizzo + oreSettimanali;
    }
}

