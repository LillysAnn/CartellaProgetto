package org.example;

public class Classe {
    private int anno;
    private char sezione;
    private String indirizzo;

    public Classe(int anno, char sezione, String indirizzo) {
        this.anno = anno;
        this.sezione = sezione;
        this.indirizzo = indirizzo;
    }

    public String toString(){
        return "Classe " + anno + " sezione " + sezione + " indirizzo " + indirizzo;
        //return "" + anno + sezione + indirizzo;
    }
}
