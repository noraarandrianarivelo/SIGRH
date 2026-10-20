package com.projet.sigrh.service;

import com.projet.sigrh.model.Departement;
import com.projet.sigrh.repository.DepartementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DepartementService {

    @Autowired
    private DepartementRepository departementRepository;

    public List<Departement> listerTous() {
        return departementRepository.findAll();
    }

    public Optional<Departement> trouverParId(Long id) {
        return departementRepository.findById(id);
    }

    public Departement enregistrer(Departement departement) {
        return departementRepository.save(departement);
    }

    public void supprimer(Long id) {
        departementRepository.deleteById(id);
    }
}