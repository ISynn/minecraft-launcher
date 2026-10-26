# Audit du launcher existant

**Projet :** launcher Minecraft « Nova » (prototype indépendant, non affilié à PokéIsland, Mojang ni Microsoft)
**Date :** 26 octobre 2026
**Périmètre :** dépôt `Launcher Minecraft` au commit `aad4d9e`, dossier de jeu `%APPDATA%\.nova`
**Objet :** décider de ce qu'il faut conserver, corriger ou reconstruire avant la refonte.

---

## 1. Résumé

Le launcher est un projet **Java 24 / JavaFX 25** compact (environ 4 450 lignes de Java et 700 lignes de CSS), bien découpé entre une couche métier `core` sans dépendance graphique et une couche `ui`.

**Le cœur technique fonctionne réellement.** Les journaux du dossier de jeu montrent huit parties lancées les 23 et 24 octobre avec Minecraft 26.3, Fabric Loader 0.20.1, le Java officiel de Mojang (`java-runtime-epsilon`) et deux mods. L'installation, la vérification SHA-1, la fusion Mojang + Fabric et la construction de la ligne de commande ont donc fait leurs preuves.

**L'interface, en revanche, ne correspond pas au cahier des charges.** Elle est violette et cyan, centrée sur une scène 3D. Elle ne compte que deux écrans (Accueil, Paramètres), sans actualités, sans écran d'installation, sans compte, sans aide ni liens externes. Le bouton Jouer ne connaît que trois états.

**Recommandation :** conserver Java/JavaFX et la quasi-totalité de `core`, corriger les défauts de fiabilité et de sécurité relevés, et reconstruire entièrement la couche `ui`.

---

## 2. Méthode

| Vérification | Réalisée | Résultat |
|---|---|---|
| Lecture de l'ensemble du code source (49 fichiers Java, 1 CSS) | Oui | Voir sections 4 à 6 |
| Compilation `mvn clean compile` avec `-Xlint:all` | Oui | Succès, 4 avertissements « restricted method » (appels natifs voulus de `NativeWindow`)\* |
| Lancement réel `mvn javafx:run` | Oui | La fenêtre s'ouvre, session « Dev » restaurée |
| Capture et inspection visuelle de l'accueil et des paramètres | Oui | Voir section 7 |
| Navigation Accueil → Paramètres, fermeture par le bouton ✕ | Oui | Fonctionne, sortie propre (code 0) |
| Mesure CPU et mémoire au repos (10 s, accueil affiché) | Oui | Voir section 7 |
| Lecture des journaux de jeu (`logs/*.log.gz`, `latest.log`) | Oui | 8 sessions réussies |
| Clic sur « Jouer » et partie complète | **Non** | Déjà prouvé par les journaux. Sera refait à l'étape 5 |
| Connexion Microsoft | **Non** | Impossible : `AZURE_CLIENT_ID` est vide |
| Fonctionnement hors ligne (réseau coupé) | **Non** | Défaut identifié à la lecture du code (P2), non reproduit |
| Tests automatisés | — | **Il n'en existe aucun** (`src/test` absent) |

> \* *Correction du 26/10 :* la première version de cet audit indiquait « aucun avertissement », car le mode `-q` de Maven les masquait. Ils sont attendus (API Foreign Function autorisée par `--enable-native-access`) et ont été annotés à l'étape 2.

> **Capture de référence PokéIsland :** elle n'est pas présente dans le projet ni dans le dossier parent. La seule capture disponible, `docs/accueil.png`, montre le launcher Nova actuel. La direction artistique s'appuiera sur la description du cahier des charges tant que la capture ne sera pas fournie.

---

## 3. Technologies et organisation

