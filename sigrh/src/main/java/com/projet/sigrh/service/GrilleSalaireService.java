package com.projet.sigrh.service;

import com.projet.sigrh.model.GrilleSalaire;
import com.projet.sigrh.repository.GrilleSalaireRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class GrilleSalaireService {

    @Autowired
    private GrilleSalaireRepository grilleSalaireRepository;

    public List<GrilleSalaire> listerToutes() {
        return grilleSalaireRepository.findAll();
    }

    public Optional<GrilleSalaire> trouverParPoste(Long idPoste) {
        return grilleSalaireRepository.findByPosteId(idPoste);
    }

    public GrilleSalaire enregistrer(GrilleSalaire grilleSalaire) {
        return grilleSalaireRepository.save(grilleSalaire);
    }
}