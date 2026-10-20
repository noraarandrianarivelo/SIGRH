## 1. Gestion RH

Destine au cycle de vie du salarie.
Aspects vus : 
- Recrutement
- Embauche
- Vie du salarie
- Evolution
- Manifestation du besion
- Rupture

### Objectifs
- Comprendre les processus RH
- Transformer un processus en workflow, gestion et interfaces
- Concevoir un SIRH

### Bloc 1 : ouverture de recrutement

Un manager veut creer un post ou remplacer un poste vacant.
Informations : poste , departement, effectif , motif , budget , date souhaitee
Workflow : Manager -> RH -> (Finance) ->Direction -> Ouverture du recrutement
Objets metier : departement , poste , DemandeRecrutement , Validation
Un poste existe independament de l'employe

### Bloc 2 : recrutement

La demande validee devient une offre d'emploi
Publier l'offre et recevoir les candidatures
Preselectionner et organiser les entretiens et les tests
Les tests peuvent avoir plusieurs niveaus (1 -> 10), on va faire (1 -> 3)
Conserver les evaluations et la decision finale
Objets : OffreEmploi, Candidat, Candidature, Entretien, EvaluationCandidat

#### NB : 
Le but de tout cela est la traceabilite de toute les informations. Surtout les historiques ou d'autres types de mouvement

### Bloc 3 : selection et embauche

Le candidat retenu recoit une proposition d'embauche
Apres acceptation (champ oui ou non/attendre du montant salaire , date et heure de travail, Cf contrat de travail), creation du dossier salarie et du matricule (sous forme pdf)
Creer le contrat, le poste et l'affectation
Point de conception: CANDIDAT != EMPLOYE
Une personne peut avoir plusieurs candidatures avant de devenir salarie

### Bloc 4 : contrat et onboarding

Un salarie peut avoir plusieurs contrats successifs : CDD , renouvellement, CDL
Essai : 6 mois renouvelable 1 fois max par la loi
CDD  sa CDL ???  2 ans renouvelable 1 fois
CDI : duree indeterminee
Ne pas ecraser l'historique en stockant seulement employe, type_contrat
Onboarding: contrat signe , badge(creation de matricule) , compte, materiel (voiture , ordi , secretaire), formation (misy formation requise ve ?)  , inscription paie
Utiliser une checklist et un workflow d integration
Objet : Contrat , Affection , Onboarding , Onboarding Tache, Document

### Bloc 5 : dossier administratif