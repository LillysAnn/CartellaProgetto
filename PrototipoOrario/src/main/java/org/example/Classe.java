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

    public String toString(){
        return "Classe " + anno + " sezione " + sezione + " indirizzo " + indirizzo+ " oreSettimanali " + oreSettimanali;
        //return "" + anno + sezione + indirizzo + oreSettimanali;
    }
}
