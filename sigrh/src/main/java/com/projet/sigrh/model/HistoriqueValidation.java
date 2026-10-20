package com.projet.sigrh.model;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "historique_validation")
public class HistoriqueValidation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_demande", nullable = false)
    private Long idDemande;

    @ManyToOne
    @JoinColumn(name = "id_validateur", nullable = false)
    private Employe validateur;

    @Column(nullable = false, length = 20)
    private String etape; // RH, FINANCE, DIRECTION

    @Column(nullable = false, length = 20)
    private String decision; // APPROUVEE, REJETEE, A_CORRIGER

    @Column(name = "date_validation", nullable = false)
    private LocalDateTime dateValidation = LocalDateTime.now();

    @Column(columnDefinition = "TEXT")
    private String commentaire;

    // Getters et Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getIdDemande() { return idDemande; }
    public void setIdDemande(Long idDemande) { this.idDemande = idDemande; }
    public Employe getValidateur() { return validateur; }
    public void setValidateur(Employe validateur) { this.validateur = validateur; }
    public String getEtape() { return etape; }
    public void setEtape(String etape) { this.etape = etape; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public LocalDateTime getDateValidation() { return dateValidation; }
    public void setDateValidation(LocalDateTime dateValidation) { this.dateValidation = dateValidation; }
    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }
}