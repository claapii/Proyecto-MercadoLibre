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

    // Comunicador reutilizable para enviar mensajes a otros nodos
    private ComunicadorNodos comunicador = new ComunicadorNodos();

    // ID del coordinador actual (arranca siendo el nodo 3, como en Membresia)
    private volatile int idCoordinadorActual;

    // Algoritmo de elección de coordinador
    private AlgoritmoBully bully;

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
        this.idCoordinadorActual = Membresia.obtenerCoordinadorInicial().getIdNodo();
        this.bully = new AlgoritmoBully(this);
    }

    private int calcularPuertoNodos(int idNodo) {
        return 7000 + idNodo;
    }

    private void iniciar() {
        cargarProductos();

        // 1. Detector de fallos (necesita referencia al Servidor para el callback)
        DetectorFallos detector = new DetectorFallos(idNodo, this);

        // 2. Receptor de mensajes entre nodos (comparte relojLamport, detector y bully)
        ReceptorMensajesNodo receptorNodos = new ReceptorMensajesNodo(
            idNodo, puertoNodos, relojLamport, detector, this
        );
        receptorNodos.start();

        // 3. Emisor de heartbeats periódicos hacia los otros nodos
        EmisorHeartbeat emisorHB = new EmisorHeartbeat(idNodo, puertoNodos, relojLamport);
        emisorHB.start();

        // 4. Detector empieza a vigilar los timeouts
        detector.start();

        // 5. Si no soy el coordinador inicial, pido el estado actual al arrancar
        solicitarTransferenciaEstado();

        ExecutorService poolClientes = Executors.newFixedThreadPool(MAX_CLIENTES);

        try (ServerSocket serverSocket = new ServerSocket(puertoClientes)) {

            System.out.println("========================================");
            System.out.println("Nodo MercadoLibre " + idNodo + " iniciado.");
            System.out.println("Puerto clientes: " + puertoClientes);
            System.out.println("Puerto mensajes entre nodos: " + puertoNodos);
            System.out.println("Pool de clientes activo. Máximo simultáneos: " + MAX_CLIENTES);
            System.out.println("Coordinador actual: Nodo " + idCoordinadorActual);
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

    // El DetectorFallos llama a este método cuando detecta que un nodo cayó
    public void notificarNodoCaido(int idNodoCaido) {
        registrarEvento("Notificación: Nodo " + idNodoCaido + " caído.");

        if (idNodoCaido == idCoordinadorActual) {
            registrarEvento("¡El coordinador (Nodo " + idNodoCaido + ") cayó! "
                + "Iniciando elección Bully...");
            bully.iniciarEleccion();
        }
    }

    // El DetectorFallos llama a este método cuando un nodo se reintegra
    public void notificarNodoReintegrado(int idNodoReintegrado) {
        registrarEvento("Nodo " + idNodoReintegrado + " se reintegró. Enviando estado actual.");

        // Solo el coordinador envía el estado al nodo que vuelve
        if (idNodoReintegrado != idNodo && idCoordinadorActual == idNodo) {
            enviarEstadoActualANodo(idNodoReintegrado);
        }
    }

    // Expone el Bully al ReceptorMensajesNodo para procesar mensajes de elección
    public AlgoritmoBully getBully() {
        return bully;
    }

    // Permite actualizar el coordinador cuando llega un mensaje COORDINADOR
    public void actualizarCoordinador(int nuevoIdCoordinador) {
        this.idCoordinadorActual = nuevoIdCoordinador;
        registrarEvento("Coordinador actualizado → Nodo " + nuevoIdCoordinador);
    }

    public int getIdCoordinadorActual() {
        return idCoordinadorActual;
    }

    // Función principal 1: buscar productos
    public List<Producto> buscar(String nombre) {
        registrarEvento("Solicitud de búsqueda recibida: " + nombre);

        List<Producto> resultado = new ArrayList<>();

        if (nombre == null || nombre.trim().isEmpty()) {
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

                    // Si WebPay aprueba el pago, se descuenta stock y se sincroniza.
                    if (respuestaPago.aprobado) {
                        p.stock--;

                        // Estampar el Lamport de esta modificación en el producto
                        int tiempoCompra = relojLamport.incrementar();
                        p.ultimoLamport = tiempoCompra;

                        guardarProductos();

                        registrarEvento("Compra aprobada. Stock actualizado. Producto ID: " + id
                                + ". Stock restante: " + p.stock
                                + " [Lamport=" + tiempoCompra + "]");

                        difundirSyncStock(id, p.stock, tiempoCompra);

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

    // Difunde SYNC_STOCK a los otros nodos con el reloj Lamport estampado
    private void difundirSyncStock(int idProducto, int nuevoStock, int tiempoLamport) {
        List<NodoInfo> otros = Membresia.obtenerOtrosNodos(idNodo);

        for (NodoInfo destino : otros) {
            MensajeNodo msg = new MensajeNodo(
                "SYNC_STOCK",
                idNodo,
                puertoNodos,
                destino.getIdNodo(),
                tiempoLamport,  // usamos el Lamport de la compra, no uno nuevo
                idProducto + ":" + nuevoStock
            );

            boolean ok = comunicador.enviarMensaje(destino, msg);

            if (ok) {
                System.out.println("[Lamport=" + tiempoLamport + "][Nodo " + idNodo
                    + "] SYNC_STOCK enviado a Nodo " + destino.getIdNodo()
                    + " -> Producto " + idProducto + ", Stock: " + nuevoStock);
            } else {
                System.out.println("[Lamport=" + tiempoLamport + "][Nodo " + idNodo
                    + "] SYNC_STOCK fallido hacia Nodo " + destino.getIdNodo()
                    + " (nodo inactivo o caído)");
            }
        }
    }

    // Validar orden de Lamport antes de aplicar sincronización (Punto 3)
    public void aplicarSincronizacionStock(int idProducto, int nuevoStock, int tiempoLamportRecibido) {
        boolean encontrado = false;
        boolean aplicado = false;

        synchronized (productos) {
            for (Producto p : productos) {
                if (p.id == idProducto) {
                    encontrado = true;

                    if (tiempoLamportRecibido > p.ultimoLamport) {
                        p.stock = nuevoStock;
                        p.ultimoLamport = tiempoLamportRecibido;
                        aplicado = true;
                        registrarEvento("[Lamport] Sync aplicada. Producto ID: " + idProducto
                                + " -> Stock: " + nuevoStock
                                + " (Lamport recibido: " + tiempoLamportRecibido + ")");
                    } else {
                        registrarEvento("[Lamport] Sync ignorada por obsoleta. Producto ID: " + idProducto
                                + " | Msg: " + tiempoLamportRecibido
                                + " <= Local: " + p.ultimoLamport);
                    }
                    break;
                }
            }
        }

        // Guardar fuera del synchronized con snapshot (evita I/O bloqueante)
        if (aplicado) {
            guardarProductos();
        }

        if (!encontrado) {
            registrarEvento("Alerta: SYNC_STOCK para Producto ID " + idProducto
                    + " no encontrado en catálogo local.");
        }
    }

    // Envía el estado completo del catálogo a un nodo que se reintegró (Punto 2)
    public void enviarEstadoActualANodo(int idNodoDestino) {
        NodoInfo destino = Membresia.buscarPorId(idNodoDestino);
        if (destino == null || !destino.isActivo()) return;

        List<Producto> snapshot;
        synchronized (productos) {
            snapshot = new ArrayList<>(productos);
        }

        registrarEvento("Enviando transferencia de estado completa al Nodo " + idNodoDestino);

        for (Producto p : snapshot) {
            MensajeNodo msg = new MensajeNodo(
                "SYNC_STOCK",
                idNodo,
                puertoNodos,
                idNodoDestino,
                p.ultimoLamport,  // usamos el Lamport real de cada producto
                p.id + ":" + p.stock
            );
            comunicador.enviarMensaje(destino, msg);
        }

        registrarEvento("Transferencia de estado completada para Nodo " + idNodoDestino);
    }

    // Al arrancar, si no soy el coordinador pido el estado actual (Punto 2)
    public void solicitarTransferenciaEstado() {
        // Si soy el coordinador inicial no pido nada
        if (idCoordinadorActual == this.idNodo) return;

        NodoInfo coord = Membresia.buscarPorId(idCoordinadorActual);
        if (coord != null && coord.isActivo()) {
            registrarEvento("Solicitando transferencia de estado al Coordinador (Nodo "
                    + idCoordinadorActual + ")");

            int tiempo = relojLamport.incrementar();
            MensajeNodo msg = new MensajeNodo(
                "SOLICITAR_ESTADO",
                idNodo,
                puertoNodos,
                idCoordinadorActual,
                tiempo,
                "pull_state"
            );
            comunicador.enviarMensaje(coord, msg);
        }
    }

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

    private void guardarProductos() {
        // 1. Snapshot bajo el candado (operación rápida, solo memoria)
        List<Producto> snapshot;
        synchronized (productos) {
            snapshot = new ArrayList<>(productos);
        }

        // 2. Escribir al disco fuera del candado (operación lenta, no bloquea a nadie)
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("productos.txt"))) {
            for (Producto p : snapshot) {
                bw.write(p.id + ";" + p.nombre + ";" + p.stock + ";" + p.precio);
                bw.newLine();
            }
            registrarEvento("Archivo productos.txt actualizado correctamente.");

        } catch (IOException e) {
            registrarEvento("Error guardando productos.txt: " + e.getMessage());
            System.out.println("Error guardando productos.txt: " + e.getMessage());
        }
    }

    public int getIdNodo() { return idNodo; }
    public int getPuertoClientes() { return puertoClientes; }
    public int getPuertoNodos() { return puertoNodos; }
    public int getPuerto() { return puertoClientes; }

    public synchronized int avanzarRelojLamport() {
        return relojLamport.incrementar();
    }

    public synchronized void registrarEvento(String descripcion) {
        int tiempo = relojLamport.incrementar();
        
        String linea = "[Lamport=" + tiempo + "]"
                + "[Nodo " + idNodo + "]"
                + descripcion;
        
        System.out.println(linea);
        
        //Esto es para guardar el mismo evento en archivo para después adjuntarlo en la entrega
        LoggerSistema.logNodo(idNodo, linea);
    }
}