package com.mycompany.mercadolibre;

import java.util.List;

public class EmisorHeartbeat extends Thread {

    private static final int INTERVALO_MS = 2000; // cada 2 segundos

    private int idNodo;
    private int puertoNodo;
    private RelojLamport relojLamport;
    private ComunicadorNodos comunicador;
    private boolean ejecutando;

    public EmisorHeartbeat(int idNodo, int puertoNodo, RelojLamport relojLamport) {
        this.idNodo = idNodo;
        this.puertoNodo = puertoNodo;
        this.relojLamport = relojLamport;
        this.comunicador = new ComunicadorNodos();
        this.ejecutando = true;
        this.setDaemon(true); // muere solo cuando el Servidor muere
    }

    @Override
    public void run() {
        System.out.println("[Nodo " + idNodo + "] Emisor de heartbeats iniciado.");

        while (ejecutando) {
            try {
                Thread.sleep(INTERVALO_MS);
            } catch (InterruptedException e) {
                break;
            }

            List<NodoInfo> otros = Membresia.obtenerOtrosNodos(idNodo);

            for (NodoInfo destino : otros) {
                int tiempo = relojLamport.incrementar(); // evento de envío

                MensajeNodo hb = new MensajeNodo(
                    "HEARTBEAT",
                    idNodo,
                    puertoNodo,
                    destino.getIdNodo(),
                    tiempo,
                    "ping"
                );

                comunicador.enviarMensaje(destino, hb);
                // El log de envío ya lo imprime ComunicadorNodos
            }
        }
    }

    public void detener() {
        this.ejecutando = false;
    }
}