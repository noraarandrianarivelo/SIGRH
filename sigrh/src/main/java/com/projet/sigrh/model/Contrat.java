package com.projet.sigrh.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "contrat")
public class Contrat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;

    @OneToOne
    @JoinColumn(name = "id_proposition", unique = true)
    public PropositionEmbauche proposition;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_employe", nullable = false)
    public Employe employe;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_poste", nullable = false)
    public Poste poste;

    @Column(name = "type_contrat", nullable = false, length = 30)
    public String typeContrat;

    @Column(name = "date_debut", nullable = false)
    public LocalDate dateDebut;

    @Column(name = "date_fin")
    public LocalDate dateFin;

    @Column(name = "date_signature")
    public LocalDate dateSignature;

    @Column(name = "salaire_mensuel", nullable = false, precision = 14, scale = 2)
    public BigDecimal salaireMensuel;

    @Column(length = 150)
    public String horaires;

    @Column(name = "lieu_travail", length = 150)
    public String lieuTravail;

    @Column(name = "conditions")
    public String conditions;

    @Column(name = "date_creation", nullable = false)
    public LocalDate dateCreation = LocalDate.now();

    @Column(nullable = false, length = 20)
    public String statut = "BROUILLON";
}
