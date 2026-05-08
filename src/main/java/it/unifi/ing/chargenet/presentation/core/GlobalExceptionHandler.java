package it.unifi.ing.chargenet.presentation.core;

import java.lang.Thread.UncaughtExceptionHandler;

public class GlobalExceptionHandler implements UncaughtExceptionHandler {

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        // TODO: Intercettare l'errore, stamparlo in console e chiamare showErrorDialog
    }

    private void showErrorDialog(Thread t, Throwable e) {
        // TODO: Creare un JavaFX Alert di tipo ERROR per mostrare il messaggio all'utente
        // senza far crashare l'applicazione
    }
}