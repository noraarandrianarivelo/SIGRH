package com.projet.sigrh.controller.RH;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import com.projet.sigrh.service.CandidatureService;
import com.projet.sigrh.service.OffreEmploiService;

@Controller
public class RHController {
    private final OffreEmploiService offreEmploiService;
    private final CandidatureService candidatureService;

    public RHController(OffreEmploiService offreEmploiService, CandidatureService candidatureService) {
        this.offreEmploiService = offreEmploiService;
        this.candidatureService = candidatureService;
    }

    @GetMapping("/rh/offres")
    public String afficherOffres(Model model) {
        model.addAttribute("offres", offreEmploiService.findByStatut("OUVERT"));
        return "RH/offre-list";
    }

    @GetMapping("/rh/offres/{id}")
    public String afficherOffre(@PathVariable Integer id, Model model) {
        model.addAttribute("offre", offreEmploiService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offre d'emploi introuvable")));
        model.addAttribute("candidatures", candidatureService.findByOffreId(id));
        return "RH/offre-detail";
    }
}
