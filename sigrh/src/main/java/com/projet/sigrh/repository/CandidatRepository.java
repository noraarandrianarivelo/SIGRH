package com.projet.sigrh.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projet.sigrh.model.Candidat;

public interface CandidatRepository extends JpaRepository<Candidat, Integer> {
}
