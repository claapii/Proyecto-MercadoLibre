package com.mycompany.mercadolibre;

import java.io.*;
import java.net.*;
import java.util.*;

public class Cliente {

    public static void main(String[] args) {

        try (
            Socket socket = new Socket("localhost", 5001);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            Scanner sc = new Scanner(System.in);
        ) {
            // Timeout para evitar quedar esperando indefinidamente una respuesta del servidor.
            socket.setSoTimeout(15000);

            System.out.println("Conectado al servidor MercadoLibre.");

            // Se pide saldo inicial validando que sea un número válido.
            int miSaldo = leerEntero(sc, "Bienvenido a MercadoLibre. Ingrese su saldo inicial: $");

            while (miSaldo < 0) {
                System.out.println("El saldo no puede ser negativo.");
                miSaldo = leerEntero(sc, "Ingrese nuevamente su saldo inicial: $");
            }

            // Bucle principal para mantener la sesión activa.
            while (true) {
                System.out.println("\n--- MENÚ MERCADOLIBRE ---");
                System.out.println("Saldo actual: $" + miSaldo);
                System.out.println("1. Buscar productos");
                System.out.println("2. Comprar producto");
                System.out.println("3. Salir");

                int op = leerEntero(sc, "Elija una opción: ");

                Peticion req = new Peticion();

                if (op == 1) {
                    req.tipo = "BUSCAR";

                    System.out.print("Nombre del producto a buscar: ");
                    req.dato = sc.nextLine();

                    if (req.dato.trim().isEmpty()) {
                        System.out.println("Debe ingresar un nombre de producto.");
                        continue;
                    }

                } else if (op == 2) {
                    req.tipo = "COMPRAR";

                    int idProducto = leerEntero(sc, "ID del producto a comprar: ");
                    req.dato = String.valueOf(idProducto);
                    req.saldoCliente = miSaldo;

                } else if (op == 3) {
                    req.tipo = "SALIR";

                    out.writeObject(req);
                    out.flush();

                    System.out.println("Desconectando del servidor... ¡Hasta pronto!");
                    break;

                } else {
                    System.out.println("Opción no válida. Intente nuevamente.");
                    continue;
                }

                // Enviar petición serializada al servidor.
                out.writeObject(req);
                out.flush();

                // Recibir respuesta serializada del servidor.
                Respuesta res = (Respuesta) in.readObject();

                if (req.tipo.equals("BUSCAR")) {
                    mostrarResultadosBusqueda(res);

                } else if (req.tipo.equals("COMPRAR")) {
                    System.out.println("\n" + res.mensaje);

                    // Si la compra fue exitosa, el servidor envía el nuevo saldo.
                    if (res.nuevoSaldo != -1) {
                        miSaldo = res.nuevoSaldo;
                    }
                }
            }

        } catch (ConnectException e) {
            System.out.println("No se pudo conectar al servidor MercadoLibre. Verifique que esté iniciado.");

        } catch (SocketTimeoutException e) {
            System.out.println("Tiempo de espera agotado. El servidor no respondió a tiempo.");

        } catch (EOFException e) {
            System.out.println("El servidor cerró la conexión.");

        } catch (ClassNotFoundException e) {
            System.out.println("Respuesta recibida no reconocida: " + e.getMessage());

        } catch (IOException e) {
            System.out.println("Error de comunicación con el servidor: " + e.getMessage());

        } catch (Exception e) {
            System.out.println("Error inesperado en cliente: " + e.getMessage());
        }
    }

    // Método auxiliar para leer números enteros sin que el programa se caiga.
    private static int leerEntero(Scanner sc, String mensaje) {
        while (true) {
            System.out.print(mensaje);

            String entrada = sc.nextLine();

            try {
                return Integer.parseInt(entrada);

            } catch (NumberFormatException e) {
                System.out.println("Entrada inválida. Debe ingresar un número.");
            }
        }
    }

    // Método auxiliar para mostrar los resultados de búsqueda.
    private static void mostrarResultadosBusqueda(Respuesta res) {
        System.out.println("\n--- Resultados de búsqueda ---");

        if (res.productos == null || res.productos.isEmpty()) {
            System.out.println("No se encontraron productos.");
            return;
        }

        for (Producto p : res.productos) {
            System.out.println(
                    "ID: " + p.id +
                    " | " + p.nombre +
                    " | Stock: " + p.stock +
                    " | Precio: $" + p.precio
            );
        }
    }
}