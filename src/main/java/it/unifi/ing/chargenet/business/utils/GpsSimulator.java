package it.unifi.ing.chargenet.business.utils;

public class GpsSimulator {
    // Coordinate del "rettangolo" che racchiude l'area urbana di Firenze
    private static final double FIRENZE_MIN_LAT = 43.74;
    private static final double FIRENZE_MAX_LAT = 43.82;
    private static final double FIRENZE_MIN_LNG = 11.18;
    private static final double FIRENZE_MAX_LNG = 11.32;

    /**
     * Genera un punto casuale all'interno dell'area di Firenze.
     * Simula il rilevamento di un sensore GPS.
     */
    public static double[] randomPosition() {
        double lat = FIRENZE_MIN_LAT + Math.random() * (FIRENZE_MAX_LAT - FIRENZE_MIN_LAT);
        double lng = FIRENZE_MIN_LNG + Math.random() * (FIRENZE_MAX_LNG - FIRENZE_MIN_LNG);
        return new double[]{ lat, lng };
    }
}