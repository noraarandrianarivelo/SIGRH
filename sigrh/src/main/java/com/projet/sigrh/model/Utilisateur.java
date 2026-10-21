package com.projet.sigrh.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "utilisateur")
public class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    // Relation ManyToOne avec RoleUser pour récupérer le nom du rôle (ex: "ROLE_RH")
    @ManyToOne(fetch = FetchType.EAGER)
@JoinColumn(name = "id_role")
    private RoleUser roleUser;

    @OneToOne
    @JoinColumn(name = "id_employee")
    private Employe employe;

    @Column(name = "date_creation")
    private LocalDateTime dateCreation = LocalDateTime.now();

    // Getters et Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public RoleUser getRoleUser() { return roleUser; }
    public void setRoleUser(RoleUser roleUser) { this.roleUser = roleUser; }

    public Employe getEmploye() { return employe; }
    public void setEmploye(Employe employe) { this.employe = employe; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }

    /**
     * Méthode essentielle pour que Spring Security et l'application 
     * récupèrent directement le texte du rôle ("ROLE_MANAGER", "ROLE_RH", etc.)
     */
public String getRole() {
    return roleUser != null ? roleUser.getRoleName() : "ROLE_USER";
}
}