package com.projet.sigrh.controller;

import com.projet.sigrh.model.Departement;
import com.projet.sigrh.service.DepartementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/departements")
@CrossOrigin(origins = "*")
public class DepartementController {

    @Autowired
    private DepartementService service;

    @GetMapping
    public ResponseEntity<List<Departement>> listerTous() {
        return ResponseEntity.ok(service.listerTous());
    }

    @PostMapping
    public ResponseEntity<Departement> ajouter(@RequestBody Departement departement) {
        return ResponseEntity.ok(service.enregistrer(departement));
    }
}