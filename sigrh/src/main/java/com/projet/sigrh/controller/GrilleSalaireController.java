package com.projet.sigrh.controller;

import com.projet.sigrh.model.GrilleSalaire;
import com.projet.sigrh.service.GrilleSalaireService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/grilles-salaires")
@CrossOrigin(origins = "*")
public class GrilleSalaireController {

    @Autowired
    private GrilleSalaireService service;

    @GetMapping
    public ResponseEntity<List<GrilleSalaire>> listerToutes() {
        return ResponseEntity.ok(service.listerToutes());
    }

    @GetMapping("/poste/{idPoste}")
    public ResponseEntity<GrilleSalaire> trouverParPoste(@PathVariable Long idPoste) {
        return service.trouverParPoste(idPoste)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<GrilleSalaire> ajouter(@RequestBody GrilleSalaire grille) {
        return ResponseEntity.ok(service.enregistrer(grille));
    }
}