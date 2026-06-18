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

            System.out.println("[COMUNICADOR] Mensaje enviado a Nodo "
                    + nodoDestino.getIdNodo() + ": " + mensaje.getTipoMensaje());

            return true;

        } catch (Exception e) {
            System.out.println("[COMUNICADOR] No se pudo enviar mensaje al Nodo "
                    + nodoDestino.getIdNodo());

            nodoDestino.setActivo(false);
            return false;
        }
    }
}