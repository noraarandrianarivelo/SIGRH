package com.projet.sigrh.dto.personnel;

import java.time.LocalDate;

/** La table de preuve est toujours déduite de l'étape par le serveur. */
public record RealisationForm(Integer existant, Integer catalogue, String numero, String systeme,
        String identifiant, LocalDate date, LocalDate priseEnCharge, boolean confirme) { }
