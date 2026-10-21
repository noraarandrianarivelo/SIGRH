package com.projet.sigrh.model;

import java.time.LocalDateTime;

import com.projet.sigrh.model.enums.DecisionValidation;
import com.projet.sigrh.model.enums.EtapeValidation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// Une ligne par décision : rien n'est jamais écrasé.
@Entity
@Table(name = "historique_validation")
public class HistoriqueValidation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_demande", nullable = false)
    private Long idDemande;

    @ManyToOne
    @JoinColumn(name = "id_validateur", nullable = false)
    private Employe validateur;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EtapeValidation etape;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DecisionValidation decision;

    @Column(name = "date_validation", nullable = false)
    private LocalDateTime dateValidation = LocalDateTime.now();

    @Column(columnDefinition = "TEXT")
    private String commentaire;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getIdDemande() { return idDemande; }
    public void setIdDemande(Long idDemande) { this.idDemande = idDemande; }

    public Employe getValidateur() { return validateur; }
    public void setValidateur(Employe validateur) { this.validateur = validateur; }

    public EtapeValidation getEtape() { return etape; }
    public void setEtape(EtapeValidation etape) { this.etape = etape; }

    public DecisionValidation getDecision() { return decision; }
    public void setDecision(DecisionValidation decision) { this.decision = decision; }

    public LocalDateTime getDateValidation() { return dateValidation; }
    public void setDateValidation(LocalDateTime dateValidation) { this.dateValidation = dateValidation; }

    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }
}