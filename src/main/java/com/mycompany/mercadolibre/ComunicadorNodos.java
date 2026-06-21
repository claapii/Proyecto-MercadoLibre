package com.mycompany.mercadolibre;

import java.io.ObjectOutputStream;
import java.net.Socket;

public class ComunicadorNodos {

    public boolean enviarMensaje(NodoInfo nodoDestino, MensajeNodo mensaje) {
        try {
            Socket socket = new Socket(nodoDestino.getHost(), nodoDestino.getPuerto());

            ObjectOutputStream salida = new ObjectOutputStream(socket.getOutputStream());
            salida.writeObject(mensaje);
            salida.flush();

            salida.close();
            socket.close();

            String linea = "[Lamport=" + mensaje.getRelojLamport() + "]"
                    + "[Nodo " + mensaje.getIdOrigen() + "] "
                    + "Mensaje " + mensaje.getTipoMensaje()
                    + " enviado a Nodo " + nodoDestino.getIdNodo()
                    + " | Contenido: " + mensaje.getContenido();

            System.out.println(linea);

            // Guarda en el log del nodo que envía el mensaje.
            LoggerSistema.logNodo(mensaje.getIdOrigen(), linea);

            return true;

        } catch (Exception e) {
            String linea = "[Nodo " + mensaje.getIdOrigen() + "] "
                    + "No se pudo enviar mensaje " + mensaje.getTipoMensaje()
                    + " al Nodo " + nodoDestino.getIdNodo()
                    + ": " + e.getMessage();

            System.out.println("[COMUNICADOR] " + linea);

            // También guardamos los fallos de envío, porque sirven como evidencia de omisión/fallo.
            LoggerSistema.logNodo(mensaje.getIdOrigen(), "[COMUNICADOR] " + linea);

            return false;
        }
    }
}