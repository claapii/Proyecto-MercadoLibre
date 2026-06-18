package com.mycompany.mercadolibre;

import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class ReceptorMensajesNodo extends Thread {

    private int idNodo;
    private int puertoNodo;
    private boolean ejecutando;

    public ReceptorMensajesNodo(int idNodo, int puertoNodo) {
        this.idNodo = idNodo;
        this.puertoNodo = puertoNodo;
        this.ejecutando = true;
    }

    @Override
    public void run() {
        try {
            ServerSocket servidor = new ServerSocket(puertoNodo);

            System.out.println("[Nodo " + idNodo + "] Receptor de mensajes iniciado en puerto " + puertoNodo);

            while (ejecutando) {
                Socket socket = servidor.accept();

                ObjectInputStream entrada = new ObjectInputStream(socket.getInputStream());

                Object objetoRecibido = entrada.readObject();

                if (objetoRecibido instanceof MensajeNodo) {
                    MensajeNodo mensaje = (MensajeNodo) objetoRecibido;
                    procesarMensaje(mensaje);
                }

                entrada.close();
                socket.close();
            }

            servidor.close();

        } catch (Exception e) {
            System.out.println("[Nodo " + idNodo + "] Error en receptor de mensajes: " + e.getMessage());
        }
    }

    private void procesarMensaje(MensajeNodo mensaje) {
        System.out.println("------------------------------------");
        System.out.println("[Nodo " + idNodo + "] Mensaje recibido");
        System.out.println("Tipo: " + mensaje.getTipoMensaje());
        System.out.println("Origen: Nodo " + mensaje.getIdOrigen());
        System.out.println("Destino: Nodo " + mensaje.getIdDestino());
        System.out.println("Reloj Lamport recibido: " + mensaje.getRelojLamport());
        System.out.println("Contenido: " + mensaje.getContenido());
        System.out.println("------------------------------------");

        if (mensaje.getTipoMensaje().equals("HEARTBEAT")) {
            System.out.println("[Nodo " + idNodo + "] Nodo " + mensaje.getIdOrigen() + " sigue activo.");
        } else if (mensaje.getTipoMensaje().equals("ELECCION")) {
            System.out.println("[Nodo " + idNodo + "] Recibí solicitud de elección desde Nodo " + mensaje.getIdOrigen());
        } else if (mensaje.getTipoMensaje().equals("OK")) {
            System.out.println("[Nodo " + idNodo + "] Recibí OK desde Nodo " + mensaje.getIdOrigen());
        } else if (mensaje.getTipoMensaje().equals("COORDINADOR")) {
            System.out.println("[Nodo " + idNodo + "] Nuevo coordinador informado: Nodo " + mensaje.getIdOrigen());
        } else if (mensaje.getTipoMensaje().equals("SYNC_STOCK")) {
            System.out.println("[Nodo " + idNodo + "] Recibí sincronización de stock.");
        } else {
            System.out.println("[Nodo " + idNodo + "] Tipo de mensaje no reconocido.");
        }
    }

    public void detener() {
        this.ejecutando = false;
    }
}