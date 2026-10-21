package com.projet.sigrh.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.projet.sigrh.model.OffreEmploi;
import com.projet.sigrh.repository.OffreEmploiRepository;

@Service
public class OffreEmploiService {
    private final OffreEmploiRepository repository;

    public OffreEmploiService(OffreEmploiRepository repository) {
        this.repository = repository;
    }

    public List<OffreEmploi> findAll() {
        return repository.findAll();
    }

    public List<OffreEmploi> findByStatut(String statut) {
        return repository.findByStatut(statut);
    }

    public Optional<OffreEmploi> findById(Integer id) {
        return repository.findById(id);
    }
}
