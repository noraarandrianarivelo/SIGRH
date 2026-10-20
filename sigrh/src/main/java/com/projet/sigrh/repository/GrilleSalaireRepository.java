package com.projet.sigrh.repository;

import com.projet.sigrh.model.GrilleSalaire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GrilleSalaireRepository extends JpaRepository<GrilleSalaire, Long> {
    Optional<GrilleSalaire> findByPosteId(Long posteId);
}