package com.projet.sigrh.repository;

import java.util.List;

import com.projet.sigrh.model.Employe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface EmployeRepository extends JpaRepository<Employe, String> {

    interface EvaluateurOption {
        String getId();
        String getNomComplet();
        String getPoste();
    }

    @Query(value = """
            SELECT e.id,
                   concat_ws(' ', e.prenom, e.nom) AS "nomComplet",
                   COALESCE(p.libelle, 'Poste non renseigné') AS poste
            FROM employe e
            LEFT JOIN affectation a ON a.id_employe = e.id AND a.date_fin IS NULL
            LEFT JOIN poste p ON p.id = a.id_poste
            ORDER BY e.nom, e.prenom
            """, nativeQuery = true)
    List<EvaluateurOption> findEvaluateursAvecPoste();
}
