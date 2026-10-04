# Tīaʻi

Application Android pour personnes âgées : bouton SOS et signe de vie quotidien, avec alerte par SMS aux proches. Prototype, version 0.1.

« Tīaʻi » est un nom provisoire.

## Ce que fait cette version

- **SOS** : gros bouton rouge, compte à rebours de 5 secondes (annulable), puis SMS à tous les proches avec un lien vers la position.
- **Fausse alerte** : un second SMS rassure les proches.
- **Signe de vie** : bouton « Je vais bien » à appuyer une fois par jour. Rappel sonore à l'heure choisie. À l'heure limite, sans appui, un SMS part aux proches.
- **Mise en veille** : aujourd'hui, 3 jours, 1 semaine ou jusqu'à une date. Les proches reçoivent un SMS. Le SOS reste actif.
- **Proches** : ajout et suppression, SMS d'essai.
- **Réglages** : prénom, horaires, position dans le SMS, mode essai (aucun SMS ne part).

Les SMS partent du téléphone, avec le forfait de la personne. Il n'y a pas de serveur dans cette version.

## Limites connues

- **Téléphone éteint ou déchargé : aucune alerte ne part.** L'alerte « pas de signe de vie » fiable demande un serveur (étape suivante).
- Android peut retarder le rappel et l'alerte de quelques minutes quand le téléphone dort. Sur Samsung, Xiaomi, etc., il faut désactiver l'économie de batterie pour l'application.
- Un appui sur « Je vais bien » compte pour la journée, quelle que soit l'heure.
- La position peut manquer (GPS coupé, intérieur d'un bâtiment).
- Pas encore : appui long sur le volume, widget d'écran d'accueil, accord des proches, abonnement, interface en tahitien.
- L'application ne remplace pas les secours (15, 18).

## Obtenir l'APK avec GitHub

1. Déposer tous les fichiers de ce dossier dans un dépôt GitHub, y compris le dossier caché `.github`.
2. Onglet **Actions** : le travail « Construire l'APK » démarre seul à chaque dépôt sur la branche `main` (ou bouton **Run workflow**).
3. Quand il est vert, ouvrir le travail et télécharger **tiai-apk** en bas de page. Le fichier zip contient `app-debug.apk`.
4. Copier l'APK sur le téléphone et l'installer (autoriser « sources inconnues »).

Chaque APK construit ainsi a une signature différente : pour installer une nouvelle version, désinstaller d'abord l'ancienne.

On peut aussi ouvrir le dossier dans Android Studio et lancer l'application.

## Organisation du code

`app/src/main/java/pf/tiai/app/`

| Fichier | Rôle |
| --- | --- |
| `MainActivity.kt` | Les six écrans |
| `Store.kt` | Données enregistrées sur le téléphone |
| `Sms.kt` | Textes des SMS, envoi, position |
| `Alarms.kt` | Rappel et heure limite du signe de vie |
| `Ui.kt` | Couleurs, boutons, tiare et frise de coquillages |
