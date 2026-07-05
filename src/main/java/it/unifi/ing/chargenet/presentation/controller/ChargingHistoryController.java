package it.unifi.ing.chargenet.presentation.controller;

import it.unifi.ing.chargenet.business.services.SessionService;
import it.unifi.ing.chargenet.domain.sessions.ChargingSession;
import it.unifi.ing.chargenet.domain.users.Driver;
import it.unifi.ing.chargenet.domain.users.User;
import it.unifi.ing.chargenet.presentation.navigation.NavigationManager;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ChargingHistoryController {

    @FXML private Label totalSpentLabel;
    @FXML private Label totalEnergyLabel;
    @FXML private VBox sessionsContainer;

    private Driver loggedDriver;
    private SessionService sessionService;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM • HH:mm");

    @FXML
    public void initialize() {
        User current = NavigationManager.getCurrentUser();
        if (!(current instanceof Driver)) {
            NavigationManager.navigateTo("LOGIN");
            return;
        }
        this.loggedDriver = (Driver) current;
        this.sessionService = NavigationManager.getSessionService();

        loadHistory();
    }

    private void loadHistory() {
        sessionsContainer.getChildren().clear();

        List<ChargingSession> sessions = sessionService.getSessionHistoryByDriver(loggedDriver);

        // Statistiche calcolate dalla lista stessa (nessuna query extra)
        BigDecimal totalSpent = BigDecimal.ZERO;
        double totalEnergy = 0.0;
        for (ChargingSession s : sessions) {
            if (s.getCostTotal() != null) totalSpent = totalSpent.add(s.getCostTotal());
            if (s.getKwhDelivered() != null) totalEnergy += s.getKwhDelivered();
        }
        totalSpentLabel.setText(String.format("€ %.2f", totalSpent));
        totalEnergyLabel.setText(String.format("%.1f kWh", totalEnergy));
        if (sessions.isEmpty()) {
            Label empty = new Label("Nessuna sessione completata.");
            empty.setStyle("-fx-text-fill: #7b6d6d; -fx-font-size: 14px;");
            sessionsContainer.getChildren().add(empty);
            return;
        }

        for (ChargingSession s : sessions) {
            sessionsContainer.getChildren().add(createSessionCard(s));
        }
    }

    private HBox createSessionCard(ChargingSession s) {
        HBox card = new HBox();
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPrefHeight(80);
        // niente padding a sinistra: la banda deve toccare il bordo
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-padding: 0 15 0 0; "
                + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.05), 5, 0, 0, 2);");

        // Banda arancione verticale, attaccata a sinistra, arrotondata solo sui due angoli sinistri
        Region accent = new Region();
        accent.setPrefWidth(6);
        accent.setMinWidth(6);
        accent.setMaxWidth(6);
        accent.setStyle("-fx-background-color: #f59100; -fx-background-radius: 10 0 0 10;");
        VBox.setVgrow(accent, Priority.ALWAYS);   // a tutta altezza
        HBox.setHgrow(accent, Priority.NEVER);

        // Nome stazione + data
        VBox info = new VBox();
        info.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(info, Priority.ALWAYS);
        HBox.setMargin(info, new javafx.geometry.Insets(0, 0, 0, 15));   // stacco dalla banda

        String stationName = (s.getStation() != null && s.getStation().getName() != null)
                ? s.getStation().getName() : "Stazione";
        Label name = new Label(stationName);
        name.setFont(Font.font("System", FontWeight.BOLD, 14));
        name.setStyle("-fx-text-fill: #131212;");

        String when = (s.getClosedAt() != null) ? s.getClosedAt().format(FMT)
                : (s.getOpenedAt() != null ? s.getOpenedAt().format(FMT) : "");
        Label date = new Label(when);
        date.setStyle("-fx-text-fill: #7b6d6d; -fx-font-size: 12px;");

        info.getChildren().addAll(name, date);

        // Costo + kWh
        VBox amount = new VBox();
        amount.setAlignment(Pos.CENTER_RIGHT);
        BigDecimal cost = s.getCostTotal() != null ? s.getCostTotal() : BigDecimal.ZERO;
        Label costLabel = new Label(String.format("€ %.2f", cost));
        costLabel.setFont(Font.font("System", FontWeight.BOLD, 15));
        costLabel.setStyle("-fx-text-fill: #131212;");

        double kwh = s.getKwhDelivered() != null ? s.getKwhDelivered() : 0.0;
        Label kwhLabel = new Label(String.format("%.1f kWh", kwh));
        kwhLabel.setStyle("-fx-text-fill: #f59100; -fx-font-weight: bold; -fx-font-size: 12px;");

        amount.getChildren().addAll(costLabel, kwhLabel);

        card.getChildren().addAll(accent, info, amount);
        return card;
    }

    @FXML
    private void goBack(MouseEvent event) {
        NavigationManager.navigateTo("DRIVER_MENU");
    }
}