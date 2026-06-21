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
            LoggerSistema.logNodo(idNodo,
                    "[Nodo " + idNodo + "] Error en receptor de mensajes: " + e.getMessage());
        }
    }

    private void procesarMensaje(MensajeNodo mensaje) {
        int tiempoActualizado = relojLamport.actualizar(mensaje.getRelojLamport());

        String lineaRecepcion = "[Lamport=" + tiempoActualizado + "]"
                + "[Nodo " + idNodo + "] "
                + "Mensaje recibido | Tipo: " + mensaje.getTipoMensaje()
                + " | Origen: Nodo " + mensaje.getIdOrigen()
                + " | Destino: Nodo " + mensaje.getIdDestino()
                + " | Lamport emisor: " + mensaje.getRelojLamport()
                + " | Lamport local actualizado: " + tiempoActualizado
                + " | Contenido: " + mensaje.getContenido();

        System.out.println(lineaRecepcion);
        LoggerSistema.logNodo(idNodo, lineaRecepcion);

        switch (mensaje.getTipoMensaje()) {

            case "HEARTBEAT": {
                detectorFallos.registrarHeartbeat(mensaje.getIdOrigen());

                String linea = "[Lamport=" + tiempoActualizado + "]"
                        + "[Nodo " + idNodo + "] "
                        + "Heartbeat registrado desde Nodo " + mensaje.getIdOrigen();

                System.out.println(linea);
                LoggerSistema.logNodo(idNodo, linea);
                break;
            }

            case "ELECCION": {
                String linea = "[Lamport=" + tiempoActualizado + "]"
                        + "[Nodo " + idNodo + "] "
                        + "Mensaje ELECCION recibido desde Nodo " + mensaje.getIdOrigen();

                System.out.println(linea);
                LoggerSistema.logNodo(idNodo, linea);

                servidor.getBully().recibirEleccion(mensaje.getIdOrigen());
                break;
            }

            case "OK": {
                String linea = "[Lamport=" + tiempoActualizado + "]"
                        + "[Nodo " + idNodo + "] "
                        + "Mensaje OK recibido desde Nodo " + mensaje.getIdOrigen();

                System.out.println(linea);
                LoggerSistema.logNodo(idNodo, linea);

                servidor.getBully().recibirOK(mensaje.getIdOrigen());
                break;
            }

            case "COORDINADOR": {
                String linea = "[Lamport=" + tiempoActualizado + "]"
                        + "[Nodo " + idNodo + "] "
                        + "Mensaje COORDINADOR recibido. Nuevo coordinador propuesto: Nodo "
                        + mensaje.getIdOrigen();

                System.out.println(linea);
                LoggerSistema.logNodo(idNodo, linea);

                servidor.getBully().recibirCoordinador(mensaje.getIdOrigen());
                break;
            }

            case "SYNC_STOCK": {
                try {
                    String[] partes = mensaje.getContenido().split(":");
                    int idProducto = Integer.parseInt(partes[0]);
                    int nuevoStock = Integer.parseInt(partes[1]);

                    String linea = "[Lamport=" + tiempoActualizado + "]"
                            + "[Nodo " + idNodo + "] "
                            + "SYNC_STOCK recibido desde Nodo " + mensaje.getIdOrigen()
                            + " | Producto ID: " + idProducto
                            + " | Nuevo stock: " + nuevoStock
                            + " | Lamport actualización: " + mensaje.getRelojLamport();

                    System.out.println(linea);
                    LoggerSistema.logNodo(idNodo, linea);

                    servidor.aplicarSincronizacionStock(
                            idProducto,
                            nuevoStock,
                            mensaje.getRelojLamport()
                    );

                } catch (Exception e) {
                    String linea = "[Nodo " + idNodo + "] "
                            + "Error parseando SYNC_STOCK: " + e.getMessage();

                    System.out.println(linea);
                    LoggerSistema.logNodo(idNodo, linea);
                }
                break;
            }

            case "SOLICITAR_ESTADO": {
                String linea = "[Lamport=" + tiempoActualizado + "]"
                        + "[Nodo " + idNodo + "] "
                        + "Nodo " + mensaje.getIdOrigen()
                        + " solicitó transferencia de estado.";

                System.out.println(linea);
                LoggerSistema.logNodo(idNodo, linea);

                servidor.enviarEstadoActualANodo(mensaje.getIdOrigen());
                break;
            }

            default: {
                String linea = "[Lamport=" + tiempoActualizado + "]"
                        + "[Nodo " + idNodo + "] "
                        + "Tipo de mensaje no reconocido: " + mensaje.getTipoMensaje();

                System.out.println(linea);
                LoggerSistema.logNodo(idNodo, linea);
                break;
            }
        }
    }

    public void detener() {
        this.ejecutando = false;
    }
}