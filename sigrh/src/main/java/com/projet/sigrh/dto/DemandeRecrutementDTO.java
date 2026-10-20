package com.projet.sigrh.dto;

import java.time.LocalDate;

public class DemandeRecrutementDTO {
    private Long departementId;
    private String typeBesoin; // "EXISTANT" ou "CREATION"
    private Long posteId;
    private String libelleNouveauPoste;
    private String typeMotif; // "REMPLACEMENT" ou "NOUVEAU_POSTE"
    private String motif;
    private int effectif;
    private double budgetMensuelUnitaire;
    private LocalDate dateArriveeSouhaitee;

    // Getters et Setters
    public Long getDepartementId() { return departementId; }
    public void setDepartementId(Long departementId) { this.departementId = departementId; }

    public String getTypeBesoin() { return typeBesoin; }
    public void setTypeBesoin(String typeBesoin) { this.typeBesoin = typeBesoin; }

    public Long getPosteId() { return posteId; }
    public void setPosteId(Long posteId) { this.posteId = posteId; }

    public String getLibelleNouveauPoste() { return libelleNouveauPoste; }
    public void setLibelleNouveauPoste(String libelleNouveauPoste) { this.libelleNouveauPoste = libelleNouveauPoste; }

    public String getTypeMotif() { return typeMotif; }
    public void setTypeMotif(String typeMotif) { this.typeMotif = typeMotif; }

    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }

    public int getEffectif() { return effectif; }
    public void setEffectif(int effectif) { this.effectif = effectif; }

    public double getBudgetMensuelUnitaire() { return budgetMensuelUnitaire; }
    public void setBudgetMensuelUnitaire(double budgetMensuelUnitaire) { this.budgetMensuelUnitaire = budgetMensuelUnitaire; }

    public LocalDate getDateArriveeSouhaitee() { return dateArriveeSouhaitee; }
    public void setDateArriveeSouhaitee(LocalDate dateArriveeSouhaitee) { this.dateArriveeSouhaitee = dateArriveeSouhaitee; }
}