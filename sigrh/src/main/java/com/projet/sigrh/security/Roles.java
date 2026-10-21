package com.projet.sigrh.security;

/** Valeurs de role_user.role_name (utilisées aussi comme authorities Spring Security). */
public final class Roles {

    public static final String MANAGER = "ROLE_MANAGER";
    public static final String RH = "ROLE_RH";
    public static final String FINANCE = "ROLE_FINANCE";
    public static final String DIRECTION = "ROLE_DIRECTION";
    public static final String CANDIDAT = "CANDIDAT";

    private Roles() {
    }
}
