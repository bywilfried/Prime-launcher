# Prime Launcher — Fonctionnalités et nouveautés de la prochaine release

Prime Launcher est un launcher Android open source basé sur Lawnchair 16 (Launcher3 / Android 16). Ce document présente les fonctions présentes dans la branche **Prime-identity**, en distinguant les ajouts Prime des possibilités héritées de Lawnchair. Il ne constitue pas une garantie de validation de la build finale.

## 1. Tiroir d'applications : modes et organisation Prime

- Trois modes d'affichage : **Classique (Default)**, **Onglets (Tabs)** et **Caddy (bêta)**.
- Profils de réglages **indépendants pour chaque mode**, afin de passer de l'un à l'autre sans écraser les préférences de disposition.
- Mode Onglets : création et renommage de catégories personnalisées, réorganisation des onglets et gestion des applications associées.
- Options pour afficher ou masquer les onglets **Toutes les applications** et **Non classées**, ainsi que le bouton **+** de création de catégorie.
- Choix de l'onglet affiché à l'ouverture du tiroir et navigation entre catégories par glissement.
- Dossiers au sein des catégories, avec gestion des applications et personnalisation avancée des catégories et des dossiers.
- Personnalisation visuelle par catégorie/dossier : paramètres hérités ou surchargés, avec option de remise à zéro.
- Réglages de disposition du tiroir : nombre de colonnes, hauteur des lignes, indentation, marge supérieure, taille des icônes, noms, taille des noms et libellés sur deux lignes.
- Réglages du fond, de l'opacité, de la recherche et des suggestions d'applications ; possibilité de masquer des applications.
- Réinitialisation des réglages propres au mode actif.

## 2. Écran d'accueil

- Grille personnalisable et réglages des espacements horizontaux et verticaux.
- Taille des icônes, affichage des noms et taille des libellés.
- Verrouillage de la disposition, barre d'état configurable et options de défilement du fond d'écran.
- Options de défilement infini, gestes et comportement des raccourcis.
- Réglages des widgets : coins arrondis, chevauchement, suppression de certaines contraintes de taille et redimensionnement forcé lorsque pris en charge.
- Réglage **global** de l'espacement/marge des widgets.

## 3. Dock : réglages indépendants Prime

- Taille des icônes du dock **indépendante** de celle des icônes de l'écran d'accueil.
- Taille des libellés du dock réglable séparément ; possibilité de masquer les noms.
- Aperçus des dossiers du dock adaptés au facteur de taille des icônes.
- Pastilles de notification dimensionnées en fonction de la taille effective des icônes, y compris pour les dossiers.

## 4. Widgets : contrôles individuels Prime

- Menu contextuel sur appui long : **Redimensionner**, puis **Marges du widget**.
- Déplacement par appui long suivi d'un glissement, avec seuil de pré-déplacement inspiré du comportement des icônes.
- Réglage **individuel des quatre marges** (gauche, haut, droite, bas) de **−40 % à +40 %**, permettant de compenser ou d'augmenter l'espacement global.
- Conservation des réglages de marge par instance de widget.
- Mise à jour des dimensions utiles transmises au fournisseur du widget pour favoriser son adaptation ; l'affichage réel dépend de l'application du widget.
- Aperçu flottant des grands widgets temporairement limité à **80 % de la largeur** et **60 % de la hauteur** de l'écran pendant le déplacement ; taille réelle inchangée après dépôt.

## 5. Personnalisation visuelle et thèmes

- Couleurs dynamiques Material 3 Expressive.
- Personnalisation des couleurs, de l'apparence des icônes, des packs d'icônes et des polices.
- Options de couleurs et de formes propres aux catégories et dossiers du tiroir, avec possibilité d'hériter des réglages généraux.
- Options de couleur du texte et des fonds selon les écrans de préférences disponibles.

## 6. Recherche, navigation et intégrations Lawnchair

- Recherche d'applications, de contacts et de résultats web.
- Intégration **At a Glance** et **Smartspacer**.
- Prise en charge de **QuickSwitch** pour l'intégration des applications récentes sur Android 15/16 (**root requis**).
- Paramètres de recherche distincts pour les surfaces du launcher, selon les fonctions disponibles.

## 7. Réglages avancés et diagnostics Prime

- Réglage de l'intensité des retours haptiques par action : appui long, démarrage et déplacement, seuil et appui dans le tiroir, réorganisation et annulation.
- Réinitialisation des intensités haptiques.
- Options de développement : redémarrage du launcher, consultation des journaux de diagnostic et diagnostic des glissements entre catégories.

## 8. Corrections et améliorations récentes

- Ajustement du rendu des pastilles de notification aux tailles d'icônes personnalisées.
- Améliorations du déplacement et du redimensionnement des widgets.
- Réorganisation du menu des widgets, sans commande « Déplacer » redondante.
- Correctifs de compatibilité dans le code du tiroir d'applications.

## Notes de validation

Les fonctions ci-dessus sont documentées d'après les réglages et le code de la branche **Prime-identity**. Les modifications les plus récentes ne sont pas déclarées comme entièrement testées dans une APK de release. Certains widgets peuvent conserver des contraintes internes imposées par leur fournisseur.
