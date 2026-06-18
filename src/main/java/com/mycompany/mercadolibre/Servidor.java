package com.mycompany.mercadolibre;

import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Servidor {

    // Datos propios del nodo
    private RelojLamport relojLamport;
    private int idNodo;
    private int puertoClientes;
    private int puertoNodos;

    // Lista compartida entre los clientes conectados a este nodo
    private List<Producto> productos = Collections.synchronizedList(new ArrayList<>());

    // Pool fijo de hilos para controlar clientes simultáneos
    private static final int MAX_CLIENTES = 10;

    public static void main(String[] args) {

        if (args.length < 2) {
            System.out.println("Uso: java Servidor <idNodo> <puertoClientes>");
            System.out.println("Ejemplo Nodo 1: java Servidor 1 5001");
            System.out.println("Ejemplo Nodo 2: java Servidor 2 5002");
            System.out.println("Ejemplo Nodo 3: java Servidor 3 5003");
            return;
        }

        int idNodo = Integer.parseInt(args[0]);
        int puertoClientes = Integer.parseInt(args[1]);

        new Servidor(idNodo, puertoClientes).iniciar();
    }

    public Servidor(int idNodo, int puertoClientes) {
        this.idNodo = idNodo;
        this.puertoClientes = puertoClientes;
        this.puertoNodos = calcularPuertoNodos(idNodo);
        this.relojLamport = new RelojLamport();
    }

    private int calcularPuertoNodos(int idNodo) {
        return 7000 + idNodo;
    }

    private void iniciar() {
        cargarProductos();

        // Inicia receptor interno para mensajes entre nodos.
        ReceptorMensajesNodo receptorNodos = new ReceptorMensajesNodo(idNodo, puertoNodos);
        receptorNodos.start();

        ExecutorService poolClientes = Executors.newFixedThreadPool(MAX_CLIENTES);

        try (ServerSocket serverSocket = new ServerSocket(puertoClientes)) {

            System.out.println("========================================");
            System.out.println("Nodo MercadoLibre " + idNodo + " iniciado.");
            System.out.println("Puerto clientes: " + puertoClientes);
            System.out.println("Puerto mensajes entre nodos: " + puertoNodos);
            System.out.println("Pool de clientes activo. Máximo simultáneos: " + MAX_CLIENTES);
            System.out.println("Coordinador inicial: Nodo " 
                    + Membresia.obtenerCoordinadorInicial().getIdNodo());
            System.out.println("========================================");

            registrarEvento("Nodo iniciado. Puerto clientes: " 
                    + puertoClientes + ", puerto nodos: " + puertoNodos);

            while (true) {
                Socket cliente = serverSocket.accept();

                registrarEvento("Cliente conectado desde " + cliente.getInetAddress());

                // Evita que un cliente quede conectado sin responder para siempre.
                cliente.setSoTimeout(15000);

                ManejadorCliente manejador = new ManejadorCliente(cliente, this);

                // En vez de crear hilos ilimitados, usamos el pool.
                poolClientes.execute(manejador);
            }

        } catch (IOException e) {
            registrarEvento("Error en Nodo MercadoLibre " + idNodo + ": " + e.getMessage());
            System.out.println("Error en Nodo MercadoLibre " + idNodo + ": " + e.getMessage());

        } finally {
            poolClientes.shutdown();
        }
    }

    // Función principal 1: buscar productos
    public List<Producto> buscar(String nombre) {
        registrarEvento("Solicitud de búsqueda recibida: " + nombre);

        List<Producto> resultado = new ArrayList<>();

        if (nombre == null) {
            registrarEvento("Búsqueda cancelada: nombre nulo.");
            return resultado;
        }

        synchronized (productos) {
            for (Producto p : productos) {
                if (p.nombre.toLowerCase().contains(nombre.toLowerCase())) {
                    resultado.add(p);
                }
            }
        }

        registrarEvento("Búsqueda finalizada. Resultados encontrados: " + resultado.size());

        return resultado;
    }

    // Función principal 2: comprar producto usando WebPay
    public Respuesta comprar(int id, int saldoCliente) {
        registrarEvento("Solicitud de compra recibida. Producto ID: " + id);

        Respuesta res = new Respuesta();

        synchronized (productos) {
            for (Producto p : productos) {
                if (p.id == id) {

                    registrarEvento("Producto encontrado para compra. Producto ID: " + id);

                    // Región crítica:
                    // Varios clientes podrían intentar comprar el mismo producto al mismo tiempo.
                    if (p.stock <= 0) {
                        registrarEvento("Compra rechazada por falta de stock. Producto ID: " + id);

                        res.mensaje = "Compra rechazada: producto sin stock.";
                        return res;
                    }

                    // Se consulta al servidor WebPay.
                    registrarEvento("Enviando solicitud de pago a WebPay. Producto ID: " + id);

                    RespuestaPago respuestaPago = procesarPagoWebPay(p.precio, saldoCliente);

                    // Si WebPay no responde, no se modifica el stock.
                    if (respuestaPago == null) {
                        registrarEvento("Compra cancelada. WebPay no respondió. Producto ID: " + id);

                        res.mensaje = "Compra cancelada: no se pudo conectar con WebPay.";
                        return res;
                    }

                    // Si WebPay aprueba el pago, se descuenta stock.
                    if (respuestaPago.aprobado) {
                        p.stock--;
                        guardarProductos();

                        registrarEvento("Compra aprobada. Stock actualizado. Producto ID: " + id
                                + ". Stock restante: " + p.stock);

                        // Más adelante aquí enviaremos SYNC_STOCK a los otros nodos.
                        // Por ahora dejamos el punto marcado para la siguiente etapa.

                        res.mensaje = "Compra exitosa. " + respuestaPago.mensaje;
                        res.nuevoSaldo = respuestaPago.saldoRestante;
                        return res;

                    } else {
                        registrarEvento("Compra rechazada por WebPay. Producto ID: " + id);

                        res.mensaje = "Compra rechazada. " + respuestaPago.mensaje;
                        return res;
                    }
                }
            }
        }

        registrarEvento("Compra rechazada. Producto no encontrado. Producto ID: " + id);

        res.mensaje = "Producto no encontrado.";
        return res;
    }

    // Comunicación entre el nodo MercadoLibre y el servidor WebPay
    private RespuestaPago procesarPagoWebPay(int monto, int saldoCliente) {
        try (
            Socket socket = new Socket("localhost", 6000);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream())
        ) {
            // Evita bloqueo si WebPay no responde.
            socket.setSoTimeout(10000);

            registrarEvento("Conexión establecida con WebPay. Monto: " + monto);

            PeticionPago peticionPago = new PeticionPago(monto, saldoCliente);

            out.writeObject(peticionPago);
            out.flush();

            registrarEvento("Petición de pago enviada a WebPay. Monto: " + monto);

            RespuestaPago respuestaPago = (RespuestaPago) in.readObject();

            registrarEvento("Respuesta recibida desde WebPay. Aprobado: " + respuestaPago.aprobado);

            return respuestaPago;

        } catch (SocketTimeoutException e) {
            registrarEvento("Tiempo de espera agotado conectando con WebPay.");
            System.out.println("Tiempo de espera agotado conectando con WebPay.");
            return null;

        } catch (ConnectException e) {
            registrarEvento("No se pudo conectar con WebPay.");
            System.out.println("No se pudo conectar con WebPay. Verifique que el servidor WebPay esté activo.");
            return null;

        } catch (Exception e) {
            registrarEvento("Error conectando con WebPay: " + e.getMessage());
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

        } catch (FileNotFoundException e) {
            System.out.println("No se encontró productos.txt. El nodo iniciará sin productos.");

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

            registrarEvento("Archivo productos.txt actualizado correctamente.");

        } catch (IOException e) {
            registrarEvento("Error guardando productos.txt: " + e.getMessage());
            System.out.println("Error guardando productos.txt: " + e.getMessage());
        }
    }

    public int getIdNodo() {
        return idNodo;
    }

    public int getPuertoClientes() {
        return puertoClientes;
    }

    public int getPuertoNodos() {
        return puertoNodos;
    }

    // Lo dejo también para no romper código antiguo que use getPuerto()
    public int getPuerto() {
        return puertoClientes;
    }

    public synchronized int avanzarRelojLamport() {
        return relojLamport.incrementar();
    }

    public synchronized void registrarEvento(String descripcion) {
        int tiempo = relojLamport.incrementar();

        System.out.println(
                "[Lamport=" + tiempo + "]"
                + "[Nodo " + idNodo + "] "
                + descripcion
        );
    }
}