| Élément | Constat |
|---|---|
| Langage | Java 24 (`maven.compiler.release` = 24), module JPMS `fr.launcher` |
| Interface | JavaFX 25.0.5 (`javafx-controls` uniquement), CSS, scène 3D `SubScene` |
| Construction | Maven 3.9, `javafx-maven-plugin` 0.0.8 (`mvn javafx:run`) |
| Dépendances | **Deux seulement** : JavaFX et Gson 2.14. HTTP via `java.net.http`, natif Windows via l'API Foreign Function (pas de JNA) |
| Points d'entrée | `Main` → `LauncherApp` → `LauncherUi.start` ; outil `tools.ManifestGenerator` |
| Configuration | Constantes compilées dans `LauncherConfig.java` + préférences joueur dans `launcher/settings.json` |
| Lancement de Minecraft | `core/game/GameLauncher` : `ProcessBuilder`, sortie redirigée vers `logs/game-output.log` |
| Téléchargements | `core/download/Downloader` : threads virtuels, 24 connexions, 3 essais, fichier `.part` puis SHA-1 puis renommage |
| API externes | piston-meta et resources (Mojang), meta.fabricmc.net, launchermeta (runtimes Java), Microsoft/Xbox/Minecraft Services, mc-heads.net (avatars) |
| Authentification | Microsoft « device code flow » → Xbox Live → XSTS → Minecraft ; compte hors ligne en `DEV_MODE` |
| Mise à jour | Du jeu et du modpack, oui, à chaque clic sur Jouer. **Du launcher lui-même, non** |
| Tests | Aucun |
| Empaquetage | Aucun exécutable ni installateur (prévu dans la feuille de route du README) |

```
src/main/java/fr/launcher
├── LauncherConfig.java        constantes de personnalisation
├── core/                      logique métier, sans JavaFX
│   ├── auth/                  Microsoft → Xbox → XSTS → Minecraft, stockage du compte
│   ├── download/              téléchargements parallèles, progression
│   ├── game/                  versions, Java, règles Mojang, lancement
│   ├── modpack/               synchronisation des mods
│   ├── server/                Server List Ping
│   └── settings/              préférences
├── ui/                        JavaFX : fenêtre, barre latérale, 3 vues, composants, scène 3D
├── tools/                     générateur de modpack.json
└── util/                      Http, Json, Hash, Os
```

---

## 4. Fonctionnalités existantes

### 4.1. Opérationnelles (vérifiées)

| Fonctionnalité | Preuve |
|---|---|
| Fenêtre personnalisée, coins et ombre natifs Windows 11 | Lancement et capture |
| Restauration de la session au démarrage | Lancement : compte « Dev » restauré |
| Navigation Accueil ↔ Paramètres avec transitions | Test manuel |
| Statut du serveur (Server List Ping) | « Serveur hors ligne » affiché, cohérent avec `localhost` sans serveur |
| Installation de Minecraft 26.3, Fabric, Java Mojang et bibliothèques | Dossiers `versions/`, `runtime/`, `libraries/` et journaux |
| Lancement du jeu avec Fabric et mods | 8 sessions dans `logs/`, 50 mods chargés (Fabric API + NovaMetallurgie) |
| Réglage de la mémoire, persistance des préférences | Écran Paramètres, `settings.json` |
| Vérification approfondie des fichiers (SHA-1) | Code lu, bouton présent. Non exécuté pendant l'audit |

### 4.2. Implémentées mais non vérifiables en l'état

| Fonctionnalité | Raison |
|---|---|
| Connexion Microsoft | Nécessite un `AZURE_CLIENT_ID` et l'approbation de Mojang. Le code suit le flux officiel |
| Synchronisation du modpack | `MODPACK_URL` est vide. Les mods actuels ont été copiés à la main |
| Connexion directe au serveur (Quick Play) | Aucun serveur réel configuré (`localhost`) |

### 4.3. Simulées ou de démonstration

| Élément | Détail |
|---|---|
| Compte hors ligne « Dev » | `DEV_MODE = true`. Le jeton vaut `"0"`, d'où les erreurs 401 attendues dans `latest.log` |
| Serveur | `localhost:25565`, donc toujours « hors ligne » |

### 4.4. Absentes (demandées par le cahier des charges)

Actualités et carrousel · écran Installation et mises à jour · écran Mon compte · centre d'aide · liens externes (site, boutique, Discord) · états « téléchargement », « mise à jour », « lancement » et « erreur » du bouton Jouer · annulation d'une installation · réglages résolution et plein écran · journal du launcher · tests · exécutable.

---

## 5. Problèmes constatés

Légende : **C** = constaté (lecture du code, exécution ou mesure) · **H** = hypothèse à confirmer.

### 5.1. Sécurité

