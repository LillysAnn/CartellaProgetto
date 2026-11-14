package org.example;

public class Assegnazione {
    private Docente docente;
    private Materia materia;
    private Classe classe;
    private int oreSettimanali;

    public Assegnazione(Docente docente, Materia materia, Classe classe, int oreSettimanali) {
        this.docente = docente;
        this.materia = materia;
        this.classe = classe;
        this.oreSettimanali = oreSettimanali;
    }

    public Docente getDocente() {
        return docente; }
    public Materia getMateria() {
        return materia; }
    public Classe getClasse() {
        return classe; }
    public int getOreSettimanali() {
        return oreSettimanali; }
}
//14/11
