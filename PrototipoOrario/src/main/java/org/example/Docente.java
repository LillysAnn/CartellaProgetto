package org.example;

public class Docente {
    private String nomeCognome;


    public Docente(String nome) {
        this.nomeCognome = nome;
    }

    public String getNome() {
        return nomeCognome;
    }

    public String toString() {
        return nomeCognome;
    }
}

