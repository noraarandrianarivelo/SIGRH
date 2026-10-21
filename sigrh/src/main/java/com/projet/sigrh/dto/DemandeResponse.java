package com.projet.sigrh.dto;

import com.projet.sigrh.model.enums.Circuit;
import com.projet.sigrh.model.enums.StatutDemande;
import com.projet.sigrh.model.enums.TypeMotif;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Ce que le navigateur reçoit : la demande + ce que l'utilisateur connecté a le droit d'en faire. */
public record DemandeResponse(
        Long id,
        String demandeur,
        Long departementId,
        String departement,
        TypeMotif typeMotif,
        String motif,
        LocalDate dateCreation,
        LocalDate dateArriveeSouhaitee,
        Circuit circuit,
        boolean validationFinance,
        StatutDemande statut,
        List<Ligne> lignes,
        List<Historique> historique,
        boolean peutModifier,
        List<String> decisionsPossibles) {

    public record Ligne(
            Long id,
            Long posteId,
            String poste,
            boolean posteACreer,
            Integer effectif,
            BigDecimal budgetMensuelUnitaire,
            BigDecimal grilleMin,
            BigDecimal grilleMax,
            boolean conforme) {
    }

    public record Historique(
            String etape,
            String decision,
            String validateur,
            LocalDateTime date,
            String commentaire) {
    }
}