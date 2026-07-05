package it.unifi.ing.chargenet.presentation.controller;

import it.unifi.ing.chargenet.business.services.RatingService;
import it.unifi.ing.chargenet.business.services.SessionService;
import it.unifi.ing.chargenet.business.strategies.ChargingStrategy;
import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.sessions.ChargingSession;
import it.unifi.ing.chargenet.domain.sessions.ChargingType;
import it.unifi.ing.chargenet.domain.sessions.SessionStatus;
import it.unifi.ing.chargenet.domain.users.Driver;
import it.unifi.ing.chargenet.domain.users.User;
import it.unifi.ing.chargenet.presentation.navigation.NavigationManager;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class LiveSessionController {

    // Header
    @FXML private Label walletBalanceLabel;
    // Simulator
    @FXML private TextField initialBatteryInput;
    @FXML private ToggleButton fastToggle;
    @FXML private ToggleButton ecoToggle;
    // Status
    @FXML private Label statusLabel;
    @FXML private Label percentageLabel;
    @FXML private ProgressBar batteryProgressBar;
    @FXML private Label timeRemainingLabel;
    @FXML private Label speedLabel;
    @FXML private Label kwhDeliveredLabel;
    @FXML private Label durationLabel;
    @FXML private Label costLabel;
    @FXML private Button stopSessionBtn;
    // Rating popup
    @FXML private StackPane ratingPopup;
    @FXML private Label ratingMessageLabel;
    @FXML private Label star1, star2, star3, star4, star5;
    @FXML private TextField commentField;

    private Driver loggedDriver;
    private ChargingStation station;
    private ChargingSession session;
    private SessionService sessionService;
    private RatingService ratingService;

    private Long sessionId;
    private Timeline pollTimeline;
    private int selectedRating = 0;

    /** Chiamato da StationExplorerController.handleChargeNow (apertura di una NUOVA sessione). */
    public void initData(Driver driver, ChargingStation station) {
        // Se siamo già entrati in modalità "resume" da initialize(), non sovrascrivere
        // la sessione viva con una nuova selezione.
        if (session != null) return;

        this.loggedDriver = driver;
        this.station = station;
        speedLabel.setText(station.getPowerKw().intValue() + " kW");
        updateWallet(driver.getWalletBalance());
        ratingPopup.setVisible(false);
    }

    @FXML
    public void initialize() {
        sessionService = NavigationManager.getSessionService();
        ratingService  = NavigationManager.getRatingService();

        // Toggle mutuamente esclusivi; Fast preselezionato
        ToggleGroup group = new ToggleGroup();
        fastToggle.setToggleGroup(group);
        ecoToggle.setToggleGroup(group);
        group.selectedToggleProperty().addListener((obs, old, sel) -> {
            if (sel == null) fastToggle.setSelected(true);   // impedisci deselezione totale
        });

        stopSessionBtn.setDisable(true);   // nessuna sessione ancora aperta

        // RESUME: se il driver ha già una sessione attiva (lasciata correre col "Back"),
        // riaggancia quella invece di attendere una nuova apertura da Station Explorer.
        // Il driver qui arriva dal session context, perché nel rientro initData NON viene chiamato.
        User current = NavigationManager.getCurrentUser();
        if (current instanceof Driver d) {
            try {
                ChargingSession active = sessionService.getActiveSessionForDriver(d);
                if (active != null) {
                    this.loggedDriver = d;
                    enterLiveMode(active);
                }
            } catch (Exception ignored) { /* nessun rientro: flusso normale via initData */ }
        }
    }

    @FXML
    private void startSimulation() {
        if (session != null) return;   // già avviata (o rientrata)

        // 1. Batteria iniziale
        double batteryStart;
        try {
            batteryStart = Double.parseDouble(initialBatteryInput.getText().trim());
            if (batteryStart < 0 || batteryStart > 100) throw new NumberFormatException();
        } catch (Exception e) {
            showAlert(Alert.AlertType.WARNING, "Valore non valido",
                    "Inserisci una percentuale batteria tra 0 e 100.");
            return;
        }

        // 2. Strategia scelta
        ChargingType type = ecoToggle.isSelected() ? ChargingType.ECO : ChargingType.FAST;
        ChargingStrategy strategy = ChargingStrategy.fromEnum(type);

        // 3. Apertura VERA della sessione (saldo minimo verificato nel dominio)
        ChargingSession opened;
        try {
            opened = sessionService.openSession(loggedDriver, station, strategy, batteryStart);
        } catch (RuntimeException e) {
            // Cattura InsufficientBalanceException / StationNotAvailableException / errori DB
            Throwable cause = (e.getCause() != null) ? e.getCause() : e;
            showAlert(Alert.AlertType.WARNING, "Impossibile avviare", cause.getMessage());
            return;   // session resta null: nessuno stato sporco da ripulire
        }

        // 4. Entra in modalità "charging" e avvia il polling
        enterLiveMode(opened);
    }

    /**
     * Porta la schermata nello stato "charging" agganciando una sessione già aperta.
     * Usato sia dall'avvio nuovo (startSimulation) sia dal rientro (initialize/resume).
     */
    private void enterLiveMode(ChargingSession activeSession) {
        this.session = activeSession;
        this.sessionId = activeSession.getId();

        // Nel rientro station e driver non sono passati da initData: li ricavo dalla sessione.
        if (this.station == null) {
            this.station = activeSession.getStation();
        }
        if (this.loggedDriver == null && activeSession.getDriver() != null) {
            this.loggedDriver = activeSession.getDriver();
        }

        if (station != null && station.getPowerKw() != null) {
            speedLabel.setText(station.getPowerKw().intValue() + " kW");
        }
        if (loggedDriver != null) {
            updateWallet(loggedDriver.getWalletBalance());
        }

        // UI in stato "charging"
        initialBatteryInput.setDisable(true);
        fastToggle.setDisable(true);
        ecoToggle.setDisable(true);
        stopSessionBtn.setDisable(false);
        statusLabel.setText("Charging...");
        ratingPopup.setVisible(false);

        // Il GridMonitor fa girare i tick, noi osserviamo
        startPolling();
    }

    private void startPolling() {
        pollTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> refreshFromDb()));
        pollTimeline.setCycleCount(Timeline.INDEFINITE);
        pollTimeline.play();
    }

    /** Rilegge lo stato fresco dal DB (prodotto dal GridMonitor) e aggiorna la UI. */
    private void refreshFromDb() {
        ChargingSession fresh = sessionService.getActiveSessionForDriver(loggedDriver);

        if (fresh == null) {
            onSessionEnded();
            return;
        }

        this.session = fresh;

        double battery = fresh.getBatteryCurrent() != null ? fresh.getBatteryCurrent() : 0.0;
        percentageLabel.setText(String.valueOf((int) battery));
        batteryProgressBar.setProgress(battery / 100.0);

        double kwh = fresh.getKwhDelivered() != null ? fresh.getKwhDelivered() : 0.0;
        kwhDeliveredLabel.setText(String.format("%.1f kWh", kwh));

        BigDecimal cost = fresh.getCostTotal() != null ? fresh.getCostTotal() : BigDecimal.ZERO;
        costLabel.setText(String.format("€ %.2f", cost));

        if (fresh.getOpenedAt() != null) {
            long secs = ChronoUnit.SECONDS.between(fresh.getOpenedAt(), LocalDateTime.now());
            durationLabel.setText(String.format("%02d:%02d:%02d", secs / 3600, (secs % 3600) / 60, secs % 60));
        }

        if (fresh.getDriver() != null && fresh.getDriver().getWalletBalance() != null) {
            updateWallet(fresh.getDriver().getWalletBalance());
        }
    }

    @FXML
    private void stopCharging() {
        if (pollTimeline != null) pollTimeline.stop();
        try {
            if (session != null && session.getStatus() == SessionStatus.ACTIVE) {
                sessionService.closeSession(session);
            }
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Errore", "Chiusura sessione: " + e.getMessage());
        }
        onSessionEnded();
    }

    private void onSessionEnded() {
        if (pollTimeline != null) pollTimeline.stop();
        stopSessionBtn.setDisable(true);

        SessionStatus finalStatus = null;
        if (sessionId != null) {
            try {
                ChargingSession closed = sessionService.getSessionById(sessionId);
                if (closed != null) finalStatus = closed.getStatus();
            } catch (Exception ignored) { }
        }

        final SessionStatus status = finalStatus;
        // Rimanda FUORI dal tick del Timeline: showAndWait non è permesso durante l'animazione
        Platform.runLater(() -> {
            if (status == SessionStatus.INTERRUPTED_THERMAL) {
                statusLabel.setText("Emergency stop!");
                showAlert(Alert.AlertType.WARNING, "Arresto di emergenza",
                        "Emergency stop: system overheating.\n" +
                                "La sessione è stata interrotta automaticamente. " +
                                "I kWh pagati ma non erogati ti sono stati rimborsati.");
            } else {
                statusLabel.setText("Session Complete!");
            }
            ratingPopup.setVisible(true);
        });
    }

    // --- RATING ---
    @FXML private void handleStar1(MouseEvent e) { updateStarColors(1); }
    @FXML private void handleStar2(MouseEvent e) { updateStarColors(2); }
    @FXML private void handleStar3(MouseEvent e) { updateStarColors(3); }
    @FXML private void handleStar4(MouseEvent e) { updateStarColors(4); }
    @FXML private void handleStar5(MouseEvent e) { updateStarColors(5); }

    private void updateStarColors(int rating) {
        this.selectedRating = rating;
        Label[] stars = {star1, star2, star3, star4, star5};
        for (int i = 0; i < 5; i++) {
            stars[i].setStyle("-fx-text-fill: " + (rating >= i + 1 ? "#f59100" : "#E0E0E0") + "; -fx-cursor: hand;");
        }
    }

    @FXML
    private void submitRating() {
        if (selectedRating == 0) {
            showAlert(Alert.AlertType.WARNING, "Valutazione mancante", "Seleziona almeno una stella.");
            return;
        }
        try {
            String comment = (commentField != null) ? commentField.getText() : null;
            ratingService.leaveRating(loggedDriver, station, session, selectedRating, comment);
        } catch (Exception e) {
            showAlert(Alert.AlertType.WARNING, "Attenzione", e.getMessage());
        }
        closeAndReturn();
    }

    @FXML
    private void skipRating() {
        closeAndReturn();
    }

    private void closeAndReturn() {
        ratingPopup.setVisible(false);
        NavigationManager.navigateTo("DRIVER_MENU");
    }

    @FXML
    private void goBack(MouseEvent event) {
        // La sessione, se attiva, prosegue nel GridMonitor: fermiamo solo il polling locale.
        if (pollTimeline != null) pollTimeline.stop();
        NavigationManager.navigateTo("STATION_EXPLORER");
    }

    private void updateWallet(BigDecimal balance) {
        walletBalanceLabel.setText(String.format("€ %.2f", balance != null ? balance : BigDecimal.ZERO));
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.getDialogPane().setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        alert.getDialogPane().setPrefWidth(420);
        alert.showAndWait();
    }
}