package it.unifi.ing.chargenet.presentation.controller;

import it.unifi.ing.chargenet.business.services.WalletService;
import it.unifi.ing.chargenet.domain.users.Driver;
import it.unifi.ing.chargenet.domain.users.User;
import it.unifi.ing.chargenet.presentation.navigation.NavigationManager;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;

import java.math.BigDecimal;

public class FundWalletController {

    @FXML private Label currentBalanceLabel;
    @FXML private Button preset10, preset20, preset50;
    @FXML private TextField customAmountField;
    @FXML private Label totalToPayLabel;

    private Driver loggedDriver;
    private WalletService walletService;
    private BigDecimal selectedAmount = BigDecimal.ZERO;

    @FXML
    public void initialize() {
        User current = NavigationManager.getCurrentUser();
        if (!(current instanceof Driver)) {
            NavigationManager.navigateTo("LOGIN");
            return;
        }
        this.loggedDriver = (Driver) current;
        this.walletService = NavigationManager.getWalletService();

        showBalance(loggedDriver.getWalletBalance());

        // Digitando nel custom, si annulla la selezione preset e si aggiorna il totale
        customAmountField.textProperty().addListener((obs, old, text) -> {
            try {
                BigDecimal amt = new BigDecimal(text.trim());
                if (amt.compareTo(BigDecimal.ZERO) > 0) {
                    selectedAmount = amt;
                    clearPresetHighlight();
                    updateTotal();
                }
            } catch (Exception ignored) { /* input incompleto: ignora */ }
        });
    }

    @FXML private void selectPreset10(javafx.event.ActionEvent e) { setPreset(new BigDecimal("10"), preset10); }
    @FXML private void selectPreset20(javafx.event.ActionEvent e) { setPreset(new BigDecimal("20"), preset20); }
    @FXML private void selectPreset50(javafx.event.ActionEvent e) { setPreset(new BigDecimal("50"), preset50); }

    private void setPreset(BigDecimal amount, Button chosen) {
        selectedAmount = amount;
        customAmountField.clear();
        clearPresetHighlight();
        chosen.setStyle("-fx-background-color: #FFF3E0; -fx-border-color: #F59100; -fx-border-width: 2; "
                + "-fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand; -fx-text-fill: #f59100;");
        updateTotal();
    }

    private void clearPresetHighlight() {
        String plain = "-fx-background-color: white; -fx-border-color: #E0E0E0; -fx-border-radius: 8; "
                + "-fx-background-radius: 8; -fx-cursor: hand; -fx-text-fill: #131212;";
        preset10.setStyle(plain);
        preset20.setStyle(plain);
        preset50.setStyle(plain);
    }

    private void updateTotal() {
        totalToPayLabel.setText(String.format("€ %.2f", selectedAmount));
    }

    @FXML
    private void confirmAndPay(javafx.event.ActionEvent e) {
        if (selectedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            showAlert(Alert.AlertType.WARNING, "Importo mancante", "Seleziona o inserisci un importo maggiore di zero.");
            return;
        }
        try {
            walletService.fundWallet(loggedDriver, selectedAmount);

            // Anti-stantio: ricarica il driver fresco e aggiorna la sessione,
            // così il Driver Menu al ritorno mostra il saldo nuovo.
            User refreshed = NavigationManager.getAuthService().refreshUser(loggedDriver.getId());
            if (refreshed instanceof Driver d) {
                this.loggedDriver = d;
                NavigationManager.loginUser(d);
                showBalance(d.getWalletBalance());
            }

            showAlert(Alert.AlertType.INFORMATION, "Fondi aggiunti",
                    String.format("Hai aggiunto € %.2f al tuo wallet.", selectedAmount));
            NavigationManager.navigateTo("DRIVER_MENU");

        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Errore", "Impossibile aggiungere fondi: " + ex.getMessage());
        }
    }

    @FXML
    private void goBack(MouseEvent event) {
        NavigationManager.navigateTo("DRIVER_MENU");
    }

    private void showBalance(BigDecimal balance) {
        currentBalanceLabel.setText(String.format("€ %.2f", balance != null ? balance : BigDecimal.ZERO));
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}