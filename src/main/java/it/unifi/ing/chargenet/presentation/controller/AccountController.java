package it.unifi.ing.chargenet.presentation.controller;

import it.unifi.ing.chargenet.business.services.WalletService;
import it.unifi.ing.chargenet.domain.users.Driver;
import it.unifi.ing.chargenet.domain.users.SubscriptionPlan;
import it.unifi.ing.chargenet.domain.users.User;
import it.unifi.ing.chargenet.presentation.navigation.NavigationManager;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;

public class AccountController {

    @FXML private Label walletLabel;
    @FXML private Label connectorLabel;
    @FXML private Label batteryCapacityLabel;
    @FXML private Label currentPlanBadge;
    @FXML private Label emailLabel;
    @FXML private VBox planBasicCard, planPlusCard, planPremiumCard;
    @FXML private Label planBasicDesc, planPlusDesc, planPremiumDesc;
    @FXML private Button applyPlanBtn;

    private Driver loggedDriver;
    private WalletService walletService;
    private SubscriptionPlan selectedPlan;   // piano scelto ma non ancora applicato

    @FXML
    public void initialize() {
        User current = NavigationManager.getCurrentUser();
        if (!(current instanceof Driver)) {
            NavigationManager.navigateTo("LOGIN");
            return;
        }
        this.loggedDriver = (Driver) current;
        this.walletService = NavigationManager.getWalletService();

        // Descrizioni piani dai dati reali dell'enum
        planBasicDesc.setText(planDescription(SubscriptionPlan.BASIC));
        planPlusDesc.setText(planDescription(SubscriptionPlan.PLUS));
        planPremiumDesc.setText(planDescription(SubscriptionPlan.PREMIUM));

        renderFromDriver();
    }

    private void renderFromDriver() {
        walletLabel.setText(String.format("€ %.2f",
                loggedDriver.getWalletBalance() != null ? loggedDriver.getWalletBalance() : BigDecimal.ZERO));

        connectorLabel.setText("Connector: " +
                (loggedDriver.getConnectorType() != null ? loggedDriver.getConnectorType().name() : "—"));
        batteryCapacityLabel.setText("Battery capacity: " +
                (loggedDriver.getBatteryCapacity() != null ? loggedDriver.getBatteryCapacity() + " kWh" : "—"));

        emailLabel.setText(loggedDriver.getEmail());

        SubscriptionPlan currentPlan = loggedDriver.getSubscriptionPlan();
        currentPlanBadge.setText("CURRENT: " + (currentPlan != null ? currentPlan.name() : "—"));

        // Nessuna selezione pendente all'ingresso
        this.selectedPlan = null;
        applyPlanBtn.setDisable(true);
        highlightSelection(currentPlan);   // evidenzia il piano attuale
    }

    private String planDescription(SubscriptionPlan plan) {
        // Sconto e fee dai metodi "ricchi" dell'enum
        int discountPct = (int) Math.round(plan.getDiscount() * 100);
        BigDecimal fee = plan.getMonthlyFee();
        if (fee.compareTo(BigDecimal.ZERO) == 0) {
            return "No monthly fee · " + discountPct + "% platform discount";
        }
        return String.format("€ %.0f/month · %d%% platform discount", fee, discountPct);
    }

    @FXML private void selectBasic(MouseEvent e)   { choose(SubscriptionPlan.BASIC); }
    @FXML private void selectPlus(MouseEvent e)    { choose(SubscriptionPlan.PLUS); }
    @FXML private void selectPremium(MouseEvent e) { choose(SubscriptionPlan.PREMIUM); }

    private void choose(SubscriptionPlan plan) {
        if (plan == loggedDriver.getSubscriptionPlan()) {
            // scegliere il piano attuale = nessun cambio
            this.selectedPlan = null;
            applyPlanBtn.setDisable(true);
        } else {
            this.selectedPlan = plan;
            applyPlanBtn.setDisable(false);
        }
        highlightSelection(plan);
    }

    private void highlightSelection(SubscriptionPlan plan) {
        style(planBasicCard,   plan == SubscriptionPlan.BASIC);
        style(planPlusCard,    plan == SubscriptionPlan.PLUS);
        style(planPremiumCard, plan == SubscriptionPlan.PREMIUM);
    }

    private void style(VBox card, boolean selected) {
        String border = selected ? "#F59100" : "#E0E0E0";
        String bg     = selected ? "#FFF3E0" : "#F8F9FA";
        card.setStyle("-fx-cursor: hand; -fx-background-color: " + bg + "; -fx-border-color: " + border
                + "; -fx-border-width: 2; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 12;");
    }

    @FXML
    private void applyPlanChange() {
        if (selectedPlan == null) return;
        try {
            walletService.changePlan(loggedDriver, selectedPlan);

            // Anti-stantio: piano E saldo sono cambiati → ricarica fresco
            User refreshed = NavigationManager.getAuthService().refreshUser(loggedDriver.getId());
            if (refreshed instanceof Driver d) {
                this.loggedDriver = d;
                NavigationManager.loginUser(d);
            }
            renderFromDriver();
            showAlert(Alert.AlertType.INFORMATION, "Piano aggiornato",
                    "Il tuo piano è ora " + loggedDriver.getSubscriptionPlan().name() + ".");

        } catch (IllegalStateException e) {
            // Fondi insufficienti per la fee del piano
            showAlert(Alert.AlertType.WARNING, "Fondi insufficienti", e.getMessage());
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Errore", "Impossibile cambiare piano: " + e.getMessage());
        }
    }

    @FXML
    private void goBack(MouseEvent event) {
        NavigationManager.navigateTo("DRIVER_MENU");
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}