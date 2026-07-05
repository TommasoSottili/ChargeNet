package it.unifi.ing.chargenet.presentation.controller;

import it.unifi.ing.chargenet.business.exceptions.DuplicateEmailException;
import it.unifi.ing.chargenet.business.services.AuthService;
import it.unifi.ing.chargenet.dao.interfaces.DaoFactory;
import it.unifi.ing.chargenet.domain.users.ConnectorType;
import it.unifi.ing.chargenet.domain.users.Role;
import it.unifi.ing.chargenet.presentation.navigation.NavigationManager;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;

public class RegistrationController {

    private AuthService authService;

    @FXML private TextField nameField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private TextField passwordVisibleField;
    @FXML private ComboBox<Role> roleComboBox;
    @FXML private ImageView togglePasswordIcon;

    // --- NUOVI: campi specifici del Driver ---
    @FXML private VBox driverExtraFieldsBox;
    @FXML private ComboBox<ConnectorType> connectorTypeComboBox;

    private boolean isPasswordVisible = false;

    @FXML
    public void initialize() {
        DaoFactory factory = NavigationManager.getDaoFactory();
        if (factory == null) {
            System.err.println("CRITICO: DaoFactory non inizializzata nel NavigationManager!");
        } else {
            this.authService = new AuthService(factory);
        }

        roleComboBox.getItems().addAll(Role.DRIVER, Role.STATION_OPERATOR, Role.ENERGY_MANAGER);

        // Popola i connettori una volta sola
        connectorTypeComboBox.getItems().addAll(ConnectorType.values());

        // Mostra/nasconde i campi Driver in base al ruolo scelto
        roleComboBox.valueProperty().addListener((obs, oldRole, newRole) -> {
            boolean isDriver = (newRole == Role.DRIVER);
            driverExtraFieldsBox.setVisible(isDriver);
            driverExtraFieldsBox.setManaged(isDriver);   // se false, non occupa spazio nel layout
            if (!isDriver) connectorTypeComboBox.setValue(null);
        });

        passwordVisibleField.textProperty().bindBidirectional(passwordField.textProperty());
    }

    @FXML
    public void handleRegister(ActionEvent event) {
        String name = nameField.getText();
        String email = emailField.getText();
        String password = passwordField.getText();
        Role selectedRole = roleComboBox.getValue();

        if (name == null || name.trim().isEmpty() ||
                email == null || email.trim().isEmpty() ||
                password == null || password.trim().isEmpty() ||
                selectedRole == null) {
            showAlert(Alert.AlertType.WARNING, "Campi Incompleti",
                    "Per favore, compila tutti i campi e seleziona un ruolo.");
            return;
        }

        try {
            if (selectedRole == Role.DRIVER) {
                ConnectorType connectorType = connectorTypeComboBox.getValue();
                if (connectorType == null) {
                    showAlert(Alert.AlertType.WARNING, "Connettore mancante",
                            "Seleziona il tipo di connettore del tuo veicolo.");
                    return;
                }
                // La posizione iniziale la genera AuthService via FlorenceGeoSimulator
                authService.registerDriver(name, email, password, connectorType);
            } else {
                authService.register(name, email, password, selectedRole);
            }

            showAlert(Alert.AlertType.INFORMATION, "Successo",
                    "Registrazione completata! Ora puoi fare il login.");
            NavigationManager.navigateTo("LOGIN");

        } catch (DuplicateEmailException e) {
            showAlert(Alert.AlertType.ERROR, "Email già in uso", e.getMessage());
            emailField.setStyle("-fx-border-color: red;");
        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Errore di Sistema",
                    "Si è verificato un errore critico: " + e.getMessage());
        }
    }

    @FXML
    public void togglePasswordVisibility(MouseEvent event) {
        isPasswordVisible = !isPasswordVisible;
        passwordField.setVisible(!isPasswordVisible);
        passwordField.setManaged(!isPasswordVisible);
        passwordVisibleField.setVisible(isPasswordVisible);
        passwordVisibleField.setManaged(isPasswordVisible);

        try {
            String iconPath = isPasswordVisible ? "/it/unifi/ing/chargenet/presentation/images/hide.png" : "/it/unifi/ing/chargenet/presentation/images/view.png";
            Image newIcon = new Image(getClass().getResourceAsStream(iconPath));
            togglePasswordIcon.setImage(newIcon);
        } catch (Exception e) {
            System.err.println("Impossibile caricare l'icona: verifica resources/images/");
        }
    }

    @FXML
    public void goToLogin(ActionEvent event) {
        NavigationManager.navigateTo("LOGIN");
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}