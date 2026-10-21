package com.projet.sigrh.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.projet.sigrh.model.DemandeRecrutement;
import com.projet.sigrh.model.enums.StatutDemande;

@Repository
public interface DemandeRecrutementRepository extends JpaRepository<DemandeRecrutement, Long> {

    // Vue du manager : ses propres demandes.
    List<DemandeRecrutement> findByDemandeurIdOrderByIdDesc(String idDemandeur);

    @Query("select d from DemandeRecrutement d where d.besoin.id = :besoinId")
    Optional<DemandeRecrutement> findByBesoinId(@Param("besoinId") Long besoinId);

    // Vue RH / Finance / Direction : tout sauf les brouillons des autres.
    @Query("select d from DemandeRecrutement d "
         + "where d.statut <> :brouillon or d.demandeur.id = :idEmploye "
         + "order by d.id desc")
    List<DemandeRecrutement> findVisibles(@Param("brouillon") StatutDemande brouillon,
                                          @Param("idEmploye") String idEmploye);
}