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

            //incluir el reloj de Lamport en el log de envío
            System.out.println("[Lamport=" + mensaje.getRelojLamport() + "][COMUNICADOR] "
                    + "Mensaje " + mensaje.getTipoMensaje()
                    + " enviado a Nodo " + nodoDestino.getIdNodo());

            return true;

        } catch (Exception e) {
            System.out.println("[COMUNICADOR] No se pudo enviar mensaje al Nodo "
                    + nodoDestino.getIdNodo() + ": " + e.getMessage());

            return false;
        }
    }
}
