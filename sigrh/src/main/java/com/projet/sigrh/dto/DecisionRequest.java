package com.projet.sigrh.dto;

import com.projet.sigrh.model.enums.DecisionValidation;

/** Décision d'un validateur (RH, Finance ou Direction). */
public record DecisionRequest(DecisionValidation decision, String commentaire) {
}