package com.mycompany.mercadolibre;

import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class ReceptorMensajesNodo extends Thread {

    private int idNodo;
    private int puertoNodo;
    private boolean ejecutando;
    private RelojLamport relojLamport;
    private DetectorFallos detectorFallos;
    private Servidor servidor;

    public ReceptorMensajesNodo(int idNodo, int puertoNodo, RelojLamport relojLamport,
                                 DetectorFallos detectorFallos, Servidor servidor) {
        this.idNodo = idNodo;
        this.puertoNodo = puertoNodo;
        this.ejecutando = true;
        this.relojLamport = relojLamport;
        this.detectorFallos = detectorFallos;
        this.servidor = servidor;
    }

    @Override
    public void run() {
        try {
            ServerSocket serverSocket = new ServerSocket(puertoNodo);
            System.out.println("[Nodo " + idNodo + "] Receptor de mensajes iniciado en puerto " + puertoNodo);

            while (ejecutando) {
                Socket socket = serverSocket.accept();
                ObjectInputStream entrada = new ObjectInputStream(socket.getInputStream());
                Object objetoRecibido = entrada.readObject();

                if (objetoRecibido instanceof MensajeNodo) {
                    procesarMensaje((MensajeNodo) objetoRecibido);
                }

                entrada.close();
                socket.close();
            }

            serverSocket.close();

        } catch (Exception e) {
            System.out.println("[Nodo " + idNodo + "] Error en receptor de mensajes: " + e.getMessage());
        }
    }

    private void procesarMensaje(MensajeNodo mensaje) {
        int tiempoActualizado = relojLamport.actualizar(mensaje.getRelojLamport());

        System.out.println("------------------------------------");
        System.out.println("[Lamport=" + tiempoActualizado + "][Nodo " + idNodo + "] Mensaje recibido");
        System.out.println("Tipo: " + mensaje.getTipoMensaje());
        System.out.println("Origen: Nodo " + mensaje.getIdOrigen());
        System.out.println("Destino: Nodo " + mensaje.getIdDestino());
        System.out.println("Reloj Lamport del emisor: " + mensaje.getRelojLamport());
        System.out.println("Reloj Lamport local tras actualizar: " + tiempoActualizado);
        System.out.println("Contenido: " + mensaje.getContenido());
        System.out.println("------------------------------------");

        switch (mensaje.getTipoMensaje()) {

            case "HEARTBEAT":
                detectorFallos.registrarHeartbeat(mensaje.getIdOrigen());
                System.out.println("[Lamport=" + tiempoActualizado + "][Nodo " + idNodo
                    + "] Heartbeat de Nodo " + mensaje.getIdOrigen() + " registrado.");
                break;

            case "ELECCION":
                servidor.getBully().recibirEleccion(mensaje.getIdOrigen());
                break;

            case "OK":
                servidor.getBully().recibirOK(mensaje.getIdOrigen());
                break;

            case "COORDINADOR":
                servidor.getBully().recibirCoordinador(mensaje.getIdOrigen());
                break;

            case "SYNC_STOCK":
                try {
                    String[] partes = mensaje.getContenido().split(":");
                    int idProducto = Integer.parseInt(partes[0]);
                    int nuevoStock  = Integer.parseInt(partes[1]);

                    // Pasamos el Lamport del mensaje para validar orden causal
                    servidor.aplicarSincronizacionStock(idProducto, nuevoStock,
                            mensaje.getRelojLamport());

                } catch (Exception e) {
                    System.out.println("[Nodo " + idNodo + "] Error parseando SYNC_STOCK: "
                            + e.getMessage());
                }
                break;

            case "SOLICITAR_ESTADO":
                // Un nodo se reintegró y pide el catálogo actualizado
                System.out.println("[Lamport=" + tiempoActualizado + "][Nodo " + idNodo
                        + "] Nodo " + mensaje.getIdOrigen()
                        + " solicitó transferencia de estado.");
                servidor.enviarEstadoActualANodo(mensaje.getIdOrigen());
                break;

            default:
                System.out.println("[Lamport=" + tiempoActualizado + "][Nodo " + idNodo
                    + "] Tipo de mensaje no reconocido: " + mensaje.getTipoMensaje());
        }
    }

    public void detener() {
        this.ejecutando = false;
    }
}