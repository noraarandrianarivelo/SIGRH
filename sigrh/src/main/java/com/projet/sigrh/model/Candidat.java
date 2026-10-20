package com.projet.sigrh.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "candidat")
public class Candidat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Integer id;
    @Column(name = "nom")
    public String nom;
    @Column(name = "prenom")
    public String prenom;
    @Column(name = "date_naissance")
    public LocalDate dateNaissance;
    @Column(name = "email")
    public String email;
    @Column(name = "telephone")
    public String telephone;
    @Column(name = "qualifications")
    public String qualifications;

    @OneToMany(mappedBy = "candidat")
    public List<Candidature> candidatures = new ArrayList<>();
}
