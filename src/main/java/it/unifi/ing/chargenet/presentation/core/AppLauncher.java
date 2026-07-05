package it.unifi.ing.chargenet.presentation.core;

import it.unifi.ing.chargenet.business.core.GridCluster;
import it.unifi.ing.chargenet.business.core.GridMonitor;
import it.unifi.ing.chargenet.business.services.SessionService;
import it.unifi.ing.chargenet.business.services.StationService;
import it.unifi.ing.chargenet.business.services.RatingService;
import it.unifi.ing.chargenet.business.services.LoadBalancerService;
import it.unifi.ing.chargenet.domain.infrastructure.PowerTransformer;
import it.unifi.ing.chargenet.dao.interfaces.TransformerDao;
import it.unifi.ing.chargenet.dao.interfaces.StationDao;

import it.unifi.ing.chargenet.dao.interfaces.DaoFactory;
import it.unifi.ing.chargenet.dao.postgres.PostgresDaoFactory;
import it.unifi.ing.chargenet.presentation.navigation.NavigationManager;
import it.unifi.ing.chargenet.dao.postgres.DatabaseManager;
import javafx.application.Application;
import javafx.stage.Stage;
import org.h2.tools.Server;

import java.sql.SQLException;

public class AppLauncher extends Application {

    public static Stage primaryStage;
    private Server h2WebServer;

    @Override
    public void start(Stage stage) {
        try {
            primaryStage = stage;

            h2WebServer = Server.createWebServer("-web", "-webAllowOthers", "-webPort", "8082").start();
            System.out.println("🌐 Console H2: http://localhost:8082 (JDBC: jdbc:h2:mem:chargenet)");

            DaoFactory daoFactory = new PostgresDaoFactory();
            NavigationManager.initialize(primaryStage, daoFactory);

            // 1. Schema + seed (una volta sola)
            DatabaseManager.initializeSchema();

            // 2. Inizializza il GridCluster leggendo trasformatori e stazioni dal DB.
            try (java.sql.Connection conn = DatabaseManager.getConnection()) {
                TransformerDao transformerDao = daoFactory.createTransformerDao(conn);
                StationDao stationDao = daoFactory.createStationDao(conn);
                GridCluster.getInstance().init(transformerDao, stationDao);
                System.out.println("⚙️ GridCluster inizializzato: "
                        + GridCluster.getInstance().getTransformers().size() + " trasformatori in cache.");
            }

            // 3. Crea i service condivisi
            SessionService sessionService = NavigationManager.getSessionService();
            StationService stationService = NavigationManager.getStationService();
            RatingService ratingService   = NavigationManager.getRatingService();

            // 4. OBSERVER: crea il LoadBalancerService e REGISTRALO su OGNI trasformatore.
            //    Senza questo attach, notifyObservers() non ha ascoltatori e il
            //    THERMAL_ALERT non produce alcun effetto.
            LoadBalancerService loadBalancer = new LoadBalancerService(sessionService, daoFactory);
            for (PowerTransformer t : GridCluster.getInstance().getTransformers()) {
                t.attach(loadBalancer);
            }
            System.out.println("🔌 LoadBalancerService registrato come Observer su "
                    + GridCluster.getInstance().getTransformers().size() + " trasformatori.");

            // 5. Avvia il GridMonitor (thread daemon, tick ogni 5s)
            GridMonitor gridMonitor = new GridMonitor(stationService, sessionService, ratingService);
            gridMonitor.start();
            System.out.println("⚡ GridMonitor avviato.");

            Thread.setDefaultUncaughtExceptionHandler(new GlobalExceptionHandler());

            primaryStage.setTitle("ChargeNet - EV Charging Network");
            primaryStage.setWidth(1024);
            primaryStage.setHeight(768);

            NavigationManager.navigateTo("LOGIN");

        } catch (SQLException e) {
            System.err.println("❌ Errore fatale H2/DB.");
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("❌ Errore fatale durante l'avvio.");
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {
        System.out.println("🛑 Applicazione in fase di chiusura. Rilascio risorse...");
        if (h2WebServer != null) {
            h2WebServer.stop();
            System.out.println("🔌 Server web H2 spento.");
        }
        System.out.println("👋 Risorse rilasciate con successo. Chiusura JVM.");
    }

    public static void main(String[] args) {
        launch(args);
    }
}