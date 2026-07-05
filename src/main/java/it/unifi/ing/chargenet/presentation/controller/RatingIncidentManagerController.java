package it.unifi.ing.chargenet.presentation.controller;

import it.unifi.ing.chargenet.business.services.RatingService;
import it.unifi.ing.chargenet.domain.feedback.RatingAlert;
import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.users.Role;
import it.unifi.ing.chargenet.domain.users.User;
import it.unifi.ing.chargenet.presentation.navigation.NavigationManager;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class RatingIncidentManagerController {

    @FXML private Label openAlertsLabel;
    @FXML private VBox alertsContainer;

    private RatingService ratingService;

    @FXML
    public void initialize() {
        User current = NavigationManager.getCurrentUser();
        if (current == null || current.getRole() != Role.ENERGY_MANAGER) {
            System.err.println("[IncidentManager] Nessun Energy Manager in sessione, ritorno al login.");
            NavigationManager.navigateTo("LOGIN");
            return;
        }
        this.ratingService = NavigationManager.getRatingService();
        loadAlerts();
    }

    private void loadAlerts() {
        alertsContainer.getChildren().clear();
        try {
            List<RatingAlert> alerts = ratingService.getPendingAlerts();
            openAlertsLabel.setText(String.valueOf(alerts.size()));
            if (alerts.isEmpty()) {
                alertsContainer.getChildren().add(buildEmptyState());
                return;
            }
            for (RatingAlert a : alerts) {
                alertsContainer.getChildren().add(buildAlertCard(a));
            }
        } catch (Exception e) {
            openAlertsLabel.setText("—");
            showError("Caricamento alert fallito", rootMessage(e));
        }
    }

    // ----- costruzione card -----

    private VBox buildAlertCard(RatingAlert a) {
        VBox card = new VBox(10);
        card.getStyleClass().add("station-card");
        card.setPadding(new Insets(15));

        double avg = a.getAvgAtCreation();   // dato reale dell'alert, sempre presente

        // Riga: badge severità (derivato dalla media dell'alert) + tempo relativo
        Label badge = new Label(severityText(avg));
        badge.setStyle(severityStyle(avg));
        badge.setFont(Font.font("System Bold", 10));

        Region badgeSpacer = new Region();
        HBox.setHgrow(badgeSpacer, Priority.ALWAYS);

        Label when = new Label(relativeTime(a.getCreatedAt()));
        when.setStyle("-fx-text-fill: #7b6d6d;");
        when.setFont(Font.font(12));

        HBox topRow = new HBox(badge, badgeSpacer, when);
        topRow.setAlignment(Pos.CENTER_LEFT);

        // Nome stazione (idratato dal mapper)
        Label name = new Label(stationLabel(a));
        name.setStyle("-fx-text-fill: #131212;");
        name.setFont(Font.font("System Bold", 15));

        // Dato aggregato reale: media alla creazione dell'alert
        Label ratingLine = new Label(ratingSummary(avg));
        ratingLine.setStyle("-fx-text-fill: #7b6d6d;");
        ratingLine.setFont(Font.font(13));

        // Bottoni: le due risoluzioni reali del RatingService
        Button dismiss = new Button("Dismiss (False Alarm)");
        dismiss.setMaxWidth(Double.MAX_VALUE);
        dismiss.setPrefHeight(38);
        dismiss.setStyle("-fx-background-color: transparent; -fx-border-color: #131212; -fx-border-radius: 6; -fx-text-fill: #131212; -fx-cursor: hand;");
        dismiss.setFont(Font.font("System Bold", 13));
        dismiss.setOnAction(e -> onDismiss(a));
        HBox.setHgrow(dismiss, Priority.ALWAYS);

        Button suspend = new Button("Suspend Station");
        suspend.setMaxWidth(Double.MAX_VALUE);
        suspend.setPrefHeight(38);
        suspend.setStyle("-fx-background-color: #D32F2F; -fx-background-radius: 6; -fx-text-fill: white; -fx-cursor: hand;");
        suspend.setFont(Font.font("System Bold", 13));
        suspend.setOnAction(e -> onSuspend(a));
        HBox.setHgrow(suspend, Priority.ALWAYS);

        HBox actions = new HBox(10, dismiss, suspend);
        VBox.setMargin(actions, new Insets(5, 0, 0, 0));

        card.getChildren().addAll(topRow, name, ratingLine, actions);
        return card;
    }

    private Label buildEmptyState() {
        Label empty = new Label("No open rating incidents.");
        empty.setStyle("-fx-text-fill: #7b6d6d;");
        empty.setFont(Font.font(14));
        return empty;
    }

    // ----- helper di lettura -----

    private String stationLabel(RatingAlert a) {
        try {
            ChargingStation s = a.getStation();
            if (s != null && s.getName() != null && !s.getName().isBlank()) return s.getName();
            if (s != null) return "Station #" + s.getId();
        } catch (Exception ignored) { }
        return "—";
    }

    private String ratingSummary(double avg) {
        return "Media " + String.format(Locale.ITALY, "%.1f", avg) + " ★ (soglia di allerta: 2.0)";
    }

    private String relativeTime(LocalDateTime created) {
        if (created == null) return "";
        Duration d = Duration.between(created, LocalDateTime.now());
        long mins = d.toMinutes();
        if (mins < 1)  return "Just now";
        if (mins < 60) return mins + (mins == 1 ? " minute ago" : " minutes ago");
        long hours = d.toHours();
        if (hours < 24) return hours + (hours == 1 ? " hour ago" : " hours ago");
        long days = d.toDays();
        return days + (days == 1 ? " day ago" : " days ago");
    }

    private String severityText(double avg) {
        return avg < 1.5 ? "CRITICAL" : "WARNING";
    }

    private String severityStyle(double avg) {
        return avg < 1.5
                ? "-fx-background-color: #FFCDD2; -fx-text-fill: #D32F2F; -fx-padding: 2 6 2 6; -fx-background-radius: 4;"
                : "-fx-background-color: #FFF3E0; -fx-text-fill: #f59100; -fx-padding: 2 6 2 6; -fx-background-radius: 4;";
    }

    // ----- azioni -----

    private void onSuspend(RatingAlert a) {
        Optional<String> notes = askNotes("Sospendi stazione",
                "Sospendere \"" + stationLabel(a) + "\"? La colonnina andrà offline.");
        if (notes.isEmpty()) return;
        try {
            ratingService.suspendStationFromAlert(a, notes.get());
            loadAlerts();
        } catch (Exception e) {
            showError("Sospensione fallita", rootMessage(e));
        }
    }

    private void onDismiss(RatingAlert a) {
        Optional<String> notes = askNotes("Ignora alert",
                "Segnare l'alert di \"" + stationLabel(a) + "\" come falso allarme?");
        if (notes.isEmpty()) return;
        try {
            ratingService.dismissAlert(a, notes.get());
            loadAlerts();
        } catch (Exception e) {
            showError("Chiusura alert fallita", rootMessage(e));
        }
    }

    private Optional<String> askNotes(String title, String header) {
        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle(title);
        dlg.setHeaderText(header);
        dlg.setContentText("Note (opzionali):");
        dlg.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        Optional<String> r = dlg.showAndWait();
        return r.map(String::trim);   // presente (anche vuoto) = conferma; assente = annulla
    }

    @FXML
    private void goBack(MouseEvent event) {
        NavigationManager.navigateTo("MANAGER_MENU");
    }

    // ----- utilità -----

    private void showError(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setHeaderText(title);
        a.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        a.showAndWait();
    }

    private String rootMessage(Throwable e) {
        e.printStackTrace();
        Throwable root = e;
        while (root.getCause() != null) root = root.getCause();
        return root.getClass().getSimpleName() + " — " + root.getMessage();
    }
}