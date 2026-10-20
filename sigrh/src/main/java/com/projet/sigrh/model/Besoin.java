package com.projet.sigrh.model;
import jakarta.persistence.*;

@Entity
@Table(name = "besoin")
public class Besoin {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "id_departement", nullable = false)
    private Departement departement;

    @Column(name = "type_motif", nullable = false, length = 30)
    private String typeMotif; // REMPLACEMENT, NOUVEAU_POSTE, NOUVEAU_SERVICE, AUTRE

    @Column(nullable = false, columnDefinition = "TEXT")
    private String motif;

    // Getters et Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Departement getDepartement() { return departement; }
    public void setDepartement(Departement departement) { this.departement = departement; }
    public String getTypeMotif() { return typeMotif; }
    public void setTypeMotif(String typeMotif) { this.typeMotif = typeMotif; }
    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }
}