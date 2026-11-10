package org.example;

public class Materia {
    private String nMateria;

    public Materia(String m){
        this.nMateria = m;
    }

    public String getNome() {
        return nMateria;
    }

    public String toString(){
        return "Materia " + this.nMateria;

    }

}
