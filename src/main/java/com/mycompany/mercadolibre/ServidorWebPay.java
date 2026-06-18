package com.mycompany.mercadolibre;

import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServidorWebPay {

    // Pool fijo para controlar cuántos pagos se procesan al mismo tiempo.
    private static final int MAX_PAGOS_SIMULTANEOS = 5;

    public static void main(String[] args) {
        new ServidorWebPay().iniciar();
    }

    private void iniciar() {
        ExecutorService poolPagos = Executors.newFixedThreadPool(MAX_PAGOS_SIMULTANEOS);

        try (ServerSocket serverSocket = new ServerSocket(6000)) {

            System.out.println("Servidor WebPay corriendo en puerto 6000...");
            System.out.println("Pool de pagos activo. Máximo de pagos simultáneos: " + MAX_PAGOS_SIMULTANEOS);

            while (true) {
                Socket cliente = serverSocket.accept();

                // Timeout para evitar que una conexión quede esperando indefinidamente.
                cliente.setSoTimeout(60000);

                // Se entrega la petición de pago al pool, en vez de crear hilos ilimitados.
                poolPagos.execute(new ManejadorPago(cliente));
            }

        } catch (IOException e) {
            System.out.println("Error en Servidor WebPay: " + e.getMessage());
        } finally {
            poolPagos.shutdown();
        }
    }
}