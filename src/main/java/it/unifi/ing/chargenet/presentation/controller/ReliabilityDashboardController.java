package it.unifi.ing.chargenet.presentation.controller;

import it.unifi.ing.chargenet.business.core.GridCluster;
import it.unifi.ing.chargenet.domain.infrastructure.PowerTransformer;
import it.unifi.ing.chargenet.domain.users.Role;
import it.unifi.ing.chargenet.domain.users.User;
import it.unifi.ing.chargenet.presentation.navigation.NavigationManager;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.util.Duration;

import java.util.List;

public class ReliabilityDashboardController {

    @FXML private Label totalLoadLabel;
    @FXML private Label statusLabel;
    @FXML private VBox transformersContainer;

    private Timeline pollingTimeline;

    @FXML
    public void initialize() {
        User current = NavigationManager.getCurrentUser();
        if (current == null || current.getRole() != Role.ENERGY_MANAGER) {
            System.err.println("[ReliabilityDashboard] Nessun Energy Manager in sessione, ritorno al login.");
            NavigationManager.navigateTo("LOGIN");
            return;
        }
        refresh();                 // primo disegno immediato
        startPolling();            // poi aggiornamento periodico
    }

    private void startPolling() {
        pollingTimeline = new Timeline(new KeyFrame(Duration.seconds(2), e -> refresh()));
        pollingTimeline.setCycleCount(Animation.INDEFINITE);
        pollingTimeline.play();
    }

    private void refresh() {
        try {
            List<PowerTransformer> transformers = GridCluster.getInstance().getTransformers();

            transformersContainer.getChildren().clear();

            if (transformers == null || transformers.isEmpty()) {
                totalLoadLabel.setText("—");
                setStatus("No transformers in grid.", "#7b6d6d");
                transformersContainer.getChildren().add(buildEmptyState());
                return;
            }

            double avgLoad = transformers.stream()
                    .mapToDouble(PowerTransformer::getLoadPercent)
                    .average().orElse(0.0);
            long alerts = transformers.stream()
                    .filter(PowerTransformer::isInThermalAlert)
                    .count();

            totalLoadLabel.setText(Math.round(avgLoad) + "%");
            totalLoadLabel.setStyle("-fx-text-fill: " + loadColor(avgLoad, false) + ";");

            if (alerts > 0) {
                setStatus("Thermal alert on " + alerts + (alerts == 1 ? " transformer!" : " transformers!"), "#D32F2F");
            } else {
                setStatus("System operating within safe limits.", "#2E7D32");
            }

            for (PowerTransformer t : transformers) {
                transformersContainer.getChildren().add(buildTransformerCard(t));
            }
        } catch (Exception e) {
            e.printStackTrace();
            totalLoadLabel.setText("—");
            setStatus("Errore lettura stato rete.", "#D32F2F");
        }
    }

    // ----- costruzione card -----

    private VBox buildTransformerCard(PowerTransformer t) {
        boolean alert = t.isInThermalAlert();
        double load = t.getLoadPercent();
        String color = loadColor(load, alert);

        VBox card = new VBox(10);
        card.getStyleClass().add("station-card");
        card.setPadding(new Insets(15));
        if (alert) {
            card.setStyle("-fx-border-color: #FFCDD2;");
        }

        // header: nome + (badge THERMAL ALERT | percentuale)
        Label name = new Label(transformerLabel(t));
        name.setStyle("-fx-text-fill: #131212;");
        name.setFont(Font.font("System Bold", 14));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label right;
        if (alert) {
            right = new Label("THERMAL ALERT");
            right.setStyle("-fx-background-color: #FFCDD2; -fx-text-fill: #D32F2F; -fx-padding: 2 6 2 6; -fx-background-radius: 4;");
            right.setFont(Font.font("System Bold", 9));
        } else {
            right = new Label(Math.round(load) + "%");
            right.setStyle("-fx-text-fill: " + color + ";");
            right.setFont(Font.font("System Bold", 14));
        }

        HBox header = new HBox(name, spacer, right);
        header.setAlignment(Pos.CENTER_LEFT);

        // sottotitolo: numero colonnine sul trasformatore (dato reale dal cluster)
        int stationCount = GridCluster.getInstance().getStationsForTransformer(t.getId()).size();
        Label sub = new Label(stationCount + (stationCount == 1 ? " colonnina" : " colonnine")
                + " · " + Math.round(t.getTemperature()) + "°C");
        sub.setStyle("-fx-text-fill: #7b6d6d;");
        sub.setFont(Font.font(11));

        // barra di carico
        ProgressBar bar = new ProgressBar(Math.max(0.0, Math.min(1.0, load / 100.0)));
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setPrefHeight(10.0);
        bar.setStyle("-fx-accent: " + color + ";");

        card.getChildren().addAll(header, sub, bar);
        return card;
    }

    private Label buildEmptyState() {
        Label empty = new Label("La griglia non contiene trasformatori.");
        empty.setStyle("-fx-text-fill: #7b6d6d;");
        empty.setFont(Font.font(14));
        return empty;
    }

    // ----- helper -----

    private String transformerLabel(PowerTransformer t) {
        if (t.getName() != null && !t.getName().isBlank()) return t.getName();
        return "Transformer #" + t.getId();
    }

    /** Rosso se in alert termico o carico alto, arancio se medio, verde se basso. */
    private String loadColor(double load, boolean alert) {
        if (alert || load >= 90.0) return "#D32F2F";
        if (load >= 70.0)          return "#f59100";
        return "#2E7D32";
    }

    private void setStatus(String text, String colorHex) {
        statusLabel.setText(text);
        statusLabel.setStyle("-fx-text-fill: " + colorHex + ";");
    }

    @FXML
    private void goBack(MouseEvent event) {
        if (pollingTimeline != null) {
            pollingTimeline.stop();   // ferma il polling prima di lasciare la schermata
        }
        NavigationManager.navigateTo("MANAGER_MENU");
    }
}