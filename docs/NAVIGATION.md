# Navigation et identité visuelle SIGRH

## Priorité : retrouver les fonctionnalités des quatre blocs

Chaque page métier partage une barre latérale persistante, le nom de la section active, un fil d'Ariane et l'identité de la personne connectée. L'accueil présente les quatre espaces dans l'ordre du parcours RH. Les termes métier apparaissent dans l'interface ; les numéros de blocs restent des repères de collaboration dans ce document.

| Espace | Écrans accessibles depuis le menu | Bloc |
| --- | --- | --- |
| Vue d'ensemble | Accueil et recherche d'un écran | Transversal |
| Besoins en personnel | Demandes et validations | 1 |
| Recrutement | Offres, candidatures, évaluations | 2 |
| Embauche | Propositions d'embauche | 3 |
| Personnel et intégration | Dossiers salariés, contrats, affectations, suivi d'intégration, mes tâches, catalogues, historique | 4 |
| Espace candidat | Offres disponibles et mes propositions | Parcours candidat |

Les pages de détail et les formulaires restent dans leur section et conservent un lien de retour contextuel. Les nouvelles listes transversales renvoient aux traitements existants : pas de duplication des opérations métier.

## Découverte et autorisations

Tous les collaborateurs voient les rubriques des quatre blocs. Un lien autorisé ouvre la page métier. Un lien non autorisé ouvre une présentation du module, avec ses écrans, leur fonction et le rôle nécessaire. Cette présentation ne contient aucune donnée métier personnelle. Aucun lien du menu n'accorde de droit supplémentaire.

La RH et la Direction retrouvent les opérations de recrutement et d'embauche déjà autorisées. La gestion générale du personnel reste réservée à la RH ; les autres salariés retrouvent uniquement leurs informations et tâches attribuées. Les candidats disposent de leurs liens dédiés ; ils peuvent découvrir la structure du produit depuis les présentations, sans accès aux dossiers internes.

La navigation est définie dans un registre Java unique utilisé par la barre latérale, les cartes d'accueil, les présentations et la recherche d'écran. Les états actifs utilisent les routes réelles. La recherche porte sur les intitulés des écrans, pas sur des données sensibles.

## Connexion avant refonte des pages

Une page HTML/Thymeleaf remplace l'écran généré par Spring Security. Un contrôleur prépare le formulaire et les messages. Spring Security traite le POST du formulaire, vérifie BCrypt, protège la session et gère la réussite ou l'échec. Les erreurs ne révèlent pas si un identifiant existe ; le mot de passe n'est jamais renvoyé dans le HTML. Une session candidate sans profil associé est fermée proprement. Déconnexion par formulaire POST, protection CSRF et message de confirmation.

## Palette et typographie

| Usage | Choix |
| --- | --- |
| Barre latérale / titres | Bleu marine `#172F43` |
| Action principale / lien actif | Sarcelle `#167A74` |
| Fond général | Gris clair `#F4F6F8` |
| Surface / formulaires | Blanc `#FFFFFF` |
| Texte principal | Ardoise `#243746` |
| Texte secondaire | Gris ardoise `#647587` |
| Bordures | Gris `#DFE6EC` |
| Attention | Ambre ; toujours accompagné d'un libellé |
| Action destructive | Rouge ; jamais utilisé pour une action principale ordinaire |
| Police | Inter, hébergée localement, avec repli sur les polices système |

Les boutons suivent trois niveaux : principal plein, secondaire avec bordure, lien discret. Les messages et badges décrivent leur état en texte, sans dépendre de la couleur. Les tableaux conservent un en-tête lisible, une sélection visuelle des lignes au survol et un défilement horizontal sur petit écran.

## Accessibilité et maintenance

Menu adapté aux petits écrans avec bouton explicite, fermeture et attributs ARIA. Lien d'évitement vers le contenu. Indicateur de focus visible. Recherche d'écran accessible au clavier, fermeture par Échap et raccourci Ctrl/Cmd+K. Les données métier continuent à fonctionner sans les améliorations JavaScript de navigation.

Les nouveaux HTML sont indentés, avec une balise par ligne, des groupes d'attributs lisibles et des commentaires sur les sections importantes. Les fragments partagés remplacent les en-têtes locaux des pages actives. Les anciens prototypes sous `templates/tmp/` ne sont pas des écrans routés.

## Références

- [Frappe — Sidebar](https://docs.frappe.io/framework/user/en/desk/navigation/sidebar) : regroupement des écrans par module et séparation entre navigation et permissions.
- [Odoo — Job positions](https://www.odoo.com/documentation/18.0/applications/hr/recruitment/new_job.html) : accès au suivi du recrutement depuis un tableau de bord et les fiches de poste.
- [Inter](https://github.com/rsms/inter) : police libre utilisée pour la lisibilité des interfaces.
