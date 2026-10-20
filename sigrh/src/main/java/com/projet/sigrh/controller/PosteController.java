package com.projet.sigrh.controller;

import com.projet.sigrh.model.Poste;
import com.projet.sigrh.service.PosteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/postes")
@CrossOrigin(origins = "*")
public class PosteController {

    @Autowired
    private PosteService service;

    @GetMapping
    public ResponseEntity<List<Poste>> listerTous() {
        return ResponseEntity.ok(service.listerTous());
    }

    @GetMapping("/departement/{idDepartement}")
    public ResponseEntity<List<Poste>> listerParDepartement(@PathVariable Long idDepartement) {
        return ResponseEntity.ok(service.trouverParDepartement(idDepartement));
    }

    @PostMapping
    public ResponseEntity<Poste> ajouter(@RequestBody Poste poste) {
        return ResponseEntity.ok(service.enregistrer(poste));
    }
}