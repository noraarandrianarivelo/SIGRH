package com.projet.sigrh.dto;

import com.projet.sigrh.model.enums.TypeMotif;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Corps JSON pour créer ou modifier une demande (un besoin avec N lignes de postes). */
public record DemandeRequest(
        Long departementId,
        TypeMotif typeMotif,
        String motif,
        LocalDate dateArriveeSouhaitee,
        List<LigneRequest> lignes) {

    /** Une ligne = un poste (existant OU proposé), son effectif et son budget unitaire. */
    public record LigneRequest(
            Long posteId,
            String libellePostePropose,
            Integer effectif,
            BigDecimal budgetMensuelUnitaire) {
    }
}