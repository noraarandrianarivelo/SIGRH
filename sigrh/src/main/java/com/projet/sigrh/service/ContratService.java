package com.projet.sigrh.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projet.sigrh.exception.BusinessException;
import com.projet.sigrh.model.Contrat;
import com.projet.sigrh.repository.ContratRepository;
import com.projet.sigrh.security.Roles;
import com.projet.sigrh.model.Utilisateur;

@Service
public class ContratService {
    private final ContratRepository repository;
    private final PersonnelService personnel;

    public ContratService(ContratRepository repository, PersonnelService personnel) {
        this.repository = repository;
        this.personnel = personnel;
    }

    @Transactional(readOnly = true)
    public List<Contrat> lister() {
        return repository.findAll();
    }

    @Transactional
    public void signer(Integer contratId, Utilisateur utilisateur) {
        verifierDroit(utilisateur);
        personnel.signer(new PersonnelAccess.Acteur(utilisateur.getEmploye().getId(),utilisateur.getRole()),
                contratId, personnel.aujourdHui());
    }

    private void verifierDroit(Utilisateur utilisateur) {
        if (utilisateur == null
                || (!Roles.RH.equals(utilisateur.getRole()) && !Roles.DIRECTION.equals(utilisateur.getRole()))) {
            throw new BusinessException("Seuls la RH et la Direction peuvent signer un contrat.",
                    HttpStatus.FORBIDDEN);
        }
        if (utilisateur.getEmploye() == null) {
            throw new BusinessException("Le compte signataire n'est lié à aucun employé.", HttpStatus.FORBIDDEN);
        }
    }
}
