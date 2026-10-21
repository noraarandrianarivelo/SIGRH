package com.projet.sigrh.repository;

import com.projet.sigrh.model.DemandeRecrutement;
import com.projet.sigrh.model.enums.StatutDemande;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DemandeRecrutementRepository extends JpaRepository<DemandeRecrutement, Long> {

    // Vue du manager : ses propres demandes.
    List<DemandeRecrutement> findByDemandeurIdOrderByIdDesc(String idDemandeur);

    // Vue RH / Finance / Direction : tout sauf les brouillons des autres.
    @Query("select d from DemandeRecrutement d "
         + "where d.statut <> :brouillon or d.demandeur.id = :idEmploye "
         + "order by d.id desc")
    List<DemandeRecrutement> findVisibles(@Param("brouillon") StatutDemande brouillon,
                                          @Param("idEmploye") String idEmploye);
}