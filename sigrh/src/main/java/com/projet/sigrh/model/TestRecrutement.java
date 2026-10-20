package com.projet.sigrh.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "test_recrutement")
public class TestRecrutement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Integer id;
    @Column(name = "niveau")
    public Integer niveau;
    @Column(name = "description")
    public String description;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_responsable", nullable = false)
    public Employe responsable;
}
