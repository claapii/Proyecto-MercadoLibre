package com.mycompany.mercadolibre;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DetectorFallos extends Thread {

    private static final long TIMEOUT_MS           = 6000;
    private static final int  INTERVALO_CHEQUEO_MS = 2000;

    private int idNodo;
    private Map<Integer, Long> ultimoHeartbeat = new ConcurrentHashMap<>();
    private boolean ejecutando;
    private Servidor servidor;

    public DetectorFallos(int idNodo, Servidor servidor) {
        this.idNodo = idNodo;
        this.servidor = servidor;
        this.ejecutando = true;
        this.setDaemon(true);

        for (NodoInfo nodo : Membresia.obtenerOtrosNodos(idNodo)) {
            ultimoHeartbeat.put(nodo.getIdNodo(), System.currentTimeMillis());
        }
    }

    public void registrarHeartbeat(int idNodoOrigen) {
        ultimoHeartbeat.put(idNodoOrigen, System.currentTimeMillis());

        NodoInfo nodo = Membresia.buscarPorId(idNodoOrigen);
        if (nodo != null && !nodo.isActivo()) {
            Membresia.marcarNodoActivo(idNodoOrigen);
            System.out.println("[DetectorFallos][Nodo " + idNodo + "] Nodo "
                + idNodoOrigen + " se reintegró al sistema.");

            // NUEVO: notificar al Servidor para que envíe el estado al nodo reintegrado
            servidor.notificarNodoReintegrado(idNodoOrigen);
        }
    }

    @Override
    public void run() {
        System.out.println("[Nodo " + idNodo + "] Detector de fallos iniciado. Timeout: "
            + TIMEOUT_MS + "ms");

        while (ejecutando) {
            try {
                Thread.sleep(INTERVALO_CHEQUEO_MS);
            } catch (InterruptedException e) {
                break;
            }

            long ahora = System.currentTimeMillis();
            List<NodoInfo> otros = Membresia.obtenerOtrosNodos(idNodo);

            for (NodoInfo nodo : otros) {
                Long ultimo = ultimoHeartbeat.get(nodo.getIdNodo());
                if (ultimo == null) continue;

                long silencio = ahora - ultimo;

                if (silencio > TIMEOUT_MS && nodo.isActivo()) {
                    Membresia.marcarNodoInactivo(nodo.getIdNodo());
                    System.out.println("[DetectorFallos][Nodo " + idNodo + "] *** NODO "
                        + nodo.getIdNodo() + " CAÍDO (sin heartbeat por "
                        + silencio + "ms) ***");
                    servidor.notificarNodoCaido(nodo.getIdNodo());
                }
            }
        }
    }

    public void detener() {
        this.ejecutando = false;
    }
}