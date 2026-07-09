package it.unifi.ing.chargenet.presentation.navigation;

import it.unifi.ing.chargenet.dao.interfaces.DaoFactory;
import it.unifi.ing.chargenet.domain.users.User;
import it.unifi.ing.chargenet.business.services.AuthService;
import it.unifi.ing.chargenet.business.services.SessionService;
import it.unifi.ing.chargenet.business.services.StationService;
import it.unifi.ing.chargenet.business.services.RatingService;
import it.unifi.ing.chargenet.business.services.WalletService;
import it.unifi.ing.chargenet.business.services.ValidationService;
import it.unifi.ing.chargenet.business.services.DriverService;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class NavigationManager {

    private static Stage mainStage;

    // --- AGGIUNTE FONDAMENTALI PER L'ARCHITETTURA ---
    private static DaoFactory daoFactory;
    private static User currentUser;

    private static final Map<String, String> FXML_MAP = new HashMap<>();

    static {
        FXML_MAP.put("LOGIN", "/it/unifi/ing/chargenet/presentation/view/Login.fxml");
        FXML_MAP.put("REGISTER", "/it/unifi/ing/chargenet/presentation/view/Registration.fxml");
        FXML_MAP.put("DRIVER_MENU", "/it/unifi/ing/chargenet/presentation/view/DriverMenu.fxml");
        FXML_MAP.put("STATION_EXPLORER", "/it/unifi/ing/chargenet/presentation/view/StationExplorer.fxml");
        FXML_MAP.put("LIVE_SESSION", "/it/unifi/ing/chargenet/presentation/view/LiveSession.fxml");
        FXML_MAP.put("OPERATOR_MENU", "/it/unifi/ing/chargenet/presentation/view/StationOperatorMenu.fxml");
        FXML_MAP.put("NEW_STATION", "/it/unifi/ing/chargenet/presentation/view/StationOnboardingSO.fxml");
        FXML_MAP.put("STATION_HUB", "/it/unifi/ing/chargenet/presentation/view/StationManagementSO.fxml");
        FXML_MAP.put("FINANCIAL_REPORTS", "/it/unifi/ing/chargenet/presentation/view/FinancialReportsSO.fxml");
        FXML_MAP.put("FUND_WALLET", "/it/unifi/ing/chargenet/presentation/view/FundWallet.fxml");
        FXML_MAP.put("ACCOUNT", "/it/unifi/ing/chargenet/presentation/view/AccountSubscription.fxml");
        FXML_MAP.put("HISTORY", "/it/unifi/ing/chargenet/presentation/view/ChargingHistory.fxml");
        FXML_MAP.put("MANAGER_MENU", "/it/unifi/ing/chargenet/presentation/view/EnergyManagerMenu.fxml");
        FXML_MAP.put("APPROVAL_QUEUE", "/it/unifi/ing/chargenet/presentation/view/ApprovalQueue.fxml");
        FXML_MAP.put("RATING_INCIDENTS", "/it/unifi/ing/chargenet/presentation/view/RatingIncidentManagerEM.fxml");
        FXML_MAP.put("RELIABILITY_DASHBOARD", "/it/unifi/ing/chargenet/presentation/view/ReliabilityDashboardEM.fxml");
    }

    /**
     * Setup iniziale, chiamato nell'AppLauncher
     */
    public static void initialize(Stage stage, DaoFactory factory) {
        mainStage = stage;
        daoFactory = factory;
    }

    // --- METODI PER GESTIRE I DATI (SESSIONE E DB) ---
    public static DaoFactory getDaoFactory() {
        return daoFactory;
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static void loginUser(User user) {
        currentUser = user;
    }

    public static void logout() {
        currentUser = null;
        navigateTo("LOGIN");
    }

    // --- I TUOI METODI DI NAVIGAZIONE (Invariati) ---
    public static void navigateTo(String viewName) {
        navigateToWithController(viewName);
    }

    public static <T> T navigateToWithController(String viewName) {
        // ... Il tuo codice originale per caricare la scena ...
        // (Controlli su mainStage, FXML_MAP, try-catch, loader.load(), setScene, ecc.)
        if (mainStage == null) {
            throw new IllegalStateException("NavigationManager: Il mainStage non è stato inizializzato!");
        }

        String fxmlPath = FXML_MAP.get(viewName);
        if (fxmlPath == null) {
            throw new IllegalArgumentException("NavigationManager: Nessun percorso registrato per la vista -> " + viewName);
        }

        try {
            URL resource = NavigationManager.class.getResource(fxmlPath);
            if (resource == null) {
                throw new RuntimeException("File FXML non trovato fisicamente al percorso: " + fxmlPath);
            }

            FXMLLoader loader = new FXMLLoader(resource);
            Parent root = loader.load();
            Scene scene = new Scene(root);

            // Aggancia il CSS condiviso a OGNI schermata, una volta sola
            URL css = NavigationManager.class.getResource(
                    "/it/unifi/ing/chargenet/presentation/css/style.css");
            if (css != null) {
                scene.getStylesheets().add(css.toExternalForm());
            } else {
                System.err.println("[NavigationManager] chargenet.css non trovato nel classpath!");
            }

            mainStage.setScene(scene);
            mainStage.centerOnScreen();
            mainStage.show();

            return loader.getController();

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Errore critico durante il caricamento della view: " + viewName, e);
        }
    }

    // --- SERVICE LOCATOR: punto unico di costruzione dei service ---
// I controller chiedono i service qui invece di fare "new XxxService(...)".

    public static AuthService getAuthService() {
        return new AuthService(daoFactory);
    }

    public static SessionService getSessionService() {
        return new SessionService(daoFactory);
    }

    public static StationService getStationService() {
        return new StationService(daoFactory);
    }

    public static RatingService getRatingService() {
        return new RatingService(daoFactory);
    }

    public static WalletService getWalletService() {
        return new WalletService(daoFactory);
    }

    public static ValidationService getValidationService() {
        return new ValidationService(daoFactory);
    }

    public static DriverService getDriverService() {
        return new DriverService(getDaoFactory());
    }
}