package com.mycompany.mercadolibre;

import java.io.*;
import java.net.*;

public class ManejadorPago implements Runnable {

    private Socket socket;

    public ManejadorPago(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
        ) {

            // Se recibe una petición de pago desde el servidor MercadoLibre.
            PeticionPago peticion = (PeticionPago) in.readObject();
            RespuestaPago respuesta = new RespuestaPago();

            // Validación básica de datos recibidos.
            if (peticion == null) {
                respuesta.aprobado = false;
                respuesta.mensaje = "Pago rechazado: petición inválida.";
                respuesta.saldoRestante = 0;

            } else if (peticion.monto <= 0) {
                respuesta.aprobado = false;
                respuesta.mensaje = "Pago rechazado: monto inválido.";
                respuesta.saldoRestante = peticion.saldoCliente;

            } else if (peticion.saldoCliente < 0) {
                respuesta.aprobado = false;
                respuesta.mensaje = "Pago rechazado: saldo inválido.";
                respuesta.saldoRestante = peticion.saldoCliente;

            } else if (peticion.saldoCliente >= peticion.monto) {
                respuesta.aprobado = true;
                respuesta.mensaje = "Pago aprobado por WebPay.";
                respuesta.saldoRestante = peticion.saldoCliente - peticion.monto;

            } else {
                respuesta.aprobado = false;
                respuesta.mensaje = "Pago rechazado por WebPay: saldo insuficiente.";
                respuesta.saldoRestante = peticion.saldoCliente;
            }

            // Se envía la respuesta serializada al servidor MercadoLibre.
            out.writeObject(respuesta);
            out.flush();

        } catch (SocketTimeoutException e) {
            System.out.println("Tiempo de espera agotado procesando pago.");

        } catch (EOFException e) {
            System.out.println("Conexión de pago cerrada por el cliente.");

        } catch (ClassNotFoundException e) {
            System.out.println("Objeto de pago recibido no reconocido: " + e.getMessage());

        } catch (IOException e) {
            System.out.println("Error de comunicación procesando pago: " + e.getMessage());

        } catch (Exception e) {
            System.out.println("Error inesperado procesando pago: " + e.getMessage());

        } finally {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                System.out.println("Error cerrando socket de pago: " + e.getMessage());
            }
        }
    }
}