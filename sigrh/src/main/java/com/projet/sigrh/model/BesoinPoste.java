package com.projet.sigrh.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "besoin_poste")
public class BesoinPoste {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "id_besoin", nullable = false)
    private Besoin besoin;

    // Poste existant. Reste null tant que la RH n'a pas créé le poste proposé.
    @ManyToOne
    @JoinColumn(name = "id_poste")
    private Poste poste;

    @Column(name = "libelle_poste_propose", length = 100)
    private String libellePostePropose;

    @Column(nullable = false)
    private Integer effectif;

    @Column(name = "budget_mensuel_unitaire", nullable = false, precision = 14, scale = 2)
    private BigDecimal budgetMensuelUnitaire;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Besoin getBesoin() { return besoin; }
    public void setBesoin(Besoin besoin) { this.besoin = besoin; }

    public Poste getPoste() { return poste; }
    public void setPoste(Poste poste) { this.poste = poste; }

    public String getLibellePostePropose() { return libellePostePropose; }
    public void setLibellePostePropose(String libellePostePropose) { this.libellePostePropose = libellePostePropose; }

    public Integer getEffectif() { return effectif; }
    public void setEffectif(Integer effectif) { this.effectif = effectif; }

    public BigDecimal getBudgetMensuelUnitaire() { return budgetMensuelUnitaire; }
    public void setBudgetMensuelUnitaire(BigDecimal budgetMensuelUnitaire) { this.budgetMensuelUnitaire = budgetMensuelUnitaire; }
}