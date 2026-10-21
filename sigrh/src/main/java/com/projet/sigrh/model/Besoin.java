package com.projet.sigrh.model;

import com.projet.sigrh.model.enums.TypeMotif;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "besoin")
public class Besoin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "id_departement", nullable = false)
    private Departement departement;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_motif", nullable = false, length = 30)
    private TypeMotif typeMotif;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String motif;

    // Un besoin peut demander plusieurs postes (BESOINPOSTE), chacun avec son effectif.
    @OneToMany(mappedBy = "besoin", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<BesoinPoste> lignes = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Departement getDepartement() { return departement; }
    public void setDepartement(Departement departement) { this.departement = departement; }

    public TypeMotif getTypeMotif() { return typeMotif; }
    public void setTypeMotif(TypeMotif typeMotif) { this.typeMotif = typeMotif; }

    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }

    public List<BesoinPoste> getLignes() { return lignes; }
    public void setLignes(List<BesoinPoste> lignes) { this.lignes = lignes; }
}