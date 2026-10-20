package com.projet.sigrh.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "fiche_note_evaluation")
public class FicheNoteEvaluation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Integer id;

    @OneToOne(optional = false)
    @JoinColumn(name = "id_seance", nullable = false, unique = true)
    public SeanceEvaluation seance;

    @Column(name = "note_entretien")
    public BigDecimal noteEntretien;
    @Column(name = "note_test")
    public BigDecimal noteTest;
    @Column(name = "commentaire")
    public String commentaire;
    @Column(name = "avis")
    public String avis;
}
