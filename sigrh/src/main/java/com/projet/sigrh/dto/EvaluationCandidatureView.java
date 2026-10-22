package com.projet.sigrh.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.projet.sigrh.model.Candidature;

public record EvaluationCandidatureView(Candidature candidature, List<NoteView> notes) {
    public record NoteView(LocalDateTime dateEntretien, LocalDateTime dateTest,
            BigDecimal noteEntretien, BigDecimal noteTest, String avis, String commentaire) {
    }
}
