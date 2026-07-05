package it.unifi.ing.chargenet.presentation.controller;

import it.unifi.ing.chargenet.business.services.SessionService;
import it.unifi.ing.chargenet.domain.users.StationOperator;
import it.unifi.ing.chargenet.domain.users.User;
import it.unifi.ing.chargenet.presentation.navigation.NavigationManager;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;

import java.math.BigDecimal;

public class FinancialReportsController {

    @FXML private Label payoutLabel;
    @FXML private Label sessionsLabel;
    @FXML private Label energyLabel;

    private StationOperator loggedOperator;
    private SessionService sessionService;

    @FXML
    public void initialize() {
        User current = NavigationManager.getCurrentUser();
        if (!(current instanceof StationOperator)) {
            NavigationManager.navigateTo("LOGIN");
            return;
        }
        this.loggedOperator = (StationOperator) current;
        this.sessionService = NavigationManager.getSessionService();

        loadFinancialData();
    }

    private void loadFinancialData() {
        try {
            // Revenue fresco: totalEarnings cresce a ogni ricarica chiusa
            User refreshed = NavigationManager.getAuthService().refreshUser(loggedOperator.getId());
            if (refreshed instanceof StationOperator op) {
                this.loggedOperator = op;
            }
            BigDecimal revenue = loggedOperator.getTotalEarnings() != null
                    ? loggedOperator.getTotalEarnings() : BigDecimal.ZERO;
            payoutLabel.setText(String.format("€ %.2f", revenue));

            double energy = sessionService.calculateTotalEnergySoldByOperator(loggedOperator);
            int sessions = sessionService.countSessionsByOperator(loggedOperator);
            energyLabel.setText(String.format("%.1f kWh", energy));
            sessionsLabel.setText(String.valueOf(sessions));
        } catch (Exception e) {
            e.printStackTrace();
            payoutLabel.setText("—");
            sessionsLabel.setText("—");
            energyLabel.setText("—");
        }
    }

    @FXML
    private void goBack(MouseEvent event) {
        NavigationManager.navigateTo("OPERATOR_MENU");
    }
}