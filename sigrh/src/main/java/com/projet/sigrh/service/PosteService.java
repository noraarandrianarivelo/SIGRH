package com.projet.sigrh.service;

import com.projet.sigrh.model.Poste;
import com.projet.sigrh.repository.PosteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PosteService {

    @Autowired
    private PosteRepository posteRepository;

    public List<Poste> listerTous() {
        return posteRepository.findAll();
    }

    public Optional<Poste> trouverParId(Long id) {
        return posteRepository.findById(id);
    }

    public List<Poste> trouverParDepartement(Long idDepartement) {
        return posteRepository.findByDepartementId(idDepartement);
    }

    public Poste enregistrer(Poste poste) {
        return posteRepository.save(poste);
    }

    public void supprimer(Long id) {
        posteRepository.deleteById(id);
    }
}