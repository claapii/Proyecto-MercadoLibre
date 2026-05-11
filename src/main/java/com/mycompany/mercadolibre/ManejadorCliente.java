package com.mycompany.mercadolibre;

import java.net.*;
import java.io.*;

public class ManejadorCliente implements Runnable {

    private Socket socket;
    private Servidor servidor;

    public ManejadorCliente(Socket socket, Servidor servidor) {
        this.socket = socket;
        this.servidor = servidor;
    }

    @Override
    public void run() {
        try (
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
        ) {
            // Bucle infinito para mantener la conexión persistente
            while (true) {
                Peticion req = (Peticion) in.readObject();
                Respuesta res = new Respuesta();

                if (req.tipo.equalsIgnoreCase("BUSCAR")) {
                    res.productos = servidor.buscar(req.dato);
                    
                } else if ("COMPRAR".equalsIgnoreCase(req.tipo)) {
                    try {
                        int id = Integer.parseInt(req.dato);
                        // Ahora servidor.comprar devuelve el objeto Respuesta completo
                        res = servidor.comprar(id, req.saldoCliente);
                        
                    } catch (NumberFormatException e) {
                        res.mensaje = "ID inválido.";
                    }
                    
                } else if (req.tipo.equalsIgnoreCase("SALIR")) {
                    break; // Rompe el bucle y cierra el hilo ordenadamente
                }

                out.writeObject(res);
                out.flush(); // Asegura que los datos viajen por el socket
            }

        } catch (EOFException e) {
            // Este catch maneja la desconexión normal del cliente sin mostrar un error catastrófico
            System.out.println("Un cliente se ha desconectado de forma segura.");
        } catch (Exception e) {
            System.out.println("Error en cliente: " + e.getMessage());
        }
    }
}