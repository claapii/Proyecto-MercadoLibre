package com.mycompany.mercadolibre;

import java.util.List;

public class AlgoritmoBully {

    // Tiempo de espera para recibir un OK antes de proclamarse coordinador
    private static final int TIMEOUT_RESPUESTA_MS = 3000;

    private Servidor servidor;
    private int idNodo;
    private ComunicadorNodos comunicador;

    // Flag para evitar que dos elecciones corran al mismo tiempo
    private volatile boolean eleccionEnCurso = false;

    // Flag que se activa cuando llega un OK de un nodo mayor
    private volatile boolean recibiOK = false;

    public AlgoritmoBully(Servidor servidor) {
        this.servidor = servidor;
        this.idNodo = servidor.getIdNodo();
        this.comunicador = new ComunicadorNodos();
    }

    // Llamado por el Servidor cuando detecta que el coordinador cayó
    public synchronized void iniciarEleccion() {
        if (eleccionEnCurso) {
            System.out.println("[Bully][Nodo " + idNodo + "] Elección ya en curso, ignorando.");
            return;
        }

        eleccionEnCurso = true;
        recibiOK = false;

        servidor.registrarEvento("[Bully] Iniciando elección.");

        // Buscar nodos con ID mayor que el propio
        List<NodoInfo> nodosMayores = obtenerNodosMayores();

        if (nodosMayores.isEmpty()) {
            // Soy el nodo con ID más alto → me proclamo coordinador directamente
            proclamarseCoordinador();
            return;
        }

        // Enviar ELECCION a todos los nodos con ID mayor
        for (NodoInfo destino : nodosMayores) {
            int tiempo = servidor.avanzarRelojLamport();
            MensajeNodo msg = new MensajeNodo(
                "ELECCION", idNodo, servidor.getPuertoNodos(),
                destino.getIdNodo(), tiempo, "eleccion"
            );
            comunicador.enviarMensaje(destino, msg);
            servidor.registrarEvento("[Bully] ELECCION enviada a Nodo " + destino.getIdNodo());
        }

        // Esperar TIMEOUT_RESPUESTA_MS por un OK
        new Thread(() -> {
            try {
                Thread.sleep(TIMEOUT_RESPUESTA_MS);
            } catch (InterruptedException e) {
                return;
            }

            if (!recibiOK) {
                // Nadie respondió → me proclamo coordinador
                proclamarseCoordinador();
            } else {
                // Alguien con ID mayor respondió → espero que él sea coordinador
                servidor.registrarEvento("[Bully] Recibí OK, esperando nuevo coordinador.");
                eleccionEnCurso = false;
            }
        }).start();
    }

    // Llamado por ReceptorMensajesNodo cuando llega un mensaje ELECCION
    public void recibirEleccion(int idOrigen) {
        servidor.registrarEvento("[Bully] ELECCION recibida de Nodo " + idOrigen
            + ". Respondiendo OK.");

        // Responder OK al nodo que envió la elección
        NodoInfo origen = Membresia.buscarPorId(idOrigen);
        if (origen != null) {
            int tiempo = servidor.avanzarRelojLamport();
            MensajeNodo ok = new MensajeNodo(
                "OK", idNodo, servidor.getPuertoNodos(),
                idOrigen, tiempo, "ok"
            );
            comunicador.enviarMensaje(origen, ok);
        }

        // Iniciar mi propia elección (soy mayor que el que preguntó)
        iniciarEleccion();
    }

    // Llamado por ReceptorMensajesNodo cuando llega un mensaje OK
    public void recibirOK(int idOrigen) {
        servidor.registrarEvento("[Bully] OK recibido de Nodo " + idOrigen
            + ". Un nodo mayor tomará el control.");
        recibiOK = true;
    }

    // Llamado por ReceptorMensajesNodo cuando llega un mensaje COORDINADOR
    public void recibirCoordinador(int idNuevoCoordinador) {
        servidor.registrarEvento("[Bully] Nuevo coordinador: Nodo " + idNuevoCoordinador);
        servidor.actualizarCoordinador(idNuevoCoordinador);
        eleccionEnCurso = false;
    }

    private void proclamarseCoordinador() {
        servidor.registrarEvento("[Bully] ¡Me proclamo coordinador! Nodo " + idNodo);
        servidor.actualizarCoordinador(idNodo);

        // Notificar a todos los demás
        List<NodoInfo> otros = Membresia.obtenerOtrosNodos(idNodo);
        for (NodoInfo destino : otros) {
            if (destino.isActivo()) {
                int tiempo = servidor.avanzarRelojLamport();
                MensajeNodo msg = new MensajeNodo(
                    "COORDINADOR", idNodo, servidor.getPuertoNodos(),
                    destino.getIdNodo(), tiempo, "nuevo_coordinador"
                );
                comunicador.enviarMensaje(destino, msg);
                servidor.registrarEvento("[Bully] COORDINADOR enviado a Nodo "
                    + destino.getIdNodo());
            }
        }

        eleccionEnCurso = false;
    }

    private List<NodoInfo> obtenerNodosMayores() {
        List<NodoInfo> mayores = new java.util.ArrayList<>();
        for (NodoInfo nodo : Membresia.obtenerNodos()) {
            if (nodo.getIdNodo() > idNodo && nodo.isActivo()) {
                mayores.add(nodo);
            }
        }
        return mayores;
    }
}
