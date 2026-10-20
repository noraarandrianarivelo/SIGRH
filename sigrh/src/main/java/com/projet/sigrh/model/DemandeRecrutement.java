package com.projet.sigrh.model;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "demande_recrutement")
public class DemandeRecrutement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "id_besoin", nullable = false, unique = true)
    private Besoin besoin;

    @ManyToOne
    @JoinColumn(name = "id_demandeur", nullable = false)
    private Employe demandeur;

    @Column(name = "date_creation", nullable = false)
    private LocalDate dateCreation = LocalDate.now();

    @Column(name = "date_arrivee_souhaitee")
    private LocalDate dateArriveeSouhaitee;

    @Column(nullable = false, length = 20)
    private String circuit = "STANDARD"; // STANDARD ou DIRECTION_DIRECTE

    @Column(name = "validation_finance", nullable = false)
    private boolean validationFinance = false;

    @Column(nullable = false, length = 30)
    private String statut = "BROUILLON"; 
    // BROUILLON, ATTENTE_RH, ATTENTE_FINANCE, ATTENTE_DIRECTION, A_CORRIGER, VALIDEE, REJETEE

    // Getters et Setters
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
    public String getCircuit() { return circuit; }
    public void setCircuit(String circuit) { this.circuit = circuit; }
    public boolean isValidationFinance() { return validationFinance; }
    public void setValidationFinance(boolean validationFinance) { this.validationFinance = validationFinance; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
}