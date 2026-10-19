# Publication sur Maven Central

Ce document décrit la procédure pour publier `gs1-parser` sur Maven Central.
Fichier local uniquement — non versionné (voir `.gitignore`).

## 1. Prérequis (à faire une seule fois)

- Un compte sur le [Central Portal](https://central.sonatype.com/) avec le namespace `fr.valyra` vérifié.
- Une paire de clés GPG créée et publiée sur un keyserver (`gpg --full-generate-key`, puis `gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>`).
- Les identifiants suivants renseignés dans `~/.gradle/gradle.properties` (jamais dans le repo) :

  ```properties
  mavenCentralUsername=<token Central Portal>
  mavenCentralPassword=<token Central Portal>

  # Signature via gpg-agent (le projet utilise useGpgCmd())
  signing.gnupg.keyName=<KEY_ID>
  signing.gnupg.passphrase=<passphrase>
  ```

  Sur cette machine, `mavenCentralUsername`, `mavenCentralPassword` et les clés `signing.*` sont déjà présentes dans `~/.gradle/gradle.properties`.

## 2. Avant de publier

1. Vérifier/incrémenter la version dans `gradle.properties` (`VERSION_NAME`).
2. Mettre à jour le `README.md` si le numéro de version y est mentionné (badge, exemple d'installation).
3. Lancer le build complet et les tests :

   ```bash
   ./gradlew build
   ```

4. Committer et tagger la release :

   ```bash
   git commit -am "chore: release <version>"
   git tag v<version>
   git push && git push --tags
   ```

## 3. Publier

- **Test local** (installe dans `~/.m2/repository`, pour vérifier les artefacts sans rien envoyer) :

  ```bash
  ./gradlew publishToMavenLocal
  ```

- **Publication en staging sur Maven Central** (dépose les artefacts, release manuelle ensuite depuis le [Central Portal](https://central.sonatype.com/publishing)) :

  ```bash
  ./gradlew publish
  ```

- **Publication + release automatique** (dépose et déclenche la release en une seule commande) :

  ```bash
  ./gradlew publishAndReleaseToMavenCentral
  ```

En cas de souci après un dépôt en staging (avant release), le dropper avec `./gradlew dropMavenCentralDeployment`.

## 4. Après publication

- Vérifier la disponibilité sur [search.maven.org](https://search.maven.org/artifact/fr.valyra/gs1-parser) (propagation généralement sous 15–30 min après release).
- Vérifier que le badge Maven Central du `README.md` reflète la nouvelle version.
