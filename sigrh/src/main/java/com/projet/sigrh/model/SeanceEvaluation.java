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
@Table(name = "seance_evaluation")
public class SeanceEvaluation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_candidature", nullable = false)
    public Candidature candidature;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_evaluateur", nullable = false)
    public Employe evaluateur;

    @ManyToOne
    @JoinColumn(name = "id_test")
    public TestRecrutement test;

    @Column(name = "date_entretien")
    public LocalDateTime dateEntretien;
    @Column(name = "date_test")
    public LocalDateTime dateTest;
    @Column(name = "lieu_entretien")
    public String lieuEntretien;
    @Column(name = "lieu_test")
    public String lieuTest;
    @Column(name = "statut")
    public String statut = "PLANIFIEE";
}
