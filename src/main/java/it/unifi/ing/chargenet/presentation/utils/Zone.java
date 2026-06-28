package it.unifi.ing.chargenet.presentation.utils;

public enum Zone {
    CENTRO_STORICO("Centro Storico", 43.7695, 11.2558),
    NOVOLI("Novoli", 43.7910, 11.2330),
    CAMPO_DI_MARTE("Campo di Marte", 43.7750, 11.2800),
    RIFREDI("Rifredi", 43.7980, 11.2420),
    GAVINANA("Gavinana - Sud", 43.7553, 11.2702),
    ISOLOTTO("Isolotto - Ovest", 43.7710, 11.2050),
    COVERCIANO("Coverciano", 43.7730, 11.2950);

    private final String label;
    private final double baseLat;
    private final double baseLng;

    Zone(String label, double baseLat, double baseLng) {
        this.label = label;
        this.baseLat = baseLat;
        this.baseLng = baseLng;
    }

    /**
     * Aggiunge un microscopico scostamento (Jitter) per far sì che
     * due colonnine registrate nello stesso quartiere non abbiano
     * coordinate identiche (altrimenti si sovrapporrebbero esattamente).
     */
    public double[] resolveWithJitter() {
        // +/- 0.005 gradi corrisponde a circa +/- 500 metri di variazione casuale
        double jLat = (Math.random() - 0.5) * 0.01;
        double jLng = (Math.random() - 0.5) * 0.01;
        return new double[]{ baseLat + jLat, baseLng + jLng };
    }

    @Override
    public String toString() {
        return label;
    }
}