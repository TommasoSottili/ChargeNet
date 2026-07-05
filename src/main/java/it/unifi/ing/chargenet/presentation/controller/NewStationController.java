package it.unifi.ing.chargenet.presentation.controller;

import it.unifi.ing.chargenet.business.exceptions.NoTransformerAvailableException;
import it.unifi.ing.chargenet.business.exceptions.InvalidStationParametersException;
import it.unifi.ing.chargenet.business.services.StationService;
import it.unifi.ing.chargenet.domain.users.ConnectorType;
import it.unifi.ing.chargenet.domain.users.StationOperator;
import it.unifi.ing.chargenet.domain.users.User;
import it.unifi.ing.chargenet.presentation.navigation.NavigationManager;
import it.unifi.ing.chargenet.domain.infrastructure.Zone;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;

import java.math.BigDecimal;

public class NewStationController {

    @FXML private TextField nameField;
    @FXML private TextField addressField;
    @FXML private TextField powerField;
    @FXML private TextField tariffField;
    @FXML private ComboBox<String> connectorCombo;
    @FXML private ComboBox<Zone> zoneCombo;
    @FXML private Button deployBtn;

    private StationOperator loggedOperator;
    private StationService stationService;

    @FXML
    public void initialize() {
        User current = NavigationManager.getCurrentUser();
        if (!(current instanceof StationOperator)) {
            NavigationManager.navigateTo("LOGIN");
            return;
        }
        this.loggedOperator = (StationOperator) current;
        this.stationService = NavigationManager.getStationService();

        for (ConnectorType type : ConnectorType.values()) {
            connectorCombo.getItems().add(type.name());
        }
        for (Zone zone : Zone.values()) {
            zoneCombo.getItems().add(zone);
        }
    }

    @FXML
    private void handleDeploy(MouseEvent event) {
        // Validazione campi testuali
        if (nameField.getText().trim().isEmpty()
                || addressField.getText().trim().isEmpty()
                || powerField.getText().trim().isEmpty()
                || tariffField.getText().trim().isEmpty()) {
            showAlert("Attenzione", "Tutti i campi sono obbligatori.");
            return;
        }
        if (connectorCombo.getValue() == null) {
            showAlert("Attenzione", "Seleziona un tipo di connettore.");
            return;
        }
        if (zoneCombo.getValue() == null) {
            showAlert("Attenzione", "Seleziona un quartiere.");
            return;
        }

        // Parsing potenza
        double power;
        try {
            power = Double.parseDouble(powerField.getText().trim().replace(",", "."));
            if (power <= 0) {
                showAlert("Attenzione", "La potenza deve essere maggiore di zero.");
                return;
            }
        } catch (NumberFormatException e) {
            showAlert("Attenzione", "Potenza non valida (es. 150).");
            return;
        }

        // Parsing tariffa
        BigDecimal tariff;
        try {
            tariff = new BigDecimal(tariffField.getText().trim().replace(",", "."));
            if (tariff.compareTo(BigDecimal.ZERO) <= 0) {
                showAlert("Attenzione", "La tariffa deve essere maggiore di zero.");
                return;
            }
        } catch (NumberFormatException e) {
            showAlert("Attenzione", "Tariffa non valida (es. 0.30).");
            return;
        }

        // Coordinate reali dal quartiere scelto (base del quartiere + jitter)
        double[] coords = zoneCombo.getValue().resolveWithJitter();   // [lat, lng]

        try {
            // Il service assegna il trasformatore meno carico e imposta lo stato PENDING_VERIFICATION.
            stationService.registerStation(
                    loggedOperator,
                    nameField.getText().trim(),
                    addressField.getText().trim(),
                    coords[0], coords[1],          // lat, lng del quartiere
                    ConnectorType.valueOf(connectorCombo.getValue()),
                    power,
                    false,
                    tariff);

            showAlert("Colonnina registrata",
                    "La colonnina è stata creata ed è ora IN ATTESA DI VERIFICA da parte dell'Energy Manager. "
                            + "Sarà visibile ai driver una volta approvata.");
            clearFields();
            NavigationManager.navigateTo("STATION_HUB");

        } catch (NoTransformerAvailableException e) {
            showAlert("Rete satura", e.getMessage());
        } catch (InvalidStationParametersException e) {
            // Errore di validazione di dominio: input non valido, non un guasto tecnico
            showAlert("Parametri non validi", e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            showAlert("Errore", "Impossibile registrare la colonnina: " + root.getMessage());
        }
    }

    @FXML
    private void clearFields() {
        nameField.clear();
        addressField.clear();
        powerField.clear();
        tariffField.clear();
        connectorCombo.getSelectionModel().clearSelection();
        zoneCombo.getSelectionModel().clearSelection();
    }

    @FXML
    private void goBack(MouseEvent event) {
        NavigationManager.navigateTo("STATION_HUB");
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);   // messaggio non troncato
        alert.showAndWait();
    }
}