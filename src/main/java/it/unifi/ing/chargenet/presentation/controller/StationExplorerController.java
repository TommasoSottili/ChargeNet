package it.unifi.ing.chargenet.presentation.controller;

import it.unifi.ing.chargenet.business.services.StationService;
import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.users.Driver;
import it.unifi.ing.chargenet.domain.users.User;
import it.unifi.ing.chargenet.domain.infrastructure.StationStatus;
import it.unifi.ing.chargenet.presentation.navigation.NavigationManager;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

public class StationExplorerController {

    @FXML private VBox stationsContainer;

    private Driver loggedDriver;
    private StationService stationService;

    @FXML
    public void initialize() {
        User current = NavigationManager.getCurrentUser();
        if (!(current instanceof Driver)) {
            System.err.println("[StationExplorer] Nessun Driver in sessione, ritorno al login.");
            NavigationManager.navigateTo("LOGIN");
            return;
        }
        this.loggedDriver = (Driver) current;
        this.stationService = NavigationManager.getStationService();

        loadStations();
    }

    public void initData(Driver driver) {
        this.loggedDriver = driver;
    }

    private void loadStations() {
        stationsContainer.getChildren().clear();
        try {
            // Solo colonnine ACTIVE + compatibili col connettore del driver,
            // ordinate per distanza (la più vicina in cima). Il filtro vive nel DAO.
            List<ChargingStation> stations = stationService.findNearestAvailable(loggedDriver);

            if (stations.isEmpty()) {
                Label empty = new Label("Nessuna colonnina compatibile e disponibile vicino a te.");
                empty.setStyle("-fx-text-fill: #7b6d6d; -fx-font-size: 14px;");
                stationsContainer.getChildren().add(empty);
                return;
            }

            for (ChargingStation station : stations) {
                stationsContainer.getChildren().add(createStationCard(station));
            }
        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Errore",
                    "Impossibile caricare le colonnine: " + e.getMessage());
        }
    }

    private VBox createStationCard(ChargingStation station) {
        VBox card = new VBox();
        card.getStyleClass().add("station-card");
        card.setPadding(new Insets(15));

        boolean isReservedByMe = station.getStatus() == StationStatus.RESERVED;

        // --- HEADER: potenza + info ---
        HBox header = new HBox(15);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox powerBox = new VBox();
        powerBox.setAlignment(Pos.CENTER);
        powerBox.setPrefSize(55, 50);
        boolean isFast = station.getPowerKw() >= 100;
        powerBox.setStyle("-fx-background-color: " + (isFast ? "#FFF3E0" : "#E3F2FD") + "; -fx-background-radius: 8;");
        Label powerLabel = new Label(String.valueOf(station.getPowerKw().intValue()));
        powerLabel.setStyle("-fx-text-fill: " + (isFast ? "#f59100" : "#1565C0") + "; -fx-font-weight: bold; -fx-font-size: 18px;");
        Label kwLabel = new Label("kW");
        kwLabel.setStyle("-fx-text-fill: " + (isFast ? "#f59100" : "#1565C0") + "; -fx-font-weight: bold; -fx-font-size: 10px;");
        powerBox.getChildren().addAll(powerLabel, kwLabel);

        VBox textBox = new VBox(2);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        HBox titleRow = new HBox();
        titleRow.setAlignment(Pos.CENTER_LEFT);
        String nome = (station.getName() != null && !station.getName().isEmpty()) ? station.getName() : "Nome Mancante";
        Label titleLabel = new Label(nome);
        titleLabel.setStyle("-fx-text-fill: #131212; -fx-font-weight: bold; -fx-font-size: 16px;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Badge stato: PRENOTATA se riservata da me, altrimenti la distanza
        if (isReservedByMe) {
            Label reservedBadge = new Label("PRENOTATA");
            reservedBadge.setStyle("-fx-background-color: #E3F2FD; -fx-text-fill: #1565C0; "
                    + "-fx-padding: 3 10 3 10; -fx-background-radius: 10; -fx-font-weight: bold; -fx-font-size: 11px;");
            titleRow.getChildren().addAll(titleLabel, spacer, reservedBadge);
        } else {
            Label distanceLabel = new Label(formatDistance(station));
            distanceLabel.setStyle("-fx-text-fill: #7b6d6d; -fx-font-weight: bold; -fx-font-size: 12px;");
            titleRow.getChildren().addAll(titleLabel, spacer, distanceLabel);
        }

        String indirizzo = (station.getAddress() != null && !station.getAddress().isEmpty()) ? station.getAddress() : "Indirizzo Sconosciuto";
        Label addressLabel = new Label(indirizzo);
        addressLabel.setStyle("-fx-text-fill: #7b6d6d; -fx-font-size: 12px;");

        Label connectorLabel = new Label("🔌 " + station.getConnectorType().name() + " · compatibile");
        connectorLabel.setStyle("-fx-text-fill: #2E7D32; -fx-font-size: 12px; -fx-font-weight: bold;");

        Label ratingLabel;
        if (station.getTotalRatings() != null && station.getTotalRatings() > 0) {
            double avg = station.getAverageRating() != null ? station.getAverageRating() : 0.0;
            ratingLabel = new Label(String.format("⭐ %.1f (%d)", avg, station.getTotalRatings()));
            ratingLabel.setStyle("-fx-text-fill: #7b6d6d; -fx-font-size: 12px; -fx-font-weight: bold;");
        } else {
            ratingLabel = new Label("⭐ Nessuna recensione");
            ratingLabel.setStyle("-fx-text-fill: #B0B0B0; -fx-font-size: 12px;");
        }

        textBox.getChildren().addAll(titleRow, addressLabel, connectorLabel, ratingLabel);
        header.getChildren().addAll(powerBox, textBox);

        // --- BOTTONI ---
        HBox buttonsBox = new HBox(10);
        buttonsBox.setPadding(new Insets(15, 0, 0, 0));

        Button chargeBtn = new Button("Charge Now");
        chargeBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(chargeBtn, Priority.ALWAYS);
        chargeBtn.setPrefHeight(40);
        chargeBtn.getStyleClass().add("btn-smart-book");
        chargeBtn.setOnAction(e -> handleChargeNow(station));
        buttonsBox.getChildren().add(chargeBtn);

        if (!isReservedByMe) {
            // Smart Book solo se NON è già prenotata da me
            Button bookBtn = new Button("Smart Book");
            bookBtn.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(bookBtn, Priority.ALWAYS);
            bookBtn.setPrefHeight(40);
            bookBtn.getStyleClass().add("btn-smart-book");
            bookBtn.setOnAction(e -> handleSmartBook(station));
            buttonsBox.getChildren().add(bookBtn);
        }

        card.getChildren().addAll(header, buttonsBox);
        return card;
    }

    /** Distanza euclidea Driver→Station (approx km), coerente con l'ordinamento del DAO. Solo display. */
    private String formatDistance(ChargingStation station) {
        if (loggedDriver == null || loggedDriver.getLatitude() == null || loggedDriver.getLongitude() == null
                || station.getLatitude() == null || station.getLongitude() == null) {
            return "";
        }
        double dLat = station.getLatitude() - loggedDriver.getLatitude();
        double dLng = station.getLongitude() - loggedDriver.getLongitude();
        double approxKm = Math.sqrt(dLat * dLat + dLng * dLng) * 111.0; // ~111 km per grado
        return String.format("%.1f km", approxKm);
    }

    private void handleChargeNow(ChargingStation station) {
        LiveSessionController controller = NavigationManager.navigateToWithController("LIVE_SESSION");
        if (controller != null) {
            controller.initData(loggedDriver, station);
        }
    }

    private void handleSmartBook(ChargingStation station) {
        try {
            stationService.hold(loggedDriver, station);
            showAlert(Alert.AlertType.INFORMATION, "Prenotata",
                    "Colonnina " + station.getName() + " bloccata per 15 minuti.");
            loadStations();
        } catch (Exception e) {
            showAlert(Alert.AlertType.WARNING, "Non disponibile", e.getMessage());
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