package com.projet.sigrh.dto;

import com.projet.sigrh.model.Besoin;
import com.projet.sigrh.model.BesoinPoste;

import java.time.LocalDate;

public class CreerDemandeRequest {
    private Besoin besoin;
    private BesoinPoste besoinPoste;
    private String idDemandeur;
    private LocalDate dateArriveeSouhaitee;
    private String circuit;
    private boolean validationFinance;

    // Getters et Setters
    public Besoin getBesoin() { return besoin; }
    public void setBesoin(Besoin besoin) { this.besoin = besoin; }

    public BesoinPoste getBesoinPoste() { return besoinPoste; }
    public void setBesoinPoste(BesoinPoste besoinPoste) { this.besoinPoste = besoinPoste; }

    public String getIdDemandeur() { return idDemandeur; }
    public void setIdDemandeur(String idDemandeur) { this.idDemandeur = idDemandeur; }

    public LocalDate getDateArriveeSouhaitee() { return dateArriveeSouhaitee; }
    public void setDateArriveeSouhaitee(LocalDate dateArriveeSouhaitee) { this.dateArriveeSouhaitee = dateArriveeSouhaitee; }

    public String getCircuit() { return circuit; }
    public void setCircuit(String circuit) { this.circuit = circuit; }

    public boolean isValidationFinance() { return validationFinance; }
    public void setValidationFinance(boolean validationFinance) { this.validationFinance = validationFinance; }
}