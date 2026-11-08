# Scanner WiFi

Application Android native créée par Thierry CVS — La Réunion.

## Fonctionnalités
- Scan manuel des réseaux Wi-Fi.
- Affichage des SSID et des informations disponibles.
- Mesure réelle du signal RSSI en dBm.
- Graphique horizontal à cinq niveaux de couleur.
- Estimation de la qualité du signal.
- Identification du réseau Wi-Fi connecté.
- Affichage de la bande et de la fréquence.
- Historique local des 50 derniers scans.
- Gestion des permissions et erreurs Android.
- Délai d'attente de 30 secondes.

## Échelle RSSI

| RSSI | Qualité | Couleur |
|---|---|---|
| ≤ -80 dBm | Très faible | Rouge |
| -79 à -70 dBm | Faible | Orange |
| -69 à -60 dBm | Moyenne | Jaune |
| -59 à -50 dBm | Bonne | Vert clair |
| ≥ -49 dBm | Excellente | Vert foncé |

Le RSSI ne permet pas de calculer une distance physique fiable.
Il ne mesure pas la qualité de la connexion Internet.

## Compatibilité
- Android 8.0 minimum (API 26).
- API cible 36.
- Testée sur Samsung Galaxy A04s sous Android 14.

## Autorisations
- ACCESS_FINE_LOCATION
- ACCESS_COARSE_LOCATION
- ACCESS_WIFI_STATE
- CHANGE_WIFI_STATE

Les services de localisation doivent être activés pour effectuer les scans.

Android peut limiter la fréquence des scans et retourner des résultats antérieurs. L'application distingue les résultats actualisés de ceux dont l'actualisation n'a pas été confirmée.

## Compilation sous Debian / Linux

Prérequis : Java JDK 21 et Android SDK avec les composants API 36.

Récupération :
    git clone https://github.com/MchCvs/wifi-scanner.git
    cd wifi-scanner

Compilation :
    chmod +x gradlew
    ./gradlew assembleDebug

APK généré :
    app/build/outputs/apk/debug/app-debug.apk

Installation :
    adb install -r app/build/outputs/apk/debug/app-debug.apk

## Technologies
- Java
- Android SDK
- Gradle
- WifiManager
- SharedPreferences

## État du projet
Les fonctionnalités principales ont été testées sur un Samsung Galaxy A04s sous Android 14.

Le traitement des scans refusés et des résultats non actualisés est implémenté. Certains cas limites restent dépendants des événements transmis par Android.

## Auteur
Thierry CVS — La Réunion

## Licence
Les conditions de réutilisation et de redistribution restent à préciser.
