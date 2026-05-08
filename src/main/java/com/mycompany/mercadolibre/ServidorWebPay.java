package com.mycompany.mercadolibre;

import java.io.*;
import java.net.*;

public class ServidorWebPay {

    public static void main(String[] args) {
        new ServidorWebPay().iniciar();
    }

    private void iniciar() {
        try (ServerSocket serverSocket = new ServerSocket(6000)) {

            System.out.println("Servidor WebPay corriendo en puerto 6000...");

            while (true) {
                Socket cliente = serverSocket.accept();
                new Thread(new ManejadorPago(cliente)).start();
            }

        } catch (IOException e) {
            System.out.println("Error en Servidor WebPay: " + e.getMessage());
        }
    }
}
