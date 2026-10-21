package com.projet.sigrh.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.projet.sigrh.model.Poste;

@Repository
public interface PosteRepository extends JpaRepository<Poste, Long> {

    List<Poste> findByDepartementId(Long departementId);

    // Évite de créer deux fois le même poste quand la RH valide un poste proposé.
    Optional<Poste> findFirstByDepartementIdAndLibelleIgnoreCase(Long departementId, String libelle);
}