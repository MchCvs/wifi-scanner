/*
 * Scanner WiFi
 * Copyright (C) 2026 Thierry CHEVASSUS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * See LICENSE for the full license text.
 * See ADDITIONAL-TERMS.md for the additional author attribution condition.
 */

package fr.mchcvs.wifiscanner;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int REQUEST_LOCATION = 100;
    private static final long SCAN_TIMEOUT_MS = 30_000L;
    private static final String HISTORY_PREFS = "historique_wifi";
    private static final String HISTORY_KEY = "scans";
    private static final int MAX_HISTORY = 50;

    private TextView status;
    private TextView connectedNetworkView;
    private TextView resultsView;
    private SignalChartView signalChartView;
    private Button permissionButton;
    private Button scanButton;

    private WifiManager wifiManager;
    private boolean receiverRegistered = false;
    private boolean scanPending = false;

    private final Handler mainHandler =
        new Handler(Looper.getMainLooper());

    private final Runnable scanTimeout = () -> {
        if (!scanPending) {
            return;
        }

        scanPending = false;
        status.setText(
            "Aucune réponse d'Android après 30 secondes.\n\n" +
            "Aucun nouveau résultat n'est présenté comme une mesure récente. " +
            "Tu peux relancer un scan."
        );
        scanButton.setEnabled(true);
    };

    private final BroadcastReceiver wifiScanReceiver =
        new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!WifiManager.SCAN_RESULTS_AVAILABLE_ACTION
                        .equals(intent.getAction())) {
                    return;
                }

                if (!scanPending) {
                    return;
                }

                mainHandler.removeCallbacks(scanTimeout);
                scanPending = false;

                boolean updated = intent.getBooleanExtra(
                    WifiManager.EXTRA_RESULTS_UPDATED, false
                );

                if (!updated) {
                    status.setText(
                        "Échec de l'actualisation.\n\n" +
                        "Android n'a pas confirmé de nouveaux résultats. " +
                        "Les anciennes données ne seront pas présentées " +
                        "comme des mesures récentes."
                    );
                    scanButton.setEnabled(true);
                    return;
                }

                afficherResultats();
            }
        };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        wifiManager = (WifiManager) getApplicationContext()
            .getSystemService(Context.WIFI_SERVICE);

        construireInterface();
        actualiserEtat();
    }

    private void construireInterface() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setPadding(32, 32, 32, 24);

        TextView titre = new TextView(this);
        titre.setText("ANALYSEUR WIFI");
        titre.setTextSize(24);
        titre.setGravity(Gravity.CENTER);

        TextView explication = new TextView(this);
        explication.setText(
            "Analyse réelle des réseaux Wi-Fi détectés."
        );
        explication.setTextSize(15);
        explication.setGravity(Gravity.CENTER);
        explication.setPadding(0, 20, 0, 20);

        connectedNetworkView = new TextView(this);
        connectedNetworkView.setTextSize(14);
        connectedNetworkView.setPadding(0, 8, 0, 16);

        status = new TextView(this);
        status.setTextSize(16);
        status.setPadding(0, 12, 0, 16);

        permissionButton = new Button(this);
        permissionButton.setText("Autoriser l'accès Wi-Fi");
        permissionButton.setOnClickListener(v -> {
            if (autorisationPreciseAccordee() && !localisationActive()) {
                startActivity(
                    new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                );
            } else if (refusDefinitifPermissions()) {
                ouvrirParametresApplication();
            } else {
                demanderPermission();
            }
        });

        scanButton = new Button(this);
        scanButton.setText("Lancer un scan Wi-Fi");
        scanButton.setOnClickListener(v -> lancerScan());

        Button historyButton = new Button(this);
        historyButton.setText("Consulter l'historique");
        historyButton.setOnClickListener(v -> afficherHistorique());

        resultsView = new TextView(this);
        resultsView.setTextSize(14);
        resultsView.setTextIsSelectable(true);
        resultsView.setPadding(0, 20, 0, 20);

        signalChartView = new SignalChartView(this);

        LinearLayout contenu = new LinearLayout(this);
        contenu.setOrientation(LinearLayout.VERTICAL);
        contenu.addView(signalChartView);
        contenu.addView(resultsView);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(contenu);

        layout.addView(titre);
        layout.addView(explication);
        layout.addView(connectedNetworkView);
        layout.addView(status);
        layout.addView(permissionButton);
        layout.addView(scanButton);
        layout.addView(historyButton);

        layout.addView(
            scrollView,
            new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
            )
        );

        TextView signature = new TextView(this);
        signature.setText("Créé par Thierry CHEVASSUS — Piton Sainte Rose, La Réunion, France.");
        signature.setTextSize(12);
        signature.setTextColor(Color.GRAY);
        signature.setGravity(Gravity.CENTER);
        signature.setPadding(0, 8, 0, 4);
        layout.addView(signature);

        setContentView(layout);
    }

    private void afficherReseauConnecte() {
        if (wifiManager == null || !wifiManager.isWifiEnabled()) {
            connectedNetworkView.setText("Réseau connecté : Wi-Fi désactivé");
            return;
        }

        if (!autorisationPreciseAccordee() || !localisationActive()) {
            connectedNetworkView.setText(
                "Informations réseau indisponibles : localisation nécessaire."
            );
            return;
        }

        try {
            WifiInfo info = wifiManager.getConnectionInfo();

            if (info == null || info.getNetworkId() == -1) {
                connectedNetworkView.setText("Aucun réseau Wi-Fi connecté.");
                return;
            }

            String ssid = info.getSSID();
            if (ssid == null || ssid.isEmpty()
                    || "<unknown ssid>".equals(ssid)) {
                ssid = "SSID non communiqué par Android";
            } else if (ssid.startsWith("\\\"") && ssid.endsWith("\\\"")) {
                ssid = ssid.substring(1, ssid.length() - 1);
            }

            int frequency = info.getFrequency();
            String band = frequency >= 5925 ? "6 GHz"
                : frequency >= 4900 ? "5 GHz"
                : frequency >= 2400 ? "2,4 GHz"
                : "Inconnue";

            connectedNetworkView.setText(
                "RÉSEAU CONNECTÉ\n" + ssid +
                "\n" + band + " — " + frequency + " MHz" +
                "\nSignal : " + info.getRssi() + " dBm"
            );

        } catch (SecurityException e) {
            connectedNetworkView.setText(
                "Accès aux informations réseau refusé par Android."
            );
        }
    }

    private boolean refusDefinitifPermissions() {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);

        if (!preferences.getBoolean("demande_permission_effectuee", false)) {
            return false;
        }

        boolean precise = checkSelfPermission(
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        boolean approximative = checkSelfPermission(
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        if (precise || approximative) {
            return false;
        }

        return !shouldShowRequestPermissionRationale(
            Manifest.permission.ACCESS_FINE_LOCATION
        ) && !shouldShowRequestPermissionRationale(
            Manifest.permission.ACCESS_COARSE_LOCATION
        );
    }

    private void ouvrirParametresApplication() {
        Intent intent = new Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", getPackageName(), null)
        );
        startActivity(intent);
    }

    private void demanderPermission() {
        requestPermissions(
            new String[] {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            },
            REQUEST_LOCATION
        );
    }

    private boolean autorisationPreciseAccordee() {
        return checkSelfPermission(
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void actualiserEtat() {
        boolean precise = autorisationPreciseAccordee();

        boolean approximative = checkSelfPermission(
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        if (!precise) {
            status.setText(
                approximative
                    ? "Localisation approximative accordée.\n\n" +
                      "La localisation précise est nécessaire au scan Wi-Fi."
                    : "Autorisation de localisation non accordée."
            );

            permissionButton.setText("Autoriser l'accès Wi-Fi");
            permissionButton.setVisibility(View.VISIBLE);
            scanButton.setEnabled(false);
            return;
        }

        if (!localisationActive()) {
            status.setText(
                "Autorisation précise accordée.\n\n" +
                "Les services de localisation sont désactivés."
            );
            permissionButton.setText("Activer la localisation");
            permissionButton.setVisibility(View.VISIBLE);
            scanButton.setEnabled(false);
            return;
        }

        permissionButton.setVisibility(View.GONE);

        if (wifiManager == null) {
            status.setText("Service Wi-Fi Android indisponible.");
            scanButton.setEnabled(false);
            return;
        }

        if (!wifiManager.isWifiEnabled()) {
            status.setText(
                "Wi-Fi désactivé sur le téléphone.\n\n" +
                "Active le Wi-Fi dans les réglages Android."
            );
            scanButton.setText("Ouvrir les réglages Wi-Fi");
            scanButton.setEnabled(true);
            return;
        }

        status.setText(
            "Prérequis validés.\n" +
            "Wi-Fi actif. Aucun scan en cours."
        );
        scanButton.setText("Lancer un scan Wi-Fi");
        scanButton.setEnabled(true);
    }

    private boolean localisationActive() {
        LocationManager manager =
            (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (manager == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return manager.isLocationEnabled();
        }

        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    private void enregistrerRecepteur() {
        if (receiverRegistered) {
            return;
        }

        IntentFilter filter = new IntentFilter(
            WifiManager.SCAN_RESULTS_AVAILABLE_ACTION
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                wifiScanReceiver,
                filter,
                Context.RECEIVER_EXPORTED
            );
        } else {
            registerReceiver(wifiScanReceiver, filter);
        }

        receiverRegistered = true;
    }

    private void lancerScan() {
        if (wifiManager == null) {
            status.setText("Service Wi-Fi Android indisponible.");
            return;
        }

        if (!wifiManager.isWifiEnabled()) {
            startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
            return;
        }

        if (!autorisationPreciseAccordee() || !localisationActive()) {
            actualiserEtat();
            return;
        }

        if (scanPending) {
            status.setText("Un scan est déjà en cours.");
            return;
        }

        if (!receiverRegistered) {
            status.setText("Récepteur Android indisponible.");
            return;
        }

        resultsView.setText("");
        signalChartView.setResults(Collections.emptyList());
        status.setText("Demande de scan envoyée. Attente d'Android…");
        scanButton.setEnabled(false);
        scanPending = true;

        try {
            boolean started = wifiManager.startScan();

            if (!started) {
                mainHandler.removeCallbacks(scanTimeout);
                scanPending = false;
                status.setText(
                    "Android n'a pas lancé le scan.\n\n" +
                    "Une limitation de fréquence ou un autre problème " +
                    "peut empêcher cette opération."
                );
                scanButton.setEnabled(true);
            } else {
                mainHandler.postDelayed(
                    scanTimeout, SCAN_TIMEOUT_MS
                );
            }

        } catch (SecurityException e) {
            mainHandler.removeCallbacks(scanTimeout);
            scanPending = false;
            status.setText(
                "Autorisation refusée par Android :\n" + e.getMessage()
            );
            scanButton.setEnabled(true);
        }
    }

    private void enregistrerHistorique(List<ScanResult> results) {
        SharedPreferences preferences =
            getSharedPreferences(HISTORY_PREFS, MODE_PRIVATE);

        try {
            JSONArray ancien = new JSONArray(
                preferences.getString(HISTORY_KEY, "[]")
            );

            JSONObject scan = new JSONObject();
            scan.put("date", System.currentTimeMillis());

            JSONArray points = new JSONArray();

            for (ScanResult result : results) {
                JSONObject point = new JSONObject();
                point.put("ssid", nomReseau(result));
                point.put("bssid", valeur(result.BSSID));
                point.put("rssi", result.level);
                point.put("frequence", result.frequency);
                point.put("bande", bande(result.frequency));
                point.put("canal", canal(result.frequency));
                points.put(point);
            }

            scan.put("points", points);

            JSONArray nouveau = new JSONArray();
            nouveau.put(scan);

            for (int i = 0; i < ancien.length()
                    && i < MAX_HISTORY - 1; i++) {
                nouveau.put(ancien.getJSONObject(i));
            }

            preferences.edit()
                .putString(HISTORY_KEY, nouveau.toString())
                .apply();

        } catch (JSONException e) {
            status.setText(
                "Mesure réalisée, mais erreur d'enregistrement de l'historique."
            );
        }
    }

    private void afficherHistorique() {
        SharedPreferences preferences =
            getSharedPreferences(HISTORY_PREFS, MODE_PRIVATE);

        try {
            JSONArray historique = new JSONArray(
                preferences.getString(HISTORY_KEY, "[]")
            );

            if (historique.length() == 0) {
                new AlertDialog.Builder(this)
                    .setTitle("Historique Wi-Fi")
                    .setMessage("Aucune mesure enregistrée.")
                    .setPositiveButton("Fermer", null)
                    .show();
                return;
            }

            SimpleDateFormat format =
                new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());

            StringBuilder texte = new StringBuilder();

            for (int i = 0; i < historique.length(); i++) {
                JSONObject scan = historique.getJSONObject(i);
                JSONArray points = scan.getJSONArray("points");

                texte.append(format.format(
                    new Date(scan.getLong("date"))
                ));
                texte.append(" — ").append(points.length())
                    .append(" point(s) d'accès\n");

                for (int j = 0; j < points.length(); j++) {
                    JSONObject point = points.getJSONObject(j);

                    texte.append("  ")
                        .append(point.optString("ssid"))
                        .append(" — ")
                        .append(point.optInt("rssi"))
                        .append(" dBm — ")
                        .append(point.optInt("frequence"))
                        .append(" MHz — canal ")
                        .append(point.optString("canal"))
                        .append("\n");
                }

                texte.append("------------------------------\n");
            }

            TextView contenu = new TextView(this);
            contenu.setText(texte.toString());
            contenu.setTextSize(14);
            contenu.setTextIsSelectable(true);
            contenu.setPadding(32, 24, 32, 24);

            ScrollView defilement = new ScrollView(this);
            defilement.addView(contenu);

            new AlertDialog.Builder(this)
                .setTitle("Historique des 50 derniers scans")
                .setView(defilement)
                .setPositiveButton("Fermer", null)
                .show();

        } catch (JSONException e) {
            new AlertDialog.Builder(this)
                .setTitle("Historique Wi-Fi")
                .setMessage("Impossible de lire l'historique enregistré.")
                .setPositiveButton("Fermer", null)
                .show();
        }
    }

    private void afficherResultats() {
        try {
            List<ScanResult> results =
                new ArrayList<>(wifiManager.getScanResults());

            Collections.sort(
                results,
                (a, b) -> Integer.compare(b.level, a.level)
            );

            status.setText(
                "Actualisation confirmée par Android.\n" +
                "Points d'accès détectés : " + results.size()
            );

            signalChartView.setResults(results);
            enregistrerHistorique(results);

            if (results.isEmpty()) {
                resultsView.setText(
                    "Aucun point d'accès retourné par Android."
                );
            } else {
                StringBuilder output = new StringBuilder();
                int index = 1;

                for (ScanResult result : results) {
                    output.append(index++)
                        .append(". ")
                        .append(nomReseau(result))
                        .append('\n');

                    output.append("BSSID : ")
                        .append(valeur(result.BSSID))
                        .append('\n');

                    output.append("Signal réel : ")
                        .append(result.level)
                        .append(" dBm\n");

                    output.append("Qualité estimée (RSSI) : ")
                        .append(qualiteSignal(result.level))
                        .append('\n');

                    output.append("Fréquence : ")
                        .append(result.frequency)
                        .append(" MHz\n");

                    output.append("Bande : ")
                        .append(bande(result.frequency))
                        .append('\n');

                    output.append("Canal : ")
                        .append(canal(result.frequency))
                        .append('\n');

                    output.append("Sécurité : ")
                        .append(valeur(result.capabilities))
                        .append('\n');

                    output.append("Âge du relevé : ")
                        .append(ageResultat(result.timestamp))
                        .append('\n');

                    output.append("------------------------------\n\n");
                }

                resultsView.setText(output.toString());
            }

        } catch (SecurityException e) {
            status.setText(
                "Lecture des résultats refusée par Android : " +
                e.getMessage()
            );
            resultsView.setText("");
            signalChartView.setResults(Collections.emptyList());
        }

        scanButton.setEnabled(true);
    }

    private String qualiteSignal(int rssi) {
        if (rssi <= -80) return "Très faible";
        if (rssi <= -70) return "Faible";
        if (rssi <= -60) return "Moyenne";
        if (rssi <= -50) return "Bonne";
        return "Excellente";
    }

    private String nomReseau(ScanResult result) {
        if (result.SSID == null || result.SSID.isEmpty()
                || "<unknown ssid>".equals(result.SSID)) {
            return "SSID masqué ou non communiqué";
        }
        return result.SSID;
    }

    private String valeur(String value) {
        return value == null || value.isEmpty()
            ? "Non communiqué"
            : value;
    }

    private String bande(int frequency) {
        if (frequency >= 2400 && frequency < 2500) return "2,4 GHz";
        if (frequency >= 4900 && frequency < 5925) return "5 GHz";
        if (frequency >= 5925 && frequency <= 7125) return "6 GHz";
        if (frequency >= 57000 && frequency <= 71000) return "60 GHz";
        return "Non déterminée";
    }

    private String canal(int frequency) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            int channel =
                ScanResult.convertFrequencyMhzToChannelIfSupported(frequency);

            if (channel > 0) {
                return Integer.toString(channel);
            }
        }

        return "Non déterminé";
    }

    private String ageResultat(long timestampMicros) {
        if (timestampMicros <= 0) {
            return "indisponible";
        }

        long maintenant =
            SystemClock.elapsedRealtimeNanos() / 1000L;

        long ageSecondes =
            (maintenant - timestampMicros) / 1000000L;

        if (ageSecondes < 0) {
            return "indisponible";
        }

        if (ageSecondes < 60) {
            return ageSecondes + " s";
        }

        if (ageSecondes < 3600) {
            return (ageSecondes / 60) + " min";
        }

        return String.format(
            Locale.FRANCE,
            "%.1f h",
            ageSecondes / 3600.0
        );
    }

    @Override
    public void onRequestPermissionsResult(
        int requestCode,
        String[] permissions,
        int[] grantResults
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        );

        if (requestCode == REQUEST_LOCATION) {
            getPreferences(MODE_PRIVATE)
                .edit()
                .putBoolean("demande_permission_effectuee", true)
                .apply();

            actualiserEtat();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        enregistrerRecepteur();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (status != null) {
            actualiserEtat();
            afficherReseauConnecte();
        }
    }

    @Override
    protected void onStop() {
        if (receiverRegistered) {
            unregisterReceiver(wifiScanReceiver);
            receiverRegistered = false;
        }

        if (scanPending) {
            mainHandler.removeCallbacks(scanTimeout);
            scanPending = false;

            if (status != null) {
                status.setText(
                    "Application suspendue avant la réception du scan. " +
                    "Relance un scan à son retour."
                );
            }
        }

        super.onStop();
    }
}
