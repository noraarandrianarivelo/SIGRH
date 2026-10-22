package com.projet.sigrh.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PropositionEmbaucheRequest(
        BigDecimal salaireMensuel,
        LocalDate dateDebutPrevue,
        String typeContrat,
        String horaires,
        String lieuTravail,
        String autresConditions) {
}
