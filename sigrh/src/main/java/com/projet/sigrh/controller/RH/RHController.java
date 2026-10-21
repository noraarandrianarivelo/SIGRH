package com.projet.sigrh.controller.RH;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.security.Principal;

import org.springframework.core.io.FileSystemResource;
import org.springframework.beans.factory.annotation.Autowired;
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
import com.projet.sigrh.model.SeanceEvaluation;
import com.projet.sigrh.service.CandidatureService;
import com.projet.sigrh.service.DecisionCandidatureService;
import com.projet.sigrh.service.OffreEmploiService;
import com.projet.sigrh.service.SeanceEvaluationService;
import com.projet.sigrh.service.UtilisateurService;
import com.projet.sigrh.dto.DecisionCandidatureRequest;
import com.projet.sigrh.dto.PropositionEmbaucheRequest;
import com.projet.sigrh.service.PropositionEmbaucheService;

@Controller
public class RHController {
    private final OffreEmploiService offreEmploiService;
    private final CandidatureService candidatureService;
    private final SeanceEvaluationService seanceEvaluationService;
    private final DecisionCandidatureService decisionCandidatureService;
    private final UtilisateurService utilisateurService;
    private final PropositionEmbaucheService propositionService;

    public RHController(OffreEmploiService offreEmploiService, CandidatureService candidatureService,
            SeanceEvaluationService seanceEvaluationService) {
        this(offreEmploiService, candidatureService, seanceEvaluationService, null, null);
    }

    public RHController(OffreEmploiService offreEmploiService, CandidatureService candidatureService,
            SeanceEvaluationService seanceEvaluationService, DecisionCandidatureService decisionCandidatureService,
            UtilisateurService utilisateurService) {
        this(offreEmploiService, candidatureService, seanceEvaluationService,
                decisionCandidatureService, utilisateurService, null);
    }

    @Autowired
    public RHController(OffreEmploiService offreEmploiService, CandidatureService candidatureService,
            SeanceEvaluationService seanceEvaluationService, DecisionCandidatureService decisionCandidatureService,
            UtilisateurService utilisateurService, PropositionEmbaucheService propositionService) {
        this.offreEmploiService = offreEmploiService;
        this.candidatureService = candidatureService;
        this.seanceEvaluationService = seanceEvaluationService;
        this.decisionCandidatureService = decisionCandidatureService;
        this.utilisateurService = utilisateurService;
        this.propositionService = propositionService;
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
        if (decisionCandidatureService != null) {
            model.addAttribute("comparaison", decisionCandidatureService.comparer(id));
        }
        return "RH/offre-detail";
    }

    @PostMapping("/rh/candidatures/{id}/decision")
    public String deciderCandidature(@PathVariable Integer id, @RequestParam String decision,
            @RequestParam String motif, Principal principal) {
        if (decisionCandidatureService == null || utilisateurService == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Le service de décision des candidatures n'est pas configuré");
        }
        decisionCandidatureService.decider(id, new DecisionCandidatureRequest(decision, motif),
                utilisateurService.getConnecte(principal));
        return "redirect:/rh/offres/" + trouverCandidature(id).offre.id + "?decision=enregistree";
    }

    @GetMapping("/rh/evaluations")
    public String afficherEvaluations(Model model) {
        model.addAttribute("seances", seanceEvaluationService.findAll());
        return "RH/seance-evaluation-list";
    }

    @GetMapping("/rh/evaluations/{id}")
    public String afficherEvaluation(@PathVariable Integer id, Model model) {
        model.addAttribute("seance", trouverSeance(id));
        model.addAttribute("dejaEvaluee", seanceEvaluationService.estEvaluee(id));
        return "RH/seance-evaluation-detail";
    }

    @GetMapping("/rh/evaluations/{id}/evaluer")
    public String afficherFormulaireNotation(@PathVariable Integer id, Model model) {
        if (seanceEvaluationService.estEvaluee(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette séance a déjà été évaluée");
        }
        model.addAttribute("seance", trouverSeance(id));
        return "RH/fiche-note-evaluation-form";
    }

    @PostMapping("/rh/evaluations/{id}/evaluer")
    public String evaluer(@PathVariable Integer id,
            @RequestParam(required = false) BigDecimal noteEntretien,
            @RequestParam(required = false) BigDecimal noteTest,
            @RequestParam(required = false) String commentaire,
            @RequestParam String avis) {
        seanceEvaluationService.evaluer(id, noteEntretien, noteTest, commentaire, avis);
        return "redirect:/rh/evaluations/" + id + "?note=enregistree";
    }

    @GetMapping("/rh/candidatures/{id}")
    public String afficherCandidature(@PathVariable Integer id, Model model) {
        Candidature candidature = trouverCandidature(id);
        model.addAttribute("candidature", candidature);
        model.addAttribute("cvAffichable", candidatureService.estAffichable(candidature.cv));
        model.addAttribute("lettreAffichable", candidatureService.estAffichable(candidature.lettreMotivation));
        if (propositionService != null) {
            model.addAttribute("propositionActive", propositionService.propositionActive(candidature.id).orElse(null));
        }
        return "RH/candidature-detail";
    }

    @GetMapping("/rh/candidatures/{id}/proposition")
    public String afficherFormulaireProposition(@PathVariable Integer id, Model model) {
        Candidature candidature = trouverCandidature(id);
        if (propositionService == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Le service de proposition n'est pas configuré");
        }
        if (!"RETENUE".equals(candidature.statut)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Une proposition ne peut être préparée que pour un candidat retenu");
        }
        if (propositionService.propositionActive(id).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Une proposition est déjà en attente ou acceptée");
        }
        model.addAttribute("candidature", candidature);
        model.addAttribute("salaireInitial", propositionService.salaireInitial(candidature));
        model.addAttribute("dateDebutInitiale", propositionService.dateDebutInitiale(candidature));
        model.addAttribute("typeContratInitial", "CE");
        return "RH/proposition-embauche-form";
    }

    @PostMapping("/rh/candidatures/{id}/proposition")
    public String envoyerProposition(@PathVariable Integer id,
            @RequestParam BigDecimal salaireMensuel,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate dateDebutPrevue,
            @RequestParam String typeContrat,
            @RequestParam(required = false) String horaires,
            @RequestParam(required = false) String lieuTravail,
            @RequestParam(required = false) String autresConditions,
            Principal principal) {
        if (propositionService == null || utilisateurService == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Le service de proposition n'est pas configuré");
        }
        Candidature candidature = trouverCandidature(id);
        propositionService.envoyer(candidature,
                new PropositionEmbaucheRequest(salaireMensuel, dateDebutPrevue, typeContrat,
                        horaires, lieuTravail, autresConditions),
                utilisateurService.getConnecte(principal));
        return "redirect:/rh/candidatures/" + id + "?proposition=envoyee";
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

    private SeanceEvaluation trouverSeance(Integer id) {
        return seanceEvaluationService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Séance d'évaluation introuvable"));
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
