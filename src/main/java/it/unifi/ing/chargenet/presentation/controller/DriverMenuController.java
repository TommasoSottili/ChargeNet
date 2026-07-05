package it.unifi.ing.chargenet.presentation.controller;

import it.unifi.ing.chargenet.business.services.AuthService;
import it.unifi.ing.chargenet.business.services.SessionService;
import it.unifi.ing.chargenet.business.utils.GpsSimulator;
import it.unifi.ing.chargenet.domain.sessions.ChargingSession;
import it.unifi.ing.chargenet.domain.users.Driver;
import it.unifi.ing.chargenet.domain.users.User;
import it.unifi.ing.chargenet.presentation.navigation.NavigationManager;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import java.math.BigDecimal;

public class DriverMenuController {

    @FXML private HBox resumeSessionCard;
    @FXML private Label resumeSubtitleLabel;
    @FXML private Label walletBalanceLabel;
    @FXML private Label welcomeLabel;
    @FXML private Button updateLocationBtn;

    private Driver loggedDriver;
    private SessionService sessionService;

    @FXML
    public void initialize() {
        // 1. Recupera l'utente dal session context
        User current = NavigationManager.getCurrentUser();
        if (!(current instanceof Driver)) {
            System.err.println("[DriverMenu] Nessun Driver in sessione, ritorno al login.");
            NavigationManager.navigateTo("LOGIN");
            return;
        }

        // 2. Ricarica fresco dal DB tramite il SERVICE (mai il DAO direttamente):
        //    il saldo wallet potrebbe essere cambiato dopo una ricarica.
        AuthService authService = NavigationManager.getAuthService();
        User refreshed = authService.refreshUser(current.getId());

        Driver driver = (refreshed instanceof Driver d) ? d : (Driver) current;

        // 3. Aggiorna la sessione con la versione fresca.
        NavigationManager.loginUser(driver);

        // 4. Popola la UI
        initData(driver);

        // 5. Se esiste una sessione attiva (lasciata correre col "Back"), mostra la card "Riprendi"
        this.sessionService = NavigationManager.getSessionService();
        refreshResumeCard(driver);
    }

    /** Popola l'intestazione con i dati del Driver. */
    public void initData(Driver driver) {
        this.loggedDriver = driver;
        if (driver == null) return;

        String name = (driver.getName() != null && !driver.getName().isEmpty())
                ? driver.getName() : "Driver";
        welcomeLabel.setText("Welcome back, " + name + "!");

        BigDecimal balance = (driver.getWalletBalance() != null)
                ? driver.getWalletBalance() : BigDecimal.ZERO;
        walletBalanceLabel.setText(String.format("€ %.2f", balance));
    }

    private void refreshResumeCard(Driver driver) {
        try {
            ChargingSession active = sessionService.getActiveSessionForDriver(driver);
            boolean hasActive = (active != null);
            resumeSessionCard.setVisible(hasActive);
            resumeSessionCard.setManaged(hasActive);   // niente spazio vuoto quando è nascosta
            if (hasActive && active.getStation() != null) {
                resumeSubtitleLabel.setText("Charging at " + active.getStation().getName());
            }
        } catch (Exception e) {
            e.printStackTrace();
            resumeSessionCard.setVisible(false);
            resumeSessionCard.setManaged(false);
        }
    }

    // --- AGGIORNA POSIZIONE (simulazione GPS) ---

    @FXML
    private void handleUpdateLocation(ActionEvent event) {
        if (loggedDriver == null) return;

        // 1. Rileva una nuova posizione GPS casuale nell'area di Firenze
        double[] pos = GpsSimulator.randomPosition();   // [lat, lng]

        try {
            // 2. Il DriverService fa updatePosition sul dominio + update sul DB, in transazione.
            //    (Niente updatePosition qui: lo fa già il service, evitiamo la doppia mutazione.)
            NavigationManager.getDriverService()
                    .updateDriverLocation(loggedDriver, pos[0], pos[1]);

            // 3. Ricarica fresco così la sessione resta coerente col DB
            User refreshed = NavigationManager.getAuthService().refreshUser(loggedDriver.getId());
            if (refreshed instanceof Driver d) {
                this.loggedDriver = d;
                NavigationManager.loginUser(d);
            }

            showAlert(Alert.AlertType.INFORMATION, "Location updated",
                    String.format("New GPS position detected:%n%.5f, %.5f", pos[0], pos[1]));
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error",
                    "Could not update location: " + e.getMessage());
        }
    }

    // --- NAVIGAZIONE ---

    @FXML
    private void goToStationExplorer(MouseEvent event) {
        NavigationManager.navigateTo("STATION_EXPLORER");
    }

    @FXML
    private void goToFundWallet(MouseEvent event) {
        NavigationManager.navigateTo("FUND_WALLET");
    }

    @FXML
    private void goToHistory(MouseEvent event) {
        NavigationManager.navigateTo("HISTORY");
    }

    @FXML
    private void goToAccount(MouseEvent event) {
        NavigationManager.navigateTo("ACCOUNT");
    }

    @FXML
    private void handleLogout(MouseEvent event) {
        NavigationManager.logout();
    }

    @FXML
    private void goToActiveSession(MouseEvent event) {
        NavigationManager.navigateTo("LIVE_SESSION");
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alert.showAndWait();
    }
}