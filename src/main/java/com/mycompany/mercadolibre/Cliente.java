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

            System.out.println("1. Buscar");
            System.out.println("2. Comprar");

            int op = sc.nextInt();
            sc.nextLine();

            Peticion req = new Peticion();

            if (op == 1) {
                req.tipo = "BUSCAR";
                System.out.print("Nombre: ");
                req.dato = sc.nextLine();

            } else if (op == 2) {
                req.tipo = "COMPRAR";

                System.out.print("ID: ");
                req.dato = sc.nextLine();

                System.out.print("Saldo disponible: ");
                req.saldoCliente = sc.nextInt();
            }

            // enviar petición
            out.writeObject(req);

            // recibir respuesta
            Respuesta res = (Respuesta) in.readObject();

            // mostrar resultado
            if (req.tipo.equals("BUSCAR")) {
                for (Producto p : res.productos) {
                    System.out.println(p.id + " - " + p.nombre +
                            " | Stock: " + p.stock +
                            " | Precio: " + p.precio);
                }
            } else {
                System.out.println(res.mensaje);
            }

        } catch (Exception e) {
            System.out.println("Error cliente: " + e.getMessage());
        }
    }
}
