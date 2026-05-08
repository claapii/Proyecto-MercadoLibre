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

            Peticion req = (Peticion) in.readObject();
            Respuesta res = new Respuesta();

            if (req.tipo.equalsIgnoreCase("BUSCAR")) {
                res.productos = servidor.buscar(req.dato);

            } else if ("COMPRAR".equalsIgnoreCase(req.tipo)) {

                try {
                    int id = Integer.parseInt(req.dato);
                    res.mensaje = servidor.comprar(id, req.saldoCliente);

                } catch (NumberFormatException e) {
                    res.mensaje = "ID inválido.";
                }
            }

            out.writeObject(res);

        } catch (Exception e) {
            System.out.println("Error en cliente: " + e.getMessage());
        }
    }
}