| # | Problème | Statut | Emplacement |
|---|---|---|---|
| S1 | **Jetons stockés en clair.** `account.json` contient le jeton Minecraft et le *refresh token* Microsoft, qui permet de rouvrir la session sans mot de passe pendant des semaines. Le fichier est lisible par tout programme de l'utilisateur. | C | [AccountStore.java:28](src/main/java/fr/launcher/core/auth/AccountStore.java#L28) |
| S2 | **Le modpack distant peut supprimer n'importe quel dossier du jeu.** `managedFolders` n'est borné qu'au dossier du jeu. Un manifeste erroné ou compromis contenant `"saves"` effacerait les mondes du joueur (21 Mo de sauvegardes existent déjà). | C | [ModpackSync.java:44-52](src/main/java/fr/launcher/core/modpack/ModpackSync.java#L44-L52) |
| S3 | Le modpack accepte des fichiers sans SHA-1 : aucun contrôle d'intégrité dans ce cas. | C | [ModpackSync.java:40](src/main/java/fr/launcher/core/modpack/ModpackSync.java#L40) |
| S4 | Arguments Java libres, découpés sur les espaces : pas de validation, et un chemin contenant un espace est cassé. | C | [GameLauncher.java:84](src/main/java/fr/launcher/core/game/GameLauncher.java#L84) |
| S5 | Avatars et skins chargés depuis un service tiers (`mc-heads.net`) au lieu des services officiels de Mojang. Si ce service tombe, aucun avatar ne s'affiche. | C | [Avatar.java:40](src/main/java/fr/launcher/ui/components/Avatar.java#L40), [Showcase3D.java:155](src/main/java/fr/launcher/ui/three/Showcase3D.java#L155) |
| S6 | Le jeton d'accès figure dans la ligne de commande du jeu, donc dans la liste des processus. **Imposé par le format d'arguments de Minecraft**, commun à tous les launchers. À ne jamais journaliser. | C | [GameLauncher.java:59](src/main/java/fr/launcher/core/game/GameLauncher.java#L59) |

Points positifs : écriture atomique des JSON, contrôle de traversée de chemins dans le modpack, aucun secret dans le code, flux OAuth officiel (le launcher ne voit jamais le mot de passe), aucun jeton journalisé.

### 5.2. Fiabilité

| # | Problème | Statut | Emplacement |
|---|---|---|---|
| P1 | **Pas de journal du launcher.** Les erreurs n'apparaissent que dans des notifications éphémères. Aucun diagnostic possible après coup. | C | Ensemble du projet. Seul un `System.err` existe ([Settings.java:49](src/main/java/fr/launcher/core/settings/Settings.java#L49)) |
| P2 | **Le mode hors ligne annoncé par le README ne fonctionne pas.** La recherche de la dernière version de Fabric et la récupération du modpack n'ont pas de repli local : sans Internet, Jouer échoue alors que tout est installé. | C (lecture), non reproduit | [VersionResolver.java:37](src/main/java/fr/launcher/core/game/VersionResolver.java#L37), [VersionResolver.java:88-98](src/main/java/fr/launcher/core/game/VersionResolver.java#L88-L98), [ModpackSync.java:29-32](src/main/java/fr/launcher/core/modpack/ModpackSync.java#L29-L32) |
| P3 | **Une installation ne peut pas être annulée.** La tâche de fond n'est pas conservée et `Progress` n'a pas de notion d'annulation. | C | [PlayController.java:42](src/main/java/fr/launcher/ui/PlayController.java#L42) |
| P4 | Les erreurs n'ont pas d'état durable : le bouton revient à « JOUER » et seule une notification de 7 s explique l'échec. | C | [PlayController.java:55-58](src/main/java/fr/launcher/ui/PlayController.java#L55-L58) |
| P5 | Erreurs silencieuses : préférences ou compte corrompus remplacés sans avertissement, échec de rafraîchissement réseau ignoré, erreur de ping ignorée. | C | [Settings.java:25](src/main/java/fr/launcher/core/settings/Settings.java#L25), [AccountStore.java:23](src/main/java/fr/launcher/core/auth/AccountStore.java#L23), [LauncherUi.java:39-44](src/main/java/fr/launcher/ui/LauncherUi.java#L39-L44), [HomeView.java:330](src/main/java/fr/launcher/ui/views/HomeView.java#L330) |
| P6 | Runtime Java : si l'index répond mais pas le manifeste, l'installation échoue même quand Java est déjà installé. | C (lecture) | [JavaRuntime.java:57](src/main/java/fr/launcher/core/game/JavaRuntime.java#L57) |
| P7 | `game-output.log` est écrasé à chaque lancement : la trace d'un plantage disparaît dès la partie suivante. | C | [GameLauncher.java:96](src/main/java/fr/launcher/core/game/GameLauncher.java#L96) |

### 5.3. Conception et maintenabilité

| # | Problème | Statut | Emplacement |
|---|---|---|---|
| D1 | `HomeView` (331 lignes) mélange polling du serveur, calcul de débit, animations du bouton et mise en page. | C | [HomeView.java](src/main/java/fr/launcher/ui/views/HomeView.java) |
| D2 | La navigation est un `enum` à deux valeurs et un `switch` dans `AppWindow`. Chaque nouvel écran oblige à modifier trois endroits. | C | [Page.java](src/main/java/fr/launcher/ui/Page.java), [AppWindow.java:190-197](src/main/java/fr/launcher/ui/AppWindow.java#L190-L197) |
| D3 | `Settings` expose des champs publics modifiables, sans validation (hors RAM) ni version de format. | C | [Settings.java:13-17](src/main/java/fr/launcher/core/settings/Settings.java#L13-L17) |
| D4 | Duplications : calcul SHA-1 recopié dans `ManifestGenerator`, règle « UUID ou pseudo » recopiée dans `Avatar` et `Showcase3D`. | C | [ManifestGenerator.java:81-90](src/main/java/fr/launcher/tools/ManifestGenerator.java#L81-L90) |
| D5 | Le CSS (700 lignes) code les couleurs en dur, sans variables de thème. Changer la palette demande de modifier des dizaines de règles. | C | [launcher.css](src/main/resources/fr/launcher/assets/launcher.css) |
| D6 | L'identité (nom, serveur, versions, Azure) est compilée dans le code. Elle est simple à lire, mais un changement impose une recompilation. | C | [LauncherConfig.java](src/main/java/fr/launcher/LauncherConfig.java) |
| D7 | Aucun test, alors que plusieurs fonctions pures s'y prêtent : règles Mojang, chemins Maven, substitution d'arguments, contrôle des chemins, VarInt. | C | — |

### 5.4. Performances

| # | Problème | Statut | Emplacement |
|---|---|---|---|
| F1 | **3,5 % du processeur au repos** (≈ 280 ms de CPU par seconde), dû au fond animé, à la scène 3D à 60 images/s et à plusieurs animations infinies. C'est élevé pour une fenêtre qui attend un clic. | C (mesure) | — |
| F2 | Animations jamais arrêtées : halo et reflet du bouton, reflet du titre, onde du statut, ping toutes les 30 s. Elles continuent quand le launcher est caché pendant la partie. | C (lecture) / impact H | [HomeView.java:237-272](src/main/java/fr/launcher/ui/views/HomeView.java#L237-L272), [Shimmer.java:32-38](src/main/java/fr/launcher/ui/components/Shimmer.java#L32-L38) |
| F3 | Les bibliothèques (88 Mo) sont rehashées en SHA-1 à chaque clic sur Jouer. | C (lecture) / coût H, probablement inférieur à 1 s | [GameProfile.java:25-27](src/main/java/fr/launcher/core/game/GameProfile.java#L25-L27) |

### 5.5. Interface (observée à l'écran)

| # | Problème |
|---|---|
| U1 | Identité violette et cyan, éloignée de la référence : ni jaune, ni illustration, ni logo, ni actualités. |
| U2 | Sous certains angles, le joueur 3D est masqué par le feuillage de l'arbre (visible sur la capture de l'audit). |
| U3 | Barre de défilement visible dans les Paramètres alors que le contenu tient dans la fenêtre. |
| U4 | Curseur mémoire limité à 1–3,5 Go sur cette machine de 6 Go. Le minimum de 1 Go est irréaliste pour un jeu moddé. |
| U5 | Pas d'état vide ni d'écran d'erreur. Avatar vide si le réseau est coupé. |
| U6 | Clavier : la navigation latérale fonctionne, mais l'interrupteur maison (`Switch`) n'est pas focalisable. |

---

## 6. Risques techniques

| Risque | Impact | Mesure proposée |
|---|---|---|
| Connexion Microsoft non démontrable sans application Azure approuvée par Mojang | Le jury ne verra qu'un compte de démonstration | Garder le flux réel, afficher clairement « Mode démonstration », documenter l'activation |
| Pas d'API PokéIsland accessible (actualités, boutique, statistiques) | Contenu factice | Interface `NewsProvider`, données de démonstration **étiquetées**, URL configurable |
| Ressources protégées (illustrations, logo PokéIsland, univers Pokémon) | Problème de droits en cas de diffusion | Aucune ressource protégée dans le dépôt. Illustration libre ou capture de votre propre monde |
| Minecraft 26.x impose Java 25 au jeu | Déjà géré par le runtime Mojang | Aucune |
| Régression du cœur pendant la refonte de l'interface | Le jeu ne se lance plus | Tests unitaires du cœur avant les modifications, test de lancement réel à chaque point de contrôle |
| Machine de développement à 6 Go de RAM | Le jeu et le launcher se disputent la mémoire | Mesurer la mémoire du launcher, libérer la scène 3D pendant la partie |

---

## 7. Mesures de référence

Relevées sur cette machine (8 cœurs, 6 Go de RAM, Windows 11, affichage à 125 %), accueil affiché, fenêtre au premier plan.

| Mesure | Valeur |
|---|---|
| Compilation complète (`mvn clean compile`) | Succès, 4 avertissements « restricted method »\* |
| CPU au repos (moyenne sur 10 s) | **3,5 %** de la machine (≈ 277 ms CPU/s) |
| Mémoire (working set / privée) | **181 Mo / 290 Mo** |
| Threads | 51 |
| Taille de la fenêtre | 1100 × 680 (logique), fixe |

Ces valeurs serviront de référence à l'étape 6 : on ne corrigera que ce qui a été mesuré.

---

## 8. Décisions

### 8.1. Technologie : on garde Java et JavaFX

Changer de framework, pour Electron ou Tauri par exemple, n'est pas justifié :

- le cœur Java est fonctionnel et éprouvé. Le réécrire en JavaScript ou en Rust, c'est reprendre un code qui marche et réintroduire des bugs ;
- JavaFX permet tout ce que demande la direction artistique : CSS, animations, images, effets et fenêtre personnalisée ;
- l'empreinte reste raisonnable (≈ 180 Mo contre 300 Mo ou plus pour Electron), ce qui compte sur une machine de 6 Go qui doit aussi faire tourner le jeu ;
- un launcher en Java est cohérent avec l'écosystème Minecraft, et donc avec une candidature dans une équipe Minecraft ;
- `jpackage`, fourni avec le JDK, produit un exécutable Windows sans outil supplémentaire.

### 8.2. À conserver (avec corrections ciblées)

| Module | Raison | Correction prévue |
|---|---|---|
| `core/download` | Parallèle, atomique, vérifié par SHA-1, testé en conditions réelles | Ajouter l'annulation |
| `core/game` | Fusion Mojang + Fabric, règles, runtime Java : prouvés par 8 lancements | Repli hors ligne (P2, P6), résolution et plein écran, conservation des journaux (P7) |
| `core/auth` | Flux Microsoft officiel et correct | Chiffrer le stockage (S1) |
| `core/modpack` | Synchronisation saine | Liste blanche des dossiers gérés (S2), SHA-1 obligatoire (S3) |
| `core/server`, `util/*` | Simples et corrects | Aucune |
| `ui/NativeWindow`, `ui/Fx` | Bonne optimisation Windows 11, utilitaire asynchrone utile | Aucune |
| `ui/three/*` | Démonstration technique forte (skin réel en 3D) | Repositionner (U2), arrêter le rendu hors écran |
| `tools/ManifestGenerator` | Utile aux administrateurs | Réutiliser `Hash` (D4) |
| Police Poppins (licence OFL) | Lisible et libre | Aucune |

### 8.3. À reconstruire

| Élément | Raison |
|---|---|
| Toute la couche `ui/views` + `Sidebar` + `Page` | Identité, composition et écrans ne correspondent pas au cahier des charges (U1, D1, D2) |
| `launcher.css` | Remplacé par un système de thème à variables (D5) |
| `PlayController` | Remplacé par un service d'installation à états, annulable (P3, P4) |
| `AuroraBackground` | Remplacé par une illustration et un fond léger (F1, U1) |
| `Settings` | Validation, nouveaux réglages, version de format (D3) |

### 8.4. À ajouter

Journal du launcher avec masquage des jetons · stockage chiffré des jetons (DPAPI Windows, via l'API Foreign Function déjà utilisée) · actualités derrière une interface remplaçable · liens externes centralisés · machine d'états d'installation · réparation non destructive · tests JUnit 5 · empaquetage `jpackage`.

---

## 9. Architecture cible

```
fr.launcher
├── Main, LauncherApp
├── LauncherConfig             identité, serveur, versions, liens vérifiés, URL des actualités
├── core/                      aucune dépendance à JavaFX
│   ├── auth/                  + SecureStore (DPAPI sous Windows, permissions restreintes ailleurs)
│   ├── download/              + Cancellation
│   ├── game/                  + options de résolution et plein écran, repli hors ligne
│   ├── install/     NOUVEAU   InstallService : machine d'états, annulation, réparation
│   ├── modpack/               + liste blanche des dossiers gérés
│   ├── news/        NOUVEAU   NewsProvider (interface), DemoNewsProvider, JsonNewsProvider
│   ├── server/
│   ├── settings/              validation, migration de format
│   └── log/         NOUVEAU   journal tournant dans launcher/logs, masquage des jetons
├── ui/
│   ├── shell/                 fenêtre, barre de titre, navigation, routeur d'écrans
│   ├── theme/                 tokens : couleurs, espacements, rayons, typographie
│   ├── components/            PlayButton, NewsCarousel, ProfileChip, Card, EmptyState…
│   └── screens/               home, news, install, account, settings, help
├── tools/
└── util/
```

**Machine d'états du bouton Jouer**, alignée sur le fonctionnement réel du cœur :

```
PRÊT ──clic──▶ PRÉPARATION ──▶ TÉLÉCHARGEMENT ──▶ MISE À JOUR ──▶ LANCEMENT ──▶ EN JEU ──fin──▶ PRÊT
                (versions,      (Java, jeu,         (synchro         (process
                 modpack)        ressources)         modpack)         démarré)
       chaque étape ──erreur──▶ ERREUR (message + « Réessayer » + accès au journal)
       chaque étape ──annuler─▶ PRÊT
```

**Principes :**

- `core` reste testable sans interface ;
- chaque écran est une classe autonome enregistrée auprès du routeur ;
- les données externes passent par des interfaces : aucune URL privée n'est appelée ;
- toute donnée de démonstration porte un badge « Démo » visible.

---

## 10. Plan de refonte

Chaque étape se termine par une compilation, un lancement réel, une présentation et **une pause en attente de validation**.

| Étape | Contenu | Livrable visible |
|---|---|---|
| **2. Fondations** | Branche `refonte`, tests JUnit du cœur existant (filet de sécurité), journal du launcher, système de thème, nouvelle structure `shell` / `screens` avec routeur, composants de base, écrans vides pour toutes les rubriques | Fenêtre à la nouvelle palette, navigation complète entre des écrans vides |
| **3. Accueil** | Illustration, logo, bouton Jouer et ses états, profil, carrousel d'actualités (démo étiquetée), raccourcis | Accueil complet à juger |
| **4. Écrans secondaires** | Actualités, Installation, Mon compte, Paramètres, Aide, liens vérifiés | Tous les écrans navigables |
| **5. Fonctionnel** | `InstallService` (états, annulation), réparation, repli hors ligne, chiffrement des jetons, liste blanche du modpack, résolution et plein écran, validation des réglages | Partie réellement lancée depuis le nouveau bouton |
| **6. Optimisation** | Mesures comparées à la section 7, arrêt des animations hors écran, finitions, tests complémentaires | Tableau avant / après |
| **7. Livraison** | Documentation, exécutable `jpackage` vérifié, README à jour | Projet présentable |

---

## 11. Décisions à prendre avant l'étape 2

1. **Capture PokéIsland** : à déposer dans `docs/reference/` pour affiner la direction artistique.
2. **Nom du prototype** : garder « Nova » ou en choisir un autre. Dans tous les cas, la mention « prototype non officiel » sera visible.
3. **Illustration d'accueil** : le projet n'en contient aucune. La meilleure option, libre de droits, est une capture de votre propre monde Minecraft. À défaut, je compose un fond à partir de la scène 3D existante.
4. **Scène 3D** : je propose de la garder comme pièce de démonstration, mais en élément secondaire (écran Mon compte ou côté de l'accueil) plutôt qu'au centre.
