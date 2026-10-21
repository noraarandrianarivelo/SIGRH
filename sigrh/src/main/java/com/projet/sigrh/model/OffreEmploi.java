package com.projet.sigrh.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "offre_emploi")
public class OffreEmploi {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_besoin_poste", nullable = false)
    public BesoinPoste besoinPoste;

    @Column(name = "titre")
    public String titre;
    @Column(name = "description")
    public String description;
    @Column(name = "date_creation")
    public LocalDate dateCreation = LocalDate.now();
    @Column(name = "date_publication")
    public LocalDate datePublication;
    @Column(name = "date_limite")
    public LocalDate dateLimite;
    @Column(name = "statut")
    public String statut = "BROUILLON";
}
