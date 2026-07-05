package it.unifi.ing.chargenet.presentation.controller;

import it.unifi.ing.chargenet.business.services.StationService;
import it.unifi.ing.chargenet.domain.users.Role;
import it.unifi.ing.chargenet.domain.users.User;
import it.unifi.ing.chargenet.presentation.navigation.NavigationManager;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;

public class EnergyManagerMenuController {

    @FXML private Label pendingLabel;
    @FXML private Label alertsLabel;

    private StationService stationService;

    @FXML
    public void initialize() {
        User current = NavigationManager.getCurrentUser();
        if (current == null || current.getRole() != Role.ENERGY_MANAGER) {
            System.err.println("[ManagerMenu] Nessun Energy Manager in sessione, ritorno al login.");
            NavigationManager.navigateTo("LOGIN");
            return;
        }
        this.stationService = NavigationManager.getStationService();
        loadDashboardData();
    }

    private void loadDashboardData() {
        try {
            int pending = stationService.getPendingStations().size();
            int alerts  = stationService.getOverloadedStations().size();
            pendingLabel.setText(String.valueOf(pending));
            alertsLabel.setText(String.valueOf(alerts));
        } catch (Exception e) {
            e.printStackTrace();
            pendingLabel.setText("—");
            alertsLabel.setText("—");
        }
    }

    @FXML
    private void goToApprovalQueue(MouseEvent event) {
        NavigationManager.navigateTo("APPROVAL_QUEUE");
    }

    @FXML
    private void goToRatingIncidents(MouseEvent event) {
        NavigationManager.navigateTo("RATING_INCIDENTS");
    }

    @FXML
    private void goToGridDashboard(MouseEvent event) {
        NavigationManager.navigateTo("RELIABILITY_DASHBOARD");
    }

    @FXML
    private void logOut() {
        NavigationManager.logout();
    }
}