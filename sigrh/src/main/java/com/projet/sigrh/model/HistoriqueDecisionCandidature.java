package com.projet.sigrh.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "historique_decision_candidature")
public class HistoriqueDecisionCandidature {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_candidature", nullable = false)
    public Candidature candidature;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_decideur", nullable = false)
    public Employe decideur;

    @ManyToOne(optional = false)
    @JoinColumn(name = "iddecision", nullable = false)
    public DecisionCandidature decision;

    @Column(name = "date_decision")
    public LocalDateTime dateDecision = LocalDateTime.now();
    @Column(name = "motif")
    public String motif;
}
