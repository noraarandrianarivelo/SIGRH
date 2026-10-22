package com.projet.sigrh.model;

import java.math.BigDecimal;
import java.time.LocalDate;
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
@Table(name = "proposition_embauche")
public class PropositionEmbauche {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_candidature", nullable = false)
    public Candidature candidature;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_emetteur", nullable = false)
    public Employe emetteur;

    @Column(name = "salaire_mensuel", nullable = false, precision = 14, scale = 2)
    public BigDecimal salaireMensuel;

    @Column(name = "date_debut_prevue", nullable = false)
    public LocalDate dateDebutPrevue;

    @Column(name = "type_contrat", nullable = false, length = 30)
    public String typeContrat;

    @Column(length = 150)
    public String horaires;

    @Column(name = "lieu_travail", length = 150)
    public String lieuTravail;

    @Column(name = "autres_conditions")
    public String autresConditions;

    @Column(name = "date_envoi", nullable = false)
    public LocalDateTime dateEnvoi = LocalDateTime.now();

    @Column(name = "date_reponse")
    public LocalDateTime dateReponse;

    @Column(nullable = false, length = 20)
    public String statut = "EN_ATTENTE";

    @Column(name = "message_reponse")
    public String messageReponse;
}
