package it.unifi.ing.chargenet.presentation.controller;

import it.unifi.ing.chargenet.business.services.StationService;
import it.unifi.ing.chargenet.business.services.ValidationService;
import it.unifi.ing.chargenet.domain.infrastructure.ChargingStation;
import it.unifi.ing.chargenet.domain.users.ConnectorType;
import it.unifi.ing.chargenet.domain.users.Role;
import it.unifi.ing.chargenet.domain.users.StationOperator;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public class ApprovalQueueController {

    @FXML private Label countLabel;
    @FXML private VBox queueContainer;

    private StationService stationService;
    private ValidationService validationService;

    @FXML
    public void initialize() {
        User current = NavigationManager.getCurrentUser();
        if (current == null || current.getRole() != Role.ENERGY_MANAGER) {
            System.err.println("[ApprovalQueue] Nessun Energy Manager in sessione, ritorno al login.");
            NavigationManager.navigateTo("LOGIN");
            return;
        }
        this.stationService = NavigationManager.getStationService();
        this.validationService = NavigationManager.getValidationService();
        loadQueue();
    }

    private void loadQueue() {
        queueContainer.getChildren().clear();
        try {
            List<ChargingStation> pending = stationService.getPendingStations();
            countLabel.setText(String.valueOf(pending.size()));
            if (pending.isEmpty()) {
                queueContainer.getChildren().add(buildEmptyState());
                return;
            }
            for (ChargingStation s : pending) {
                queueContainer.getChildren().add(buildStationCard(s));
            }
        } catch (Exception e) {
            countLabel.setText("—");
            showError("Caricamento coda fallito", rootMessage(e));
        }
    }

    // ----- costruzione card -----

    private VBox buildStationCard(ChargingStation s) {
        VBox card = new VBox(12);
        card.getStyleClass().add("station-card");
        card.setPadding(new Insets(15));

        Label name = new Label(s.getName());
        name.setStyle("-fx-text-fill: #131212;");
        name.setFont(Font.font("System Bold", 16));

        Label requestedBy = new Label("Requested by: " + operatorLabel(s));
        requestedBy.setStyle("-fx-text-fill: #7b6d6d;");
        requestedBy.setFont(Font.font(12));

        VBox nameBox = new VBox(name, requestedBy);

        Label badge = new Label("PENDING");
        badge.setStyle("-fx-background-color: #FFF3E0; -fx-text-fill: #f59100; -fx-padding: 2 6 2 6; -fx-background-radius: 4;");
        badge.setFont(Font.font("System Bold", 10));

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox header = new HBox(nameBox, headerSpacer, badge);
        header.setAlignment(Pos.CENTER_LEFT);

        HBox stats = new HBox(15,
                statCell("Max Power", s.getPowerKw().intValue() + " kW"),
                statCell("Connector", connectorLabel(s.getConnectorType())));
        stats.setStyle("-fx-background-color: #F8F9FA; -fx-padding: 10; -fx-background-radius: 6;");

        Button reject = new Button("Reject");
        reject.setMaxWidth(Double.MAX_VALUE);
        reject.setPrefHeight(40);
        reject.setStyle("-fx-background-color: white; -fx-border-color: #D32F2F; -fx-border-radius: 6; -fx-text-fill: #d32f2f; -fx-cursor: hand;");
        reject.setFont(Font.font("System Bold", 13));
        reject.setOnAction(e -> onReject(s));
        HBox.setHgrow(reject, Priority.ALWAYS);

        Button approve = new Button("Approve");
        approve.setMaxWidth(Double.MAX_VALUE);
        approve.setPrefHeight(40);
        approve.setStyle("-fx-background-color: #2E7D32; -fx-background-radius: 6; -fx-text-fill: white; -fx-cursor: hand;");
        approve.setFont(Font.font("System Bold", 13));
        approve.setOnAction(e -> onApprove(s));
        HBox.setHgrow(approve, Priority.ALWAYS);

        HBox actions = new HBox(10, reject, approve);
        VBox.setMargin(actions, new Insets(5, 0, 0, 0));

        card.getChildren().addAll(header, stats, actions);
        return card;
    }

    private VBox statCell(String caption, String value) {
        Label c = new Label(caption);
        c.setStyle("-fx-text-fill: #7b6d6d;");
        c.setFont(Font.font(11));
        Label v = new Label(value);
        v.setStyle("-fx-text-fill: #131212;");
        v.setFont(Font.font("System Bold", 13));
        VBox box = new VBox(c, v);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    private Label buildEmptyState() {
        Label empty = new Label("No stations awaiting approval.");
        empty.setStyle("-fx-text-fill: #7b6d6d;");
        empty.setFont(Font.font(14));
        return empty;
    }

    private String operatorLabel(ChargingStation s) {
        try {
            StationOperator op = s.getOperator();
            if (op != null && op.getName() != null && !op.getName().isBlank()) return op.getName();
            if (op != null) return "Operator #" + op.getId();
        } catch (Exception ignored) { }
        return "—";
    }

    private String connectorLabel(ConnectorType t) {
        if (t == null) return "—";
        switch (t) {
            case TYPE_2: return "Type 2";
            case CCS_2:  return "CCS2";
            default:     return t.name();
        }
    }

    // ----- azioni: via ValidationService -----

    private void onApprove(ChargingStation s) {
        Optional<BigDecimal> tariff = askPlatformTariff(s);
        if (tariff.isEmpty()) return;   // annullato o input non valido (già segnalato)
        try {
            validationService.approve(s, tariff.get());
            loadQueue();
        } catch (Exception e) {
            showError("Approvazione fallita", rootMessage(e));
        }
    }

    private void onReject(ChargingStation s) {
        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle("Rifiuta colonnina");
        dlg.setHeaderText("Motivazione del rifiuto per \"" + s.getName() + "\"");
        dlg.setContentText("Motivo:");
        dlg.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        Optional<String> res = dlg.showAndWait();
        if (res.isEmpty()) return;
        String motivation = res.get().trim();
        if (motivation.isEmpty()) {
            showError("Motivazione mancante", "Il rifiuto richiede una motivazione.");
            return;
        }
        try {
            validationService.reject(s, motivation);
            loadQueue();
        } catch (Exception e) {
            showError("Rifiuto fallito", rootMessage(e));
        }
    }

    private Optional<BigDecimal> askPlatformTariff(ChargingStation s) {
        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle("Approva colonnina");
        dlg.setHeaderText("Imposta la tariffa piattaforma per \"" + s.getName() + "\"");
        dlg.setContentText("€ / kWh:");
        dlg.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        Optional<String> res = dlg.showAndWait();
        if (res.isEmpty()) return Optional.empty();
        try {
            BigDecimal t = new BigDecimal(res.get().trim().replace(",", "."));
            if (t.compareTo(BigDecimal.ZERO) < 0) {
                showError("Tariffa non valida", "La tariffa non può essere negativa.");
                return Optional.empty();
            }
            return Optional.of(t);
        } catch (NumberFormatException ex) {
            showError("Tariffa non valida", "Inserisci un numero, es. 0.15");
            return Optional.empty();
        }
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