package it.unifi.ing.chargenet.presentation.controller;

import it.unifi.ing.chargenet.business.services.StationService;
import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.infrastructure.StationStatus;
import it.unifi.ing.chargenet.domain.users.StationOperator;
import it.unifi.ing.chargenet.domain.users.User;
import it.unifi.ing.chargenet.presentation.navigation.NavigationManager;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

public class StationHubController {

    @FXML private Label networkStatusLabel;
    @FXML private VBox stationsContainer;

    private StationOperator loggedOperator;
    private StationService stationService;

    @FXML
    public void initialize() {
        User current = NavigationManager.getCurrentUser();
        if (!(current instanceof StationOperator)) {
            System.err.println("[StationHub] Nessun operatore in sessione, ritorno al login.");
            NavigationManager.navigateTo("LOGIN");
            return;
        }
        this.loggedOperator = (StationOperator) current;
        this.stationService = NavigationManager.getStationService();

        loadOperatorStations();
    }

    private void loadOperatorStations() {
        stationsContainer.getChildren().clear();
        try {
            List<ChargingStation> myStations = stationService.getStationsByOperator(loggedOperator);

            // Network status calcolato: online = ACTIVE / BUSY / RESERVED (operativa)
            long online = myStations.stream().filter(s ->
                    s.getStatus() == StationStatus.ACTIVE
                            || s.getStatus() == StationStatus.BUSY
                            || s.getStatus() == StationStatus.RESERVED).count();
            int pct = myStations.isEmpty() ? 100 : (int) Math.round(online * 100.0 / myStations.size());
            networkStatusLabel.setText(myStations.size() + " Stations • " + pct + "% Online");

            if (myStations.isEmpty()) {
                Label empty = new Label("Non hai ancora registrato colonnine.");
                empty.setStyle("-fx-text-fill: #7b6d6d; -fx-font-size: 14px;");
                stationsContainer.getChildren().add(empty);
                return;
            }

            for (ChargingStation station : myStations) {
                stationsContainer.getChildren().add(createOperatorStationCard(station));
            }
        } catch (Exception e) {
            e.printStackTrace();
            networkStatusLabel.setText("Errore nel caricamento");
        }
    }

    private VBox createOperatorStationCard(ChargingStation station) {
        VBox card = new VBox(15);
        card.setPadding(new Insets(15));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #E0E0E0; -fx-border-radius: 8;");

        // RIGA 1: nome + badge stato reale
        HBox topRow = new HBox();
        topRow.setAlignment(Pos.CENTER_LEFT);
        Label nameLabel = new Label(station.getName());
        nameLabel.setStyle("-fx-text-fill: #131212; -fx-font-weight: bold; -fx-font-size: 16px;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        StationStatus st = station.getStatus();
        String bg, fg;
        if (st == StationStatus.ACTIVE) { bg = "#E8F5E9"; fg = "#2E7D32"; }
        else if (st == StationStatus.OVERLOADED) { bg = "#FFEBEE"; fg = "#D32F2F"; }
        else { bg = "#FFF3E0"; fg = "#f59100"; }   // BUSY, RESERVED, PENDING, ecc.
        Label statusBadge = new Label(st.name());
        statusBadge.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: " + fg
                + "; -fx-padding: 2 8 2 8; -fx-background-radius: 4; -fx-font-weight: bold; -fx-font-size: 10px;");
        topRow.getChildren().addAll(nameLabel, spacer, statusBadge);

        // RIGA 2: dati tecnici reali (niente più "Manage" finto)
        HBox bottomRow = new HBox(20);
        bottomRow.setAlignment(Pos.CENTER_LEFT);

        VBox col1 = new VBox(2);
        Label lbl1 = new Label("Connector");
        lbl1.setStyle("-fx-text-fill: #7b6d6d; -fx-font-size: 11px;");
        Label val1 = new Label(station.getConnectorType() != null ? station.getConnectorType().name() : "N/A");
        val1.setStyle("-fx-text-fill: #131212; -fx-font-weight: bold; -fx-font-size: 14px;");
        col1.getChildren().addAll(lbl1, val1);

        VBox col2 = new VBox(2);
        Label lbl2 = new Label("Power");
        lbl2.setStyle("-fx-text-fill: #7b6d6d; -fx-font-size: 11px;");
        Label val2 = new Label(station.getPowerKw().intValue() + " kW");
        val2.setStyle("-fx-text-fill: #131212; -fx-font-weight: bold; -fx-font-size: 14px;");
        col2.getChildren().addAll(lbl2, val2);

        bottomRow.getChildren().addAll(col1, col2);

        card.getChildren().addAll(topRow, bottomRow);
        return card;
    }

    @FXML
    private void goToOnboard(MouseEvent event) {
        NavigationManager.navigateTo("NEW_STATION");
    }

    @FXML
    private void goBack(MouseEvent event) {
        NavigationManager.navigateTo("OPERATOR_MENU");
    }
}