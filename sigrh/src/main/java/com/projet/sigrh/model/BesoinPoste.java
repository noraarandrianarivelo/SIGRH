package com.projet.sigrh.model;

import jakarta.persistence.*;

@Entity
@Table(name = "besoin_poste")
public class BesoinPoste {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "id_besoin", nullable = false)
    private Besoin besoin;

    @ManyToOne
    @JoinColumn(name = "id_poste")
    private Poste poste;

    @Column(name = "libelle_poste_propose")
    private String libellePostePropose;

    @Column(nullable = false)
    private int effectif;

    @Column(name = "budget_mensuel_unitaire", nullable = false)
    private double budgetMensuelUnitaire;

    // Getters et Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Besoin getBesoin() { return besoin; }
    public void setBesoin(Besoin besoin) { this.besoin = besoin; }

    public Poste getPoste() { return poste; }
    public void setPoste(Poste poste) { this.poste = poste; }

    public String getLibellePostePropose() { return libellePostePropose; }
    public void setLibellePostePropose(String libellePostePropose) { this.libellePostePropose = libellePostePropose; }

    public int getEffectif() { return effectif; }
    public void setEffectif(int effectif) { this.effectif = effectif; }

    public double getBudgetMensuelUnitaire() { return budgetMensuelUnitaire; }
    public void setBudgetMensuelUnitaire(double budgetMensuelUnitaire) { this.budgetMensuelUnitaire = budgetMensuelUnitaire; }
}