package com.projet.sigrh.repository;

import com.projet.sigrh.model.BesoinPoste;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BesoinPosteRepository extends JpaRepository<BesoinPoste, Long> {
    List<BesoinPoste> findByBesoinId(Long besoinId);
}