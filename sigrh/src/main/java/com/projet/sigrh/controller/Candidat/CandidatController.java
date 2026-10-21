package com.projet.sigrh.controller.Candidat;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import com.projet.sigrh.service.OffreEmploiService;

@Controller
public class CandidatController {
    private final OffreEmploiService offreEmploiService;

    public CandidatController(OffreEmploiService offreEmploiService) {
        this.offreEmploiService = offreEmploiService;
    }

    @GetMapping("/candidat/offre-emploi")
    public String afficherOffres(Model model) {
        model.addAttribute("offres", offreEmploiService.findByStatut("OUVERT"));
        return "Candidat/offre-list";
    }

    @GetMapping("/candidat/offre-emploi/{id}")
    public String afficherOffre(@PathVariable Integer id, Model model) {
        model.addAttribute("offre", offreEmploiService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offre d'emploi introuvable")));
        return "Candidat/offre-detail";
    }
}
