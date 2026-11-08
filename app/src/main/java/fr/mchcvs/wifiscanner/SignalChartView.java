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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.wifi.ScanResult;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public final class SignalChartView extends View {

    private static final int RSSI_MIN = -100;
    private static final int RSSI_MAX = -30;

    private static final int ROUGE = Color.rgb(220, 45, 45);
    private static final int ORANGE = Color.rgb(240, 135, 35);
    private static final int JAUNE = Color.rgb(235, 195, 35);
    private static final int VERT_CLAIR = Color.rgb(125, 195, 100);
    private static final int VERT_FONCE = Color.rgb(25, 115, 55);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final List<ScanResult> results = new ArrayList<>();

    public SignalChartView(Context context) {
        super(context);
        initialiser();
    }

    public SignalChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialiser();
    }

    private void initialiser() {
        setBackgroundColor(Color.WHITE);
    }

    public void setResults(List<ScanResult> nouveauxResultats) {
        results.clear();

        if (nouveauxResultats != null) {
            results.addAll(nouveauxResultats);
        }

        requestLayout();
        invalidate();
    }

    private float dp(float valeur) {
        return valeur * getResources().getDisplayMetrics().density;
    }

    private float sp(float valeur) {
        return valeur * getResources().getDisplayMetrics().scaledDensity;
    }

    private int couleur(int rssi) {
        if (rssi <= -80) return ROUGE;
        if (rssi <= -70) return ORANGE;
        if (rssi <= -60) return JAUNE;
        if (rssi <= -50) return VERT_CLAIR;
        return VERT_FONCE;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int largeur = MeasureSpec.getSize(widthMeasureSpec);

        int hauteur = Math.round(
            dp(24 + 22 + 12 + 3 * 22 + 16 + 16)
            + results.size() * dp(52)
        );

        if (results.isEmpty()) {
            hauteur += Math.round(dp(32));
        }

        setMeasuredDimension(
            resolveSize(largeur, widthMeasureSpec),
            resolveSize(hauteur, heightMeasureSpec)
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float largeur = getWidth();
        float marge = dp(12);
        float droite = largeur - marge;

        paint.setColor(Color.rgb(30, 30, 30));
        paint.setTextSize(sp(17));
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextAlign(Paint.Align.LEFT);

        float y = marge + dp(17);
        canvas.drawText("NIVEAU DU SIGNAL RSSI", marge, y, paint);

        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setTextSize(sp(11));
        paint.setColor(Color.rgb(85, 85, 85));

        y += dp(22);
        canvas.drawText(
            "RSSI réel (dBm) — échelle graphique : -100 à -30",
            marge, y, paint
        );

        String[] legendes = {
            "Très faible ≤ -80",
            "Faible -79 à -70",
            "Moyen -69 à -60",
            "Bon -59 à -50",
            "Excellent ≥ -49"
        };

        int[] couleurs = {
            ROUGE, ORANGE, JAUNE, VERT_CLAIR, VERT_FONCE
        };

        float colonne = (largeur - 2 * marge) / 2;
        float debutLegende = y + dp(17);

        paint.setTextSize(sp(11));

        for (int i = 0; i < legendes.length; i++) {
            int ligne = i / 2;
            int col = i % 2;

            float x = marge + col * colonne;
            float centreY = debutLegende + ligne * dp(22);

            paint.setColor(couleurs[i]);
            canvas.drawRect(
                x, centreY - dp(8),
                x + dp(11), centreY + dp(3), paint
            );

            paint.setColor(Color.rgb(55, 55, 55));
            canvas.drawText(
                legendes[i], x + dp(16), centreY + dp(2), paint
            );
        }

        y = debutLegende + dp(3 * 22 + 12);

        if (results.isEmpty()) {
            paint.setTextSize(sp(13));
            paint.setColor(Color.GRAY);
            canvas.drawText(
                "Aucun point d'accès à représenter.",
                marge, y + dp(12), paint
            );
            return;
        }

        float hauteurBarre = dp(8);
        float largeurBarre = largeur - 2 * marge;

        for (int i = 0; i < results.size(); i++) {
            ScanResult result = results.get(i);

            float debutLigne = y + i * dp(52);
            String nom = result.SSID;

            if (nom == null || nom.isEmpty()
                    || "<unknown ssid>".equals(nom)) {
                nom = "SSID masqué ou non communiqué";
            }

            String etiquette = (i + 1) + ". " + nom;
            String valeur = result.level + " dBm";

            textPaint.setTextSize(sp(13));
            textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            float largeurValeur = textPaint.measureText(valeur);

            float largeurNom = droite - marge - largeurValeur - dp(12);
            String nomCourt = TextUtils.ellipsize(
                etiquette,
                textPaint,
                Math.max(0, largeurNom),
                TextUtils.TruncateAt.END
            ).toString();

            textPaint.setColor(Color.rgb(35, 35, 35));
            textPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(
                nomCourt, marge, debutLigne + dp(12), textPaint
            );

            textPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(
                valeur, droite, debutLigne + dp(12), textPaint
            );

            float barreY = debutLigne + dp(21);

            paint.setColor(Color.rgb(230, 230, 230));
            canvas.drawRoundRect(
                new RectF(marge, barreY, droite, barreY + hauteurBarre),
                dp(4), dp(4), paint
            );

            float proportion = (result.level - RSSI_MIN)
                / (float) (RSSI_MAX - RSSI_MIN);
            proportion = Math.max(0f, Math.min(1f, proportion));

            paint.setColor(couleur(result.level));
            canvas.drawRoundRect(
                new RectF(
                    marge, barreY,
                    marge + largeurBarre * proportion,
                    barreY + hauteurBarre
                ),
                dp(4), dp(4), paint
            );
        }
    }
}
