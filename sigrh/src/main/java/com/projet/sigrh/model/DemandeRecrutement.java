package com.projet.sigrh.model;

import java.time.LocalDate;

import com.projet.sigrh.model.enums.Circuit;
import com.projet.sigrh.model.enums.StatutDemande;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "demande_recrutement")
public class DemandeRecrutement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "id_besoin", nullable = false, unique = true)
    private Besoin besoin;

    @ManyToOne
    @JoinColumn(name = "id_demandeur", nullable = false)
    private Employe demandeur;

    @Column(name = "date_creation", nullable = false)
    private LocalDate dateCreation = LocalDate.now();

    @Column(name = "date_arrivee_souhaitee")
    private LocalDate dateArriveeSouhaitee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Circuit circuit = Circuit.STANDARD;

    // Contrôle Finance requis ou non pour cette demande.
    @Column(name = "validation_finance", nullable = false)
    private boolean validationFinance = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatutDemande statut = StatutDemande.BROUILLON;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Besoin getBesoin() { return besoin; }
    public void setBesoin(Besoin besoin) { this.besoin = besoin; }

    public Employe getDemandeur() { return demandeur; }
    public void setDemandeur(Employe demandeur) { this.demandeur = demandeur; }

    public LocalDate getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDate dateCreation) { this.dateCreation = dateCreation; }

    public LocalDate getDateArriveeSouhaitee() { return dateArriveeSouhaitee; }
    public void setDateArriveeSouhaitee(LocalDate dateArriveeSouhaitee) { this.dateArriveeSouhaitee = dateArriveeSouhaitee; }

    public Circuit getCircuit() { return circuit; }
    public void setCircuit(Circuit circuit) { this.circuit = circuit; }

    public boolean isValidationFinance() { return validationFinance; }
    public void setValidationFinance(boolean validationFinance) { this.validationFinance = validationFinance; }

    public StatutDemande getStatut() { return statut; }
    public void setStatut(StatutDemande statut) { this.statut = statut; }
}