package com.projet.sigrh.model;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "grille_salaire")
public class GrilleSalaire {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "id_poste", nullable = false, unique = true)
    private Poste poste;

    @Column(name = "salaire_min", nullable = false, precision = 14, scale = 2)
    private BigDecimal salaireMin;

    @Column(name = "salaire_max", nullable = false, precision = 14, scale = 2)
    private BigDecimal salaireMax;

    // Getters et Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Poste getPoste() { return poste; }
    public void setPoste(Poste poste) { this.poste = poste; }
    public BigDecimal getSalaireMin() { return salaireMin; }
    public void setSalaireMin(BigDecimal salaireMin) { this.salaireMin = salaireMin; }
    public BigDecimal getSalaireMax() { return salaireMax; }
    public void setSalaireMax(BigDecimal salaireMax) { this.salaireMax = salaireMax; }
}