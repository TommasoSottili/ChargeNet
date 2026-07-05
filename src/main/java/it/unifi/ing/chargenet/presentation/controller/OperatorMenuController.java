package it.unifi.ing.chargenet.presentation.controller;

import it.unifi.ing.chargenet.domain.users.StationOperator;
import it.unifi.ing.chargenet.domain.users.User;
import it.unifi.ing.chargenet.presentation.navigation.NavigationManager;

import javafx.fxml.FXML;

public class OperatorMenuController {

    private StationOperator loggedOperator;

    @FXML
    public void initialize() {
        User current = NavigationManager.getCurrentUser();
        if (!(current instanceof StationOperator)) {
            System.err.println("[OperatorMenu] Nessun operatore in sessione, ritorno al login.");
            NavigationManager.navigateTo("LOGIN");
            return;
        }
        this.loggedOperator = (StationOperator) current;
    }

    @FXML
    private void goToHub() {
        NavigationManager.navigateTo("STATION_HUB");
    }

    @FXML
    private void goToReports() {
        NavigationManager.navigateTo("FINANCIAL_REPORTS");
    }

    @FXML
    private void logOut() {
        NavigationManager.logout();
    }
}