package com.projet.sigrh.model.enums;

public enum StatutDemande {
    BROUILLON, ATTENTE_RH, ATTENTE_FINANCE, ATTENTE_DIRECTION, A_CORRIGER, VALIDEE, REJETEE;

    /** Le manager peut modifier et (re)soumettre seulement dans ces deux statuts. */
    public boolean isModifiable() {
        return this == BROUILLON || this == A_CORRIGER;
    }
}