package com.projet.sigrh.dto.personnel;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Aucun propriétaire, acteur, statut ou date de signature modifiable par mass assignment. */
public record ContratForm(Integer id, Integer precedent, Integer poste, String type,
        LocalDate debut, LocalDate fin, BigDecimal salaire, String horaires, String lieu, String conditions) { }
