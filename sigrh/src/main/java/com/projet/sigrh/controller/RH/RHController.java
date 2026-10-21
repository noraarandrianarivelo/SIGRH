package com.projet.sigrh.controller.RH;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.server.ResponseStatusException;

import com.projet.sigrh.model.Candidature;
import com.projet.sigrh.service.CandidatureService;
import com.projet.sigrh.service.OffreEmploiService;
import com.projet.sigrh.service.SeanceEvaluationService;

@Controller
public class RHController {
    private final OffreEmploiService offreEmploiService;
    private final CandidatureService candidatureService;
    private final SeanceEvaluationService seanceEvaluationService;

    public RHController(OffreEmploiService offreEmploiService, CandidatureService candidatureService,
            SeanceEvaluationService seanceEvaluationService) {
        this.offreEmploiService = offreEmploiService;
        this.candidatureService = candidatureService;
        this.seanceEvaluationService = seanceEvaluationService;
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

    @GetMapping("/rh/evaluations")
    public String afficherEvaluations(Model model) {
        model.addAttribute("seances", seanceEvaluationService.findAll());
        return "RH/seance-evaluation-list";
    }

    @GetMapping("/rh/evaluations/{id}")
    public String afficherEvaluation(@PathVariable Integer id, Model model) {
        model.addAttribute("seance", seanceEvaluationService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Séance d'évaluation introuvable")));
        return "RH/seance-evaluation-detail";
    }

    @GetMapping("/rh/candidatures/{id}")
    public String afficherCandidature(@PathVariable Integer id, Model model) {
        Candidature candidature = trouverCandidature(id);
        model.addAttribute("candidature", candidature);
        model.addAttribute("cvAffichable", candidatureService.estAffichable(candidature.cv));
        model.addAttribute("lettreAffichable", candidatureService.estAffichable(candidature.lettreMotivation));
        return "RH/candidature-detail";
    }

    @GetMapping("/rh/candidatures/{id}/cv")
    public ResponseEntity<Resource> afficherCv(@PathVariable Integer id) throws IOException {
        Candidature candidature = trouverCandidature(id);
        return servirFichier(candidature.cv);
    }

    @GetMapping("/rh/candidatures/{id}/lettre-motivation")
    public ResponseEntity<Resource> afficherLettreMotivation(@PathVariable Integer id) throws IOException {
        Candidature candidature = trouverCandidature(id);
        return servirFichier(candidature.lettreMotivation);
    }

    @GetMapping("/rh/candidatures/{id}/evaluation")
    public String afficherFormulaireEvaluation(@PathVariable Integer id, Model model) {
        model.addAttribute("candidature", trouverCandidature(id));
        model.addAttribute("evaluateurs", seanceEvaluationService.listerEvaluateurs());
        model.addAttribute("tests", seanceEvaluationService.listerTests());
        return "RH/seance-evaluation-form";
    }

    @PostMapping("/rh/candidatures/{id}/evaluation")
    public String arrangerEvaluation(@PathVariable Integer id,
            @RequestParam String evaluateurId,
            @RequestParam(required = false) Integer testId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime dateEntretien,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime dateTest,
            @RequestParam(required = false) String lieuEntretien,
            @RequestParam(required = false) String lieuTest,
            @RequestParam String statut) {
        seanceEvaluationService.creer(trouverCandidature(id), evaluateurId, testId,
                dateEntretien, dateTest, lieuEntretien, lieuTest, statut);
        return "redirect:/rh/candidatures/" + id + "?evaluation=planifiee";
    }

    private Candidature trouverCandidature(Integer id) {
        return candidatureService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidature introuvable"));
    }

    private ResponseEntity<Resource> servirFichier(String nom) throws IOException {
        Path fichier = candidatureService.trouverFichier(nom)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichier introuvable"));
        MediaType type = mediaType(fichier);
        ContentDisposition disposition = candidatureService.estAffichable(nom)
                ? ContentDisposition.inline().filename(nom).build()
                : ContentDisposition.attachment().filename(nom).build();

        return ResponseEntity.ok()
                .contentType(type)
                .contentLength(Files.size(fichier))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(fichier));
    }

    private MediaType mediaType(Path fichier) throws IOException {
        String type = Files.probeContentType(fichier);
        return type == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(type);
    }
}
