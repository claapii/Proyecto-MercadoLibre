package com.mycompany.mercadolibre;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

public class PruebaConexionNodos {

    public static void main(String[] args) {
        List<NodoInfo> nodos = Membresia.obtenerNodos();

        System.out.println("--- Probando conexión interna entre nodos MercadoLibre ---");

        for (NodoInfo nodo : nodos) {
            try (Socket socket = new Socket()) {

                socket.connect(
                        new InetSocketAddress(nodo.getHost(), nodo.getPuerto()),
                        2000
                );

                System.out.println("OK: Conectado a Nodo "
                        + nodo.getIdNodo()
                        + " en "
                        + nodo.getHost()
                        + ":"
                        + nodo.getPuerto());

                Membresia.marcarNodoActivo(nodo.getIdNodo());

            } catch (Exception e) {
                System.out.println("ERROR: No se pudo conectar a Nodo "
                        + nodo.getIdNodo()
                        + " en "
                        + nodo.getHost()
                        + ":"
                        + nodo.getPuerto()
                        + " -> "
                        + e.getMessage());

                Membresia.marcarNodoInactivo(nodo.getIdNodo());
            }
        }

        System.out.println("--- Estado final de membresía ---");
        Membresia.mostrarMembresia();

        System.out.println("--- Prueba finalizada ---");
    }
}