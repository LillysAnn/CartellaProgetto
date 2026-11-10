package org.example;

public class Lezione {
    private String id;
    private Docente docente;
    private Materia materia;
    private Classe classe;

    // Campi per l'orario (verranno valorizzati nello Step 2)
    private String giorno; // es. "Lunedi"
    private int ora;       // es. 1 (prima ora)

    public Lezione(String id, Docente docente, Materia materia, Classe classe) {
        this.id = id;
        this.docente = docente;
        this.materia = materia;
        this.classe = classe;
        this.giorno = null;
        this.ora = -1;
    }

    public String getId() {
        return id; }
    public Docente getDocente() {
        return docente; }
    public Materia getMateria() {
        return materia; }
    public Classe getClasse() {
        return classe; }

    public String getGiorno() {
        return giorno; }
    public void setGiorno(String giorno) {
        this.giorno = giorno; }

    public int getOra() {
        return ora; }
    public void setOra(int ora) {
        this.ora = ora; }
}
