package com.projet.sigrh.repository;

import com.projet.sigrh.model.Poste;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PosteRepository extends JpaRepository<Poste, Long> {
    List<Poste> findByDepartementId(Long departementId);
}