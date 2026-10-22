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
@Table(name = "affectation")
public class Affectation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_employe", nullable = false)
    public Employe employe;

    @ManyToOne
    @JoinColumn(name = "id_contrat")
    public Contrat contrat;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_poste", nullable = false)
    public Poste poste;

    @Column(length = 150)
    public String lieu;

    @Column(name = "date_debut", nullable = false)
    public LocalDate dateDebut;

    @Column(name = "date_fin")
    public LocalDate dateFin;

    @Column(name = "motif")
    public String motif;
}
