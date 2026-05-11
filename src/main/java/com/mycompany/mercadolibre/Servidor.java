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
            System.out.println("Servidor MercadoLibre corriendo en puerto 5000...");

            while (true) {
                Socket cliente = serverSocket.accept();

                ManejadorCliente manejador = new ManejadorCliente(cliente, this);
                new Thread(manejador).start();
            }

        } catch (IOException e) {
            System.out.println("Error en el servidor MercadoLibre: " + e.getMessage());
        }
    }

    // Función principal 1: buscar productos
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

    // Función principal 2: comprar producto usando WebPay
    public Respuesta comprar(int id, int saldoCliente) {
        Respuesta res = new Respuesta();

        synchronized (productos) {
            for (Producto p : productos) {
                if (p.id == id) {
                    // Primero se verifica si hay stock
                    if (p.stock <= 0) {
                        res.mensaje = "Compra rechazada: producto sin stock.";
                        return res;
                    }

                    // Luego se consulta al servidor WebPay
                    RespuestaPago respuestaPago = procesarPagoWebPay(p.precio, saldoCliente);

                    // Si WebPay no responde, no se realiza la compra
                    if (respuestaPago == null) {
                        res.mensaje = "Compra cancelada: no se pudo conectar con WebPay.";
                        return res;
                    }

                    // Si WebPay aprueba el pago, se descuenta el stock
                    if (respuestaPago.aprobado) {
                        p.stock--;
                        guardarProductos();

                        res.mensaje = "Compra exitosa. " + respuestaPago.mensaje;
                        res.nuevoSaldo = respuestaPago.saldoRestante; // Adjuntamos el nuevo saldo
                        return res;
                    } else {
                        res.mensaje = "Compra rechazada. " + respuestaPago.mensaje;
                        return res;
                    }
                }
            }
        }

        res.mensaje = "Producto no encontrado.";
        return res;
    }

    // Comunicación entre Servidor MercadoLibre y Servidor WebPay
    private RespuestaPago procesarPagoWebPay(int monto, int saldoCliente) {
        try (
            Socket socket = new Socket("localhost", 6000);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
        ) {

            PeticionPago peticionPago = new PeticionPago(monto, saldoCliente);

            out.writeObject(peticionPago);
            out.flush();

            RespuestaPago respuestaPago = (RespuestaPago) in.readObject();

            return respuestaPago;

        } catch (Exception e) {
            System.out.println("Error conectando con WebPay: " + e.getMessage());
            return null;
        }
    }

    // Cargar productos desde archivo txt
    private void cargarProductos() {
        try (BufferedReader br = new BufferedReader(new FileReader("productos.txt"))) {
            String linea;

            while ((linea = br.readLine()) != null) {
                String[] partes = linea.split(";");

                if (partes.length == 4) {
                    productos.add(new Producto(
                            Integer.parseInt(partes[0]),
                            partes[1],
                            Integer.parseInt(partes[2]),
                            Integer.parseInt(partes[3])
                    ));
                }
            }

            System.out.println("Productos cargados correctamente.");

        } catch (IOException e) {
            System.out.println("Error cargando productos.txt: " + e.getMessage());
        } catch (NumberFormatException e) {
            System.out.println("Error en el formato numérico de productos.txt: " + e.getMessage());
        }
    }

    // Guardar productos actualizados en archivo txt
    private void guardarProductos() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("productos.txt"))) {

            for (Producto p : productos) {
                bw.write(p.id + ";" + p.nombre + ";" + p.stock + ";" + p.precio);
                bw.newLine();
            }

        } catch (IOException e) {
            System.out.println("Error guardando productos.txt: " + e.getMessage());
        }
    }
}


