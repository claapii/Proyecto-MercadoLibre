package com.mycompany.mercadolibre;

import java.net.*;
import java.io.*;
import java.util.*;

public class Servidor {

    private List<Producto> productos = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) {
        new Servidor().iniciar();
    }

    private void iniciar() {
        cargarProductos();

        try (ServerSocket serverSocket = new ServerSocket(5000)) {
            System.out.println("Servidor corriendo...");

            while (true) {
                Socket cliente = serverSocket.accept();
                ManejadorCliente manejador = new ManejadorCliente(cliente, this);
                new Thread(manejador).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    //Métodos para las dos funciones principales del sistema
    public List<Producto> buscar(String nombre) {
        List<Producto> resultado = new ArrayList<>();

        synchronized (productos) {
            for (Producto p : productos) {
                if (p.nombre.toLowerCase().contains(nombre.toLowerCase())) {
                    resultado.add(p);
                }
            }
        }

        return resultado;
    }

    public String comprar(int id) {
        synchronized (productos) {
            for (Producto p : productos) {
                if (p.id == id) {
                    if (p.stock > 0) {
                        p.stock--;
                        guardarProductos();
                        return "Compra Exitosa!";
                    } else {
                        return "Sin Stock!";
                    }
                }
            }
        }
        return "No encontrado.";
    }
    
    //Métodos para el manejo de la base de datos (txt)
    private void cargarProductos() {
        try (BufferedReader br = new BufferedReader(new FileReader("productos.txt"))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                String[] partes = linea.split(";");

                productos.add(new Producto(
                    Integer.parseInt(partes[0]),
                    partes[1],
                    Integer.parseInt(partes[2]),
                    Integer.parseInt(partes[3])
                ));
            }
        } catch (IOException e) {
            System.out.println("Error cargando productos");
        }
    }

    private void guardarProductos() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("productos.txt"))) {
            for (Producto p : productos) {
                bw.write(p.id + ";" + p.nombre + ";" + p.stock + ";" + p.precio);
                bw.newLine();
            }
        } catch (IOException e) {
            System.out.println("Error guardando productos");
        }
    }
}


