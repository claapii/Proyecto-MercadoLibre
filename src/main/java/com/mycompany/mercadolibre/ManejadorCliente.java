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
            System.out.println("Cliente conectado desde: " + socket.getInetAddress());

            // Bucle para mantener la conexión persistente con el cliente.
            while (true) {
                Peticion req = (Peticion) in.readObject();
                Respuesta res = new Respuesta();

                // Validación básica: evita errores si llega una petición nula.
                if (req == null || req.tipo == null) {
                    res.mensaje = "Petición inválida.";
                    out.writeObject(res);
                    out.flush();
                    continue;
                }

                if (req.tipo.equalsIgnoreCase("BUSCAR")) {

                    if (req.dato == null || req.dato.trim().isEmpty()) {
                        res.mensaje = "Debe ingresar un nombre de producto para buscar.";
                    } else {
                        res.productos = servidor.buscar(req.dato);
                        res.mensaje = "Búsqueda realizada correctamente.";
                    }

                } else if (req.tipo.equalsIgnoreCase("COMPRAR")) {

                    try {
                        int id = Integer.parseInt(req.dato);
                        res = servidor.comprar(id, req.saldoCliente);

                    } catch (NumberFormatException e) {
                        res.mensaje = "ID inválido. Debe ingresar un número.";
                    }

                } else if (req.tipo.equalsIgnoreCase("SALIR")) {
                    System.out.println("Cliente solicitó salir.");
                    break;

                } else {
                    res.mensaje = "Tipo de petición no reconocido: " + req.tipo;
                }

                out.writeObject(res);
                out.flush();
            }

        } catch (SocketTimeoutException e) {
            System.out.println("Cliente desconectado por tiempo de espera.");

        } catch (EOFException e) {
            System.out.println("Un cliente se ha desconectado de forma segura.");

        } catch (ClassNotFoundException e) {
            System.out.println("Objeto recibido no reconocido: " + e.getMessage());

        } catch (IOException e) {
            System.out.println("Error de comunicación con cliente: " + e.getMessage());

        } catch (Exception e) {
            System.out.println("Error inesperado en cliente: " + e.getMessage());

        } finally {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
                System.out.println("Conexión con cliente cerrada.");
            } catch (IOException e) {
                System.out.println("Error cerrando socket del cliente: " + e.getMessage());
            }
        }
    }
}