package com.projet.sigrh.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.projet.sigrh.model.Candidature;
import com.projet.sigrh.repository.CandidatureRepository;

@Service
public class CandidatureService {
    private final CandidatureRepository repository;

    public CandidatureService(CandidatureRepository repository) {
        this.repository = repository;
    }

    public List<Candidature> findByOffreId(Integer offreId) {
        return repository.findByOffreId(offreId);
    }

    public Optional<Candidature> findById(Integer id) {
        return repository.findById(id);
    }
}
