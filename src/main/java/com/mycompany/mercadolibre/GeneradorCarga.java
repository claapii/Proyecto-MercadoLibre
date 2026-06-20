package com.mycompany.mercadolibre;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class GeneradorCarga {

    // ── Parámetros de la prueba ──────────────────────────────────────────────
    private static final int    CLIENTES_SIMULTANEOS = 50;
    private static final int    DURACION_SEGUNDOS    = 60;
    private static final int    SALDO_CLIENTE        = 9_999_999;

    // Nodos disponibles — el generador reparte carga en round-robin
    private static final int[][] NODOS = {
        {5001}, {5002}, {5003}
    };

    // IDs de productos a comprar/buscar (deben existir en productos.txt)
    private static final int[]    IDS_PRODUCTOS  = {2, 3, 4, 5, 6, 7, 8, 9, 10};
    private static final String[] TERMINOS_BUSCA = {"zapatillas", "polera", "jeans", "notebook", "mouse", "teclado", "monitor", "audifonos"};

    // ── Métricas globales (thread-safe) ──────────────────────────────────────
    private static final AtomicLong totalPeticiones   = new AtomicLong(0);
    private static final AtomicLong peticionesOk      = new AtomicLong(0);
    private static final AtomicLong peticionesError   = new AtomicLong(0);
    private static final AtomicLong latenciaTotalMs   = new AtomicLong(0);

    // Para calcular p95 guardamos cada latencia
    private static final List<Long> latencias = Collections.synchronizedList(new ArrayList<>());

    // Contador de mensajes de coordinación observados (elecciones)
    private static final AtomicLong mensajesCoordinacion = new AtomicLong(0);

    // Flag para detener los hilos al terminar el tiempo
    private static volatile boolean corriendo = true;

    // ── Main ─────────────────────────────────────────────────────────────────
    public static void main(String[] args) throws InterruptedException {

        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║       GENERADOR DE CARGA — MercadoLibre      ║");
        System.out.println("╠══════════════════════════════════════════════╣");
        System.out.println("║ Clientes simultáneos : " + CLIENTES_SIMULTANEOS + "                      ║");
        System.out.println("║ Duración             : " + DURACION_SEGUNDOS + " segundos             ║");
        System.out.println("║ Nodos objetivo       : 5001, 5002, 5003      ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        System.out.println();

        ExecutorService pool = Executors.newFixedThreadPool(CLIENTES_SIMULTANEOS);
        AtomicInteger roundRobin = new AtomicInteger(0);

        long inicio = System.currentTimeMillis();

        // Lanzar 50 hilos clientes
        for (int i = 0; i < CLIENTES_SIMULTANEOS; i++) {
            final int idCliente = i + 1;
            pool.submit(() -> {
                while (corriendo) {
                    // Round-robin entre nodos
                    int nodoIdx = Math.abs(roundRobin.getAndIncrement() % NODOS.length);
                    int puerto  = NODOS[nodoIdx][0];

                    ejecutarPeticion(idCliente, puerto);
                }
            });
        }

        // Imprimir métricas parciales cada 10 segundos
        ScheduledExecutorService reporter = Executors.newSingleThreadScheduledExecutor();
        reporter.scheduleAtFixedRate(() -> {
            long elapsed = (System.currentTimeMillis() - inicio) / 1000;
            long total   = totalPeticiones.get();
            double tps   = elapsed > 0 ? (double) total / elapsed : 0;
            System.out.printf("[%2ds] Peticiones: %d | OK: %d | Errores: %d | Throughput: %.1f req/s%n",
                elapsed, total, peticionesOk.get(), peticionesError.get(), tps);
        }, 10, 10, TimeUnit.SECONDS);

        // Esperar duración configurada
        Thread.sleep(DURACION_SEGUNDOS * 1000L);
        corriendo = false;

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
        reporter.shutdown();

        long duracionReal = System.currentTimeMillis() - inicio;

        imprimirResultados(duracionReal);
    }

    // ── Ejecuta una petición (buscar o comprar) contra un nodo ───────────────
    private static void ejecutarPeticion(int idCliente, int puerto) {
        // Alternar entre BUSCAR y COMPRAR (60% búsqueda, 40% compra)
        boolean esBusqueda = (Math.random() < 0.6);

        long t0 = System.currentTimeMillis();

        try (
            Socket socket = new Socket("localhost", puerto);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream  in  = new ObjectInputStream(socket.getInputStream())
        ) {
            socket.setSoTimeout(5000);

            Peticion req = new Peticion();

            if (esBusqueda) {
                req.tipo = "BUSCAR";
                req.dato = TERMINOS_BUSCA[(int)(Math.random() * TERMINOS_BUSCA.length)];
            } else {
                req.tipo = "COMPRAR";
                req.dato = String.valueOf(IDS_PRODUCTOS[(int)(Math.random() * IDS_PRODUCTOS.length)]);
                req.saldoCliente = SALDO_CLIENTE;
            }

            out.writeObject(req);
            out.flush();

            Respuesta res = (Respuesta) in.readObject();

            long latencia = System.currentTimeMillis() - t0;
            totalPeticiones.incrementAndGet();
            peticionesOk.incrementAndGet();
            latenciaTotalMs.addAndGet(latencia);
            latencias.add(latencia);

            // Enviar SALIR para cerrar limpio el hilo del servidor
            Peticion salir = new Peticion();
            salir.tipo = "SALIR";
            out.writeObject(salir);
            out.flush();

        } catch (ConnectException e) {
            // Nodo caído — cuenta como error y registra posible elección
            totalPeticiones.incrementAndGet();
            peticionesError.incrementAndGet();
            mensajesCoordinacion.incrementAndGet();
            latencias.add(5000L); // latencia máxima por timeout

        } catch (SocketTimeoutException e) {
            totalPeticiones.incrementAndGet();
            peticionesError.incrementAndGet();
            latencias.add(5000L);

        } catch (Exception e) {
            totalPeticiones.incrementAndGet();
            peticionesError.incrementAndGet();
            latencias.add(5000L);
        }
    }

    // ── Imprime resultados finales ────────────────────────────────────────────
    private static void imprimirResultados(long duracionMs) {
        long total    = totalPeticiones.get();
        long ok       = peticionesOk.get();
        long errores  = peticionesError.get();
        double durSeg = duracionMs / 1000.0;
        double tps    = total / durSeg;
        double latProm = ok > 0 ? (double) latenciaTotalMs.get() / ok : 0;
        double tasaError = total > 0 ? (100.0 * errores / total) : 0;

        // Calcular p95
        List<Long> sorted = new ArrayList<>(latencias);
        Collections.sort(sorted);
        long p95 = sorted.isEmpty() ? 0 : sorted.get((int)(sorted.size() * 0.95));

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║           RESULTADOS DE LA PRUEBA            ║");
        System.out.println("╠══════════════════════════════════════════════╣");
        System.out.printf( "║ Duración real        : %.1f segundos%n", durSeg);
        System.out.printf( "║ Total peticiones     : %d%n", total);
        System.out.printf( "║ Peticiones OK        : %d%n", ok);
        System.out.printf( "║ Peticiones con error : %d%n", errores);
        System.out.printf( "║ Tasa de error        : %.2f%%%n", tasaError);
        System.out.printf( "║ Throughput           : %.2f req/seg%n", tps);
        System.out.printf( "║ Latencia promedio    : %.2f ms%n", latProm);
        System.out.printf( "║ Latencia p95         : %d ms%n", p95);
        System.out.printf( "║ Eventos coordinación : %d%n", mensajesCoordinacion.get());
        System.out.println("╚══════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("INSTRUCCIÓN PARA FALLA INDUCIDA:");
        System.out.println("  Durante la prueba, cierra el Nodo 3 (coordinador).");
        System.out.println("  Observa cómo los errores aumentan y luego el Bully");
        System.out.println("  elige un nuevo coordinador y el sistema se recupera.");
    }
}
