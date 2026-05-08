package it.unifi.ing.chargenet.presentation.navigation;

import java.util.HashMap;
import java.util.Map;

public class NavigationManager {

    // Mappa che associa un "nome logico" al "percorso fisico" del file FXML
    private static final Map<String, String> FXML_MAP = new HashMap<>();

    // Blocco di inizializzazione statica: qui tu e il tuo collega
    // registrerete le vostre schermate man mano che le create nei vostri branch.
    static {
        // Esempio: FXML_MAP.put("LOGIN", "/fxml/auth/Login.fxml");
        // Esempio: FXML_MAP.put("DASHBOARD", "/fxml/core/Dashboard.fxml");
    }

    /**
     * Navigazione semplice: cambia pagina senza bisogno di interagire col controller.
     */
    public static void navigateTo(String viewName) {
        // TODO: Caricare l'FXML dalla mappa e impostare la Scene sull'AppLauncher.primaryStage
    }

    /**
     * Navigazione avanzata: cambia pagina e restituisce il Controller.
     * Utilissimo per passare dati alla pagina appena aperta.
     */
    public static <T> T navigateToWithController(String viewName) {
        return null; // TODO: Caricare FXML, impostare Scene e ritornare loader.getController()
    }
}