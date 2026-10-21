package com.projet.sigrh.service;

import com.projet.sigrh.model.Employe;
import com.projet.sigrh.repository.EmployeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class EmployeService {

    @Autowired
    private EmployeRepository employeRepository;

    public List<Employe> listerTous() {
        return employeRepository.findAll();
    }

    public Optional<Employe> trouverParId(String id) {
        return employeRepository.findById(id);
    }

    public Employe enregistrer(Employe employe) {
        return employeRepository.save(employe);
    }

    public void supprimer(String id) {
        employeRepository.deleteById(id);
    }
}