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

            PeticionPago peticion = (PeticionPago) in.readObject();
            RespuestaPago respuesta = new RespuestaPago();

            if (peticion.saldoCliente >= peticion.monto) {
                respuesta.aprobado = true;
                respuesta.mensaje = "Pago aprobado por WebPay.";
                respuesta.saldoRestante = peticion.saldoCliente - peticion.monto;
            } else {
                respuesta.aprobado = false;
                respuesta.mensaje = "Pago rechazado por WebPay: saldo insuficiente.";
                respuesta.saldoRestante = peticion.saldoCliente;
            }

            out.writeObject(respuesta);

        } catch (Exception e) {
            System.out.println("Error procesando pago: " + e.getMessage());
        }
    }
}