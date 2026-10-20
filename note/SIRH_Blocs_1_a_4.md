# SIRH — Blocs 1 à 4

## Vue générale

Le SIRH (Système d'Information des Ressources Humaines) doit suivre le cycle de vie du personnel depuis l'expression d'un besoin jusqu'à l'intégration du salarié.

```text
Besoin en personnel
        ↓
Demande de recrutement
        ↓
Validation
        ↓
Ouverture du recrutement
        ↓
Offre d'emploi
        ↓
Candidatures
        ↓
Sélection
        ↓
Proposition d'embauche
        ↓
Acceptation
        ↓
Création du salarié
        ↓
Contrat
        ↓
Affectation
        ↓
Onboarding
```

> **Principe transversal : la traçabilité.**
>
> Le système doit conserver l'historique des actions, décisions, validations, changements de contrat, affectations et autres mouvements importants. Il ne faut pas simplement conserver l'état actuel : il faut pouvoir retrouver ce qui s'est passé auparavant.

---

# Bloc 1 — Expression du besoin / Ouverture du recrutement

## 1.1. Objectif

Le premier bloc correspond à la **manifestation d'un besoin en personnel**.

Un manager, chef de service ou responsable constate qu'il a besoin d'une ou plusieurs personnes pour réaliser une tâche ou pour remplacer un salarié.

### Exemples

- Le département Logistique a besoin d'un chauffeur.
- Le département Finance a besoin d'un contrôleur financier.
- Un salarié quitte son poste et doit être remplacé.
- L'entreprise crée un nouveau poste.

Le besoin peut donc correspondre à :

- une **création de poste** ;
- un **remplacement d'un poste vacant**.

---

## 1.2. Informations de la demande

Le manager doit renseigner les informations nécessaires pour justifier le recrutement.

| Information | Exemple |
|---|---|
| Poste | Chauffeur |
| Département | Logistique |
| Service / Section | Transport |
| Effectif demandé | 1 |
| Motif | Remplacement d'un salarié |
| Budget | 1 200 000 Ar / mois |
| Date souhaitée | 01/12/2026 |

D'autres informations peuvent être ajoutées selon les besoins de l'entreprise :

- compétences recherchées ;
- niveau du poste ;
- type de contrat souhaité ;
- expérience minimale ;
- justification du besoin.

---

## 1.3. Point de conception : le poste est indépendant du salarié

Un point important du cours est :

```text
POSTE != EMPLOYE
```

Un poste existe indépendamment de la personne qui l'occupe.

Par exemple :

```text
Poste : Chauffeur
Département : Logistique

        ↓

Jean occupe le poste

        ↓

Jean quitte l'entreprise

        ↓

Marie occupe le même poste
```

Le poste continue donc d'exister même si le salarié change.

Il faut éviter de considérer le poste comme une simple information figée dans l'employé.

On pourra par exemple avoir :

```text
Poste
-----
id
libelle
departement
...

Employe
-------
id
nom
...

Affectation
-----------
id
employe
poste
dateDebut
dateFin
...
```

L'objet `Affectation` permettra notamment de conserver l'historique des personnes ayant occupé un poste.

---

## 1.4. Demande de recrutement

La demande peut être représentée par un objet métier :

```text
DemandeRecrutement
------------------
id
poste
departement
service
effectif
motif
budget
dateSouhaitee
demandeur
dateCreation
statut
```

Exemple :

```text
Demande #25

Demandeur : Manager Logistique
Poste : Chauffeur
Département : Logistique
Effectif : 1
Motif : Remplacement
Budget : 1 200 000 Ar
Date souhaitée : 01/12/2026
Statut : EN_ATTENTE_RH
```

---

## 1.5. Workflow de validation

La demande ne devient pas automatiquement un recrutement.

Elle doit passer par un processus de validation.

Exemple :

```text
Manager
   ↓
RH
   ↓
Finance
   ↓
Direction
   ↓
Ouverture du recrutement
```

Selon l'organisation de l'entreprise, Finance peut être impliquée ou non :

```text
Manager → RH → Direction → Ouverture
```

ou :

```text
Manager → RH → Finance → Direction → Ouverture
```

Le logiciel doit idéalement permettre d'adapter le workflow à l'organisation.

---

## 1.6. Statuts possibles

Une demande peut avoir plusieurs états :

```text
BROUILLON
EN_ATTENTE_RH
EN_ATTENTE_FINANCE
EN_ATTENTE_DIRECTION
VALIDEE
REFUSEE
ANNULEE
```

Exemple :

```text
Demande créée
      ↓
En attente RH
      ↓
RH validée
      ↓
En attente Finance
      ↓
Finance validée
      ↓
En attente Direction
      ↓
Direction validée
      ↓
Recrutement ouvert
```

---

## 1.7. Traçabilité des validations

Il ne faut pas seulement enregistrer :

```text
statut = VALIDEE
```

Il faut également conserver qui a validé, quand et éventuellement avec quel commentaire.

Exemple :

```text
Demande #25

RH
- Validée par : Responsable RH
- Date : 11/10/2026

Finance
- Validée par : Responsable Finance
- Date : 12/10/2026

Direction
- Validée par : Directeur
- Date : 13/10/2026
```

C'est une première application du principe de **traçabilité**.

---

# Bloc 2 — Recrutement

## 2.1. Objectif

Une fois la demande de recrutement validée, l'entreprise peut lancer réellement le recrutement.

La demande validée permet de créer une **offre d'emploi**.

```text
DemandeRecrutement validée
          ↓
     OffreEmploi
```

---

## 2.2. Offre d'emploi

L'offre d'emploi décrit le poste proposé aux candidats.

Elle peut contenir :

```text
OffreEmploi
-----------
id
titre
description
poste
departement
nombreDePlaces
profilRecherche
competences
experience
typeContrat
datePublication
dateCloture
statut
```

Exemple :

```text
OFFRE D'EMPLOI

Poste : Chauffeur
Département : Logistique
Nombre de places : 1

Profil :
- Permis adapté
- Expérience en conduite
- Bonne connaissance de la ville

Date de publication : 15/10/2026
Date de clôture : 30/10/2026
```

---

## 2.3. Candidats et candidatures

Plusieurs personnes peuvent répondre à la même offre.

```text
Offre : Chauffeur
      │
      ├── Candidat A
      ├── Candidat B
      ├── Candidat C
      ├── Candidat D
      └── Candidat E
```

Il faut distinguer :

```text
CANDIDAT != CANDIDATURE
```

### Candidat

Le candidat représente la personne.

```text
Candidat
--------
id
nom
prenom
email
telephone
...
```

### Candidature

La candidature représente la participation d'un candidat à une offre précise.

```text
Candidature
-----------
id
candidat
offreEmploi
dateCandidature
statut
...
```

Une même personne peut avoir plusieurs candidatures :

```text
Rakoto
  │
  ├── Candidature → Chauffeur
  ├── Candidature → Comptable
  └── Candidature → Responsable transport
```

Cela permet de conserver tout l'historique de recrutement d'une personne.

---

## 2.4. Préselection

Les RH examinent les candidatures reçues et sélectionnent les profils correspondant aux critères du poste.

Exemple :

```text
100 candidatures
        ↓
40 présélectionnées
        ↓
20 entretiens
        ↓
10 candidats testés
        ↓
3 finalistes
        ↓
1 candidat retenu
```

Chaque étape doit être traçable.

---

## 2.5. Entretiens

Un candidat peut passer plusieurs entretiens.

Exemple :

```text
Candidat : Rakoto

Entretien 1
→ RH
→ 20/10/2026
→ 14h00

Entretien 2
→ Manager Logistique
→ 22/10/2026
→ 10h00

Entretien 3
→ Direction
→ 25/10/2026
→ 15h00
```

Objet métier :

```text
Entretien
---------
id
candidature
type
date
heure
intervenant
commentaire
statut
```

---

## 2.6. Tests et niveaux de sélection

Le cours prévoit plusieurs niveaux d'évaluation.

Pour éviter un système trop complexe, on peut limiter le processus à quelques niveaux.

Exemple :

```text
Niveau 0 : Test écrit
Niveau 1 : Entretien RH
Niveau 2 : Entretien Manager
Niveau 3 : Entretien Direction
```

Le processus devient alors :

```text
Candidat
   ↓
Test écrit
   ↓
RH
   ↓
Manager
   ↓
Direction
   ↓
Décision finale
```

---

## 2.7. Évaluation du candidat

Chaque étape peut produire une évaluation.

Objet :

```text
EvaluationCandidat
------------------
id
candidature
typeEvaluation
niveau
evaluateur
note
commentaire
date
decision
```

Exemple :

```text
Test écrit
Note : 15/20
Décision : VALIDE

Entretien RH
Note : 4/5
Décision : VALIDE

Entretien Manager
Note : 4,5/5
Décision : VALIDE

Direction
Décision : RETENU
```

Il faut conserver les évaluations, et pas seulement le résultat final.

---

## 2.8. Décision finale

Après les entretiens et les tests :

```text
Candidat A → REFUSE
Candidat B → REFUSE
Candidat C → RETENU
```

Le candidat retenu passe au bloc suivant.

Les autres candidatures restent également dans l'historique.

---

# Bloc 3 — Sélection et embauche

## 3.1. Objectif

Le candidat retenu reçoit une **proposition d'embauche**.

Attention :

```text
Candidat retenu != Employé
```

Le candidat devient salarié seulement après l'acceptation et la réalisation des étapes nécessaires à l'embauche.

---

## 3.2. Proposition d'embauche

La proposition contient les principales conditions proposées au candidat.

Exemple :

```text
Poste : Chauffeur
Département : Logistique
Salaire : 1 200 000 Ar
Date prévue de début : 01/12/2026
Horaire : 08h00 - 17h00
Lieu de travail : Antananarivo
Type de contrat : CDD
```

D'autres informations peuvent être définies selon le modèle de l'entreprise et le contrat :

- rémunération ;
- horaires ;
- lieu de travail ;
- date de début ;
- poste ;
- type de contrat ;
- avantages éventuels ;
- autres conditions.

---

## 3.3. Réponse du candidat

Le candidat peut avoir plusieurs états :

```text
EN_ATTENTE
ACCEPTEE
REFUSEE
```

Exemple :

```text
Proposition envoyée
        ↓
En attente de réponse
        ↓
Acceptée
```

ou :

```text
Proposition envoyée
        ↓
Refusée
```

---

## 3.4. Création du dossier salarié

Une fois la proposition acceptée, l'entreprise peut créer le dossier salarié.

```text
Candidat
   ↓
Proposition acceptée
   ↓
Création du salarié
```

C'est ici que l'on passe conceptuellement de :

```text
CANDIDAT
```

à :

```text
EMPLOYE
```

---

## 3.5. Pourquoi Candidat != Employé ?

Une personne peut être candidate sans jamais devenir salariée.

Exemple :

```text
Rakoto
   │
   ├── Candidature 2025 → refusée
   ├── Candidature 2026 → refusée
   └── Candidature 2027 → retenue → devient salarié
```

Il faut donc conserver les candidatures précédentes.

---

## 3.6. Matricule

Lors de la création du salarié, l'entreprise lui attribue un matricule.

Exemple :

```text
EMP-2026-00125
```

Le matricule devient l'identifiant professionnel du salarié dans l'entreprise.

Un document PDF peut également être généré avec les informations du salarié.

Exemple :

```text
DOSSIER SALARIÉ

Matricule : EMP-2026-00125
Nom : Rakoto
Poste : Chauffeur
Département : Logistique
Date d'entrée : 01/12/2026
```

---

## 3.7. Création du contrat

Après l'acceptation, le contrat est créé.

Le système doit notamment prendre en compte :

- type de contrat ;
- date de début ;
- date de fin lorsqu'elle existe ;
- rémunération ;
- horaires ;
- lieu de travail ;
- poste ;
- conditions prévues par l'entreprise ;
- documents associés.

Le contrat peut ensuite être généré sous forme de document PDF.

---

## 3.8. Affectation

Il faut également créer l'affectation du salarié.

Exemple :

```text
Employé : Rakoto
Poste : Chauffeur
Département : Logistique
Lieu : Antananarivo
Date de début : 01/12/2026
```

Objet :

```text
Affectation
-----------
id
employe
poste
departement
dateDebut
dateFin
...
```

L'affectation doit être historisée.

---

# Bloc 4 — Contrat et Onboarding

## 4.1. Objectif

Ce bloc permet de gérer :

1. les contrats successifs du salarié ;
2. l'historique des contrats ;
3. l'affectation ;
4. l'intégration du salarié dans l'entreprise.

---

## 4.2. Plusieurs contrats successifs

Un salarié peut avoir plusieurs contrats pendant sa carrière.

Exemple :

```text
Rakoto
  │
  ├── Contrat d'essai
  │
  ├── CDD
  │
  ├── Renouvellement
  │
  └── CDI
```

Il ne faut donc pas stocker uniquement :

```text
Employe
-------
typeContrat = CDI
```

car cette conception détruirait l'historique.

---

## 4.3. Historisation des contrats

On peut avoir :

```text
Contrat
-------
id
employe
type
dateDebut
dateFin
statut
salaire
document
...
```

Exemple :

| Contrat | Début | Fin | État |
|---|---|---|---|
| Contrat d'essai | 01/12/2026 | 31/05/2027 | Terminé |
| CDD | 01/06/2027 | 31/05/2029 | Terminé |
| CDI | 01/06/2029 | — | Actif |

Ainsi, en consultant le salarié, on peut retrouver tous ses contrats précédents.

> Les règles juridiques exactes concernant les durées et renouvellements doivent être vérifiées selon le droit du travail applicable. Les durées indiquées dans les notes de cours peuvent être considérées comme des règles de travail du projet tant qu'elles ne sont pas validées juridiquement.

---

## 4.4. États du contrat

Le cours indique notamment :

```text
CREE
SIGNE
VALIDE
```

Le workflow peut être :

```text
Contrat créé
      ↓
Contrat signé
      ↓
Contrat validé
```

Chaque changement d'état peut être historisé.

---

## 4.5. Affectation et historique

Le salarié peut changer de poste ou de département au cours de sa carrière.

Exemple :

```text
2026
Chauffeur
Logistique

      ↓

2028
Chef d'équipe
Logistique

      ↓

2030
Responsable transport
Logistique
```

Le système doit conserver les anciennes affectations.

```text
Affectation 1
01/12/2026 → 30/11/2028
Chauffeur

Affectation 2
01/12/2028 → 31/12/2030
Chef d'équipe

Affectation 3
01/01/2031 → ...
Responsable transport
```

---

## 4.6. Onboarding

L'onboarding correspond à l'intégration du nouveau salarié dans l'entreprise.

Il commence notamment après la création et la signature du contrat.

Les tâches peuvent être :

```text
Contrat signé
      ↓
Matricule créé
      ↓
Badge créé
      ↓
Compte informatique créé
      ↓
Matériel attribué
      ↓
Formation
      ↓
Inscription à la paie
```

---

## 4.7. Exemples de tâches d'onboarding

### RH

- vérifier le dossier ;
- vérifier le contrat ;
- créer le matricule.

### Administration

- créer le badge ;
- préparer les documents administratifs.

### Informatique

- créer le compte ;
- préparer l'ordinateur ;
- donner les accès nécessaires.

### Logistique

- attribuer une voiture ;
- attribuer du matériel.

### Formation

- organiser les formations nécessaires.

### Paie

- inscrire le salarié dans le système de paie.

---

## 4.8. Checklist d'onboarding

L'onboarding peut être représenté par une checklist :

```text
ONBOARDING — Rakoto

☑ Contrat signé
☑ Matricule créé
☑ Badge créé
☐ Compte informatique
☐ Ordinateur
☐ Véhicule
☐ Formation
☐ Inscription paie
```

Cela permet aux différents responsables de savoir ce qui reste à faire.

---

## 4.9. Objet Onboarding

```text
Onboarding
----------
id
employe
dateDebut
dateFin
statut
```

Exemple de statuts :

```text
A_PREPARER
EN_COURS
TERMINE
ANNULE
```

---

## 4.10. Objet OnboardingTache

```text
OnboardingTache
---------------
id
onboarding
libelle
responsable
dateEcheance
dateExecution
statut
commentaire
```

Exemple :

```text
Onboarding #25

Tâche : Créer le compte informatique
Responsable : IT
Échéance : 02/12/2026
Statut : EN_ATTENTE
```

---

## 4.11. Documents

Le système devra également gérer les documents liés au recrutement et au salarié.

Exemples :

```text
CV
Contrat
Diplômes
Documents administratifs
Matricule PDF
Attestations
Documents d'affectation
Documents d'onboarding
```

Objet possible :

```text
Document
--------
id
nom
type
chemin
dateCreation
auteur
...
```

---

# 5. Traçabilité sur les blocs 1 à 4

La traçabilité concerne tous les blocs.

## Bloc 1

Conserver :

- qui a créé la demande ;
- qui l'a validée ;
- qui l'a refusée ;
- quand ;
- avec quel commentaire.

## Bloc 2

Conserver :

- les candidatures ;
- les présélections ;
- les entretiens ;
- les tests ;
- les évaluations ;
- les décisions.

## Bloc 3

Conserver :

- la proposition d'embauche ;
- la réponse du candidat ;
- la création du salarié ;
- le matricule ;
- le contrat ;
- l'affectation.

## Bloc 4

Conserver :

- tous les contrats successifs ;
- les états des contrats ;
- les affectations successives ;
- les tâches d'onboarding ;
- les documents ;
- les changements importants.

---

# 6. Vue globale des objets métier

```text
Département
     │
     └──────< Poste
                 │
                 └────< DemandeRecrutement
                              │
                              ▼
                         OffreEmploi
                              │
                              └────< Candidature >──── Candidat
                                           │
                              ┌────────────┴────────────┐
                              ▼                         ▼
                         Entretien              EvaluationCandidat
                              │
                              ▼
                         Sélection
                              │
                              ▼
                           Employe
                           /                               /                                ▼         ▼
                     Contrat   Affectation
                         │
                         ▼
                     Onboarding
                         │
                         ▼
                  OnboardingTache
                         │
                         ▼
                      Document
```

---

# 7. Principe fondamental à retenir

Le système ne doit pas être conçu comme une succession de simples écrans CRUD.

Il faut partir du processus métier :

```text
PROCESSUS MÉTIER
       ↓
RÈGLES DE GESTION
       ↓
OBJETS MÉTIER
       ↓
WORKFLOWS
       ↓
DONNÉES
       ↓
INTERFACES
       ↓
APPLICATION
```

Pour les quatre premiers blocs :

```text
1. Besoin
   ↓
2. Recrutement
   ↓
3. Sélection / Embauche
   ↓
4. Contrat / Onboarding
```

avec comme principe transversal :

```text
          TRAÇABILITÉ
              │
    ┌─────────┼─────────┐
    ↓         ↓         ↓
  Besoin  Recrutement  Employé
              ↓
          Contrats
              ↓
         Onboarding
```

Le système doit toujours pouvoir répondre à la question :

> **« Qu'est-ce qui s'est passé, quand, par qui et pourquoi ? »**
