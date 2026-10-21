package com.projet.sigrh.service;

import com.projet.sigrh.model.DemandeRecrutement;
import com.projet.sigrh.model.Utilisateur;
import com.projet.sigrh.model.enums.*;
import com.projet.sigrh.security.Roles;

import java.util.List;

/**
 * Règles du circuit de validation, au même endroit et sans accès base :
 * Manager -> RH -> (Finance) -> Direction.
 */
public final class WorkflowDemande {

    private WorkflowDemande() {
    }

    /** Étape qui attend une décision pour ce statut, ou null si aucune. */
    public static EtapeValidation etapeCourante(StatutDemande statut) {
        return switch (statut) {
            case ATTENTE_RH -> EtapeValidation.RH;
            case ATTENTE_FINANCE -> EtapeValidation.FINANCE;
            case ATTENTE_DIRECTION -> EtapeValidation.DIRECTION;
            default -> null;
        };
    }

    public static String roleRequis(EtapeValidation etape) {
        return switch (etape) {
            case RH -> Roles.RH;
            case FINANCE -> Roles.FINANCE;
            case DIRECTION -> Roles.DIRECTION;
        };
    }

    /** Le rôle de l'étape, ou la Direction (qui peut tout traiter). */
    public static boolean peutDecider(DemandeRecrutement d, Utilisateur u) {
        EtapeValidation etape = etapeCourante(d.getStatut());
        if (etape == null) {
            return false;
        }
        return Roles.DIRECTION.equals(u.getRole()) || roleRequis(etape).equals(u.getRole());
    }

    /** RH : approuver, corriger, rejeter. Finance et Direction : approuver ou rejeter. */
    public static List<DecisionValidation> decisionsPossibles(EtapeValidation etape) {
        if (etape == EtapeValidation.RH) {
            return List.of(DecisionValidation.APPROUVEE, DecisionValidation.A_CORRIGER, DecisionValidation.REJETEE);
        }
        return List.of(DecisionValidation.APPROUVEE, DecisionValidation.REJETEE);
    }

    public static boolean peutModifier(DemandeRecrutement d, Utilisateur u) {
        return d.getStatut().isModifiable()
                && u.getEmploye() != null
                && d.getDemandeur().getId().equals(u.getEmploye().getId());
    }

    /** Première étape après la soumission. Créée par la Direction : on saute le Manager et la RH. */
    public static StatutDemande statutApresSoumission(DemandeRecrutement d) {
        if (d.getCircuit() == Circuit.DIRECTION_DIRECTE) {
            return d.isValidationFinance() ? StatutDemande.ATTENTE_FINANCE : StatutDemande.ATTENTE_DIRECTION;
        }
        return StatutDemande.ATTENTE_RH;
    }

    public static StatutDemande statutApresDecision(DemandeRecrutement d, EtapeValidation etape, DecisionValidation decision) {
        return switch (decision) {
            case REJETEE -> StatutDemande.REJETEE;
            case A_CORRIGER -> StatutDemande.A_CORRIGER;
            case APPROUVEE -> switch (etape) {
                case RH -> d.isValidationFinance() ? StatutDemande.ATTENTE_FINANCE : StatutDemande.ATTENTE_DIRECTION;
                case FINANCE -> StatutDemande.ATTENTE_DIRECTION;
                case DIRECTION -> StatutDemande.VALIDEE;
            };
        };
    }
}