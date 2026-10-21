package com.projet.sigrh.controller.Candidat;

import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.projet.sigrh.model.Candidat;
import com.projet.sigrh.model.OffreEmploi;
import com.projet.sigrh.service.CandidatureService;
import com.projet.sigrh.service.OffreEmploiService;
import com.projet.sigrh.service.PropositionEmbaucheService;

import jakarta.servlet.http.HttpSession;

@Controller
public class CandidatController {
    private final OffreEmploiService offreEmploiService;
    private final CandidatureService candidatureService;
    private final PropositionEmbaucheService propositionService;

    public CandidatController(OffreEmploiService offreEmploiService, CandidatureService candidatureService) {
        this(offreEmploiService, candidatureService, null);
    }

    @Autowired
    public CandidatController(OffreEmploiService offreEmploiService, CandidatureService candidatureService,
            PropositionEmbaucheService propositionService) {
        this.offreEmploiService = offreEmploiService;
        this.candidatureService = candidatureService;
        this.propositionService = propositionService;
    }

    @GetMapping("/candidat/offre-emploi")
    public String afficherOffres(Model model) {
        model.addAttribute("offres", offreEmploiService.findByStatut("OUVERT"));
        return "Candidat/offre-list";
    }

    @GetMapping("/candidat/propositions")
    public String afficherPropositions(Model model, HttpSession session) {
        if (propositionService == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Le service de proposition n'est pas configuré");
        }
        model.addAttribute("propositions", propositionService.propositionsDuCandidat(candidatConnecte(session)));
        return "Candidat/proposition-list";
    }

    @PostMapping("/candidat/propositions/{id}/reponse")
    public String repondreProposition(@PathVariable Integer id,
            @RequestParam String statut,
            @RequestParam(required = false) String message,
            HttpSession session) {
        if (propositionService == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Le service de proposition n'est pas configuré");
        }
        propositionService.repondre(id, candidatConnecte(session), statut, message);
        return "redirect:/candidat/propositions?reponse=enregistree";
    }

    @GetMapping("/candidat/offre-emploi/{id}")
    public String afficherOffre(@PathVariable Integer id, Model model) {
        model.addAttribute("offre", offreEmploiService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offre d'emploi introuvable")));
        return "Candidat/offre-detail";
    }

    @GetMapping("/candidat/offre-emploi/{id}/candidature")
    public String afficherFormulaireCandidature(@PathVariable Integer id, Model model, HttpSession session) {
        candidatConnecte(session);
        model.addAttribute("offre", trouverOffre(id));
        return "Candidat/candidature-form";
    }

    @PostMapping("/candidat/offre-emploi/{id}/candidature")
    public String envoyerCandidature(@PathVariable Integer id,
            @RequestParam("cv") MultipartFile cv,
            @RequestParam(value = "lettreEncouragement", required = false) MultipartFile lettreEncouragement,
            @RequestParam(value = "message", required = false) String message,
            HttpSession session) {
        Candidat candidat = candidatConnecte(session);
        OffreEmploi offre = trouverOffre(id);
        candidatureService.creer(candidat, offre, cv, lettreEncouragement, message);
        return "redirect:/candidat/offre-emploi/" + id + "?candidature=envoyee";
    }

    private OffreEmploi trouverOffre(Integer id) {
        return offreEmploiService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offre d'emploi introuvable"));
    }

    private Candidat candidatConnecte(HttpSession session) {
        Object candidat = session.getAttribute("candidatConnecte");
        if (candidat instanceof Candidat candidatConnecte) {
            return candidatConnecte;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Candidat non connecté");
    }
}
