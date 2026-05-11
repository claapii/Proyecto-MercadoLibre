package com.mycompany.mercadolibre;

import java.io.*;
import java.net.*;
import java.util.*;

public class Cliente {

    public static void main(String[] args) {

        try (
            Socket socket = new Socket("localhost", 5000);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            Scanner sc = new Scanner(System.in);
        ) {
            // 1. Pedimos el saldo una única vez al iniciar sesión
            System.out.print("Bienvenido a MercadoLibre. Ingrese su saldo inicial: $");
            int miSaldo = sc.nextInt();
            sc.nextLine();

            // 2. Bucle principal para no desconectarse
            while (true) {
                System.out.println("\n--- MENÚ MERCADOLIBRE ---");
                System.out.println("Saldo actual: $" + miSaldo);
                System.out.println("1. Buscar productos");
                System.out.println("2. Comprar producto");
                System.out.println("3. Salir");
                System.out.print("Elija una opción: ");

                int op = sc.nextInt();
                sc.nextLine();

                Peticion req = new Peticion();

                if (op == 1) {
                    req.tipo = "BUSCAR";
                    System.out.print("Nombre del producto a buscar: ");
                    req.dato = sc.nextLine();

                } else if (op == 2) {
                    req.tipo = "COMPRAR";
                    System.out.print("ID del producto a comprar: ");
                    req.dato = sc.nextLine();
                    req.saldoCliente = miSaldo; // Enviamos el saldo de la sesión

                } else if (op == 3) {
                    req.tipo = "SALIR";
                    out.writeObject(req);
                    out.flush();
                    System.out.println("Desconectando del servidor... ¡Hasta pronto!");
                    break; // Rompe el bucle y cierra el socket
                } else {
                    System.out.println("Opción no válida.");
                    continue;
                }

                // Enviar petición al servidor
                out.writeObject(req);
                out.flush();

                // Recibir respuesta
                Respuesta res = (Respuesta) in.readObject();

                // Procesar la respuesta
                if (req.tipo.equals("BUSCAR")) {
                    System.out.println("\n--- Resultados de búsqueda ---");
                    if (res.productos.isEmpty()) {
                        System.out.println("No se encontraron productos.");
                    } else {
                        for (Producto p : res.productos) {
                            System.out.println("ID: " + p.id + " | " + p.nombre +
                                    " | Stock: " + p.stock +
                                    " | Precio: $" + p.precio);
                        }
                    }
                } else if (req.tipo.equals("COMPRAR")) {
                    System.out.println("\n" + res.mensaje);
                    
                    // Si la compra fue exitosa, el servidor nos envió el nuevo saldo. Lo actualizamos localmente.
                    if (res.nuevoSaldo != -1) {
                        miSaldo = res.nuevoSaldo;
                    }
                }
            }

        } catch (Exception e) {
            System.out.println("Error cliente: " + e.getMessage());
        }
    }
}