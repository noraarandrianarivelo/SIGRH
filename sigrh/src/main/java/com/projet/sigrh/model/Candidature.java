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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "candidature", uniqueConstraints = @UniqueConstraint(columnNames = {"id_candidat", "id_offre"}))
public class Candidature {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_candidat", nullable = false)
    public Candidat candidat;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_offre", nullable = false)
    public OffreEmploi offre;

    @Column(name = "cv", nullable = false)
    public String cv;
    @Column(name = "lettre_motivation")
    public String lettreMotivation;
    @Column(name = "message")
    public String message;
    @Column(name = "date_candidature")
    public LocalDateTime dateCandidature = LocalDateTime.now();
    @Column(name = "statut")
    public String statut = "RECUE";
}
