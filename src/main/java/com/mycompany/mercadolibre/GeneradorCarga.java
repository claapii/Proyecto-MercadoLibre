package com.mycompany.mercadolibre;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class GeneradorCarga {

    // ── Parámetros de la prueba ──────────────────────────────────────────────
    private static final int CLIENTES_SIMULTANEOS = 50;
    private static final int DURACION_SEGUNDOS = 60;
    private static final int SALDO_CLIENTE = 9_999_999;

    // Nodos disponibles — el generador reparte carga en round-robin
    private static final int[][] NODOS = {
        {5001}, {5002}, {5003}
    };

    // IDs de productos a comprar/buscar
    private static final int[] IDS_PRODUCTOS = {2, 3, 4, 5, 6, 7, 8, 9, 10};
    private static final String[] TERMINOS_BUSCA = {
        "zapatillas", "polera", "jeans", "notebook",
        "mouse", "teclado", "monitor", "audifonos"
    };

    // ── Métricas globales thread-safe ────────────────────────────────────────
    private static final AtomicLong totalPeticiones = new AtomicLong(0);
    private static final AtomicLong peticionesOk = new AtomicLong(0);
    private static final AtomicLong peticionesError = new AtomicLong(0);
    private static final AtomicLong latenciaTotalMs = new AtomicLong(0);

    private static final List<Long> latencias =
            Collections.synchronizedList(new ArrayList<>());

    private static final AtomicLong erroresConexion = new AtomicLong(0);
    private static final AtomicLong erroresTimeout = new AtomicLong(0);
    private static final AtomicLong erroresOtros = new AtomicLong(0);

    private static volatile boolean corriendo = true;

    public static void main(String[] args) throws InterruptedException {

        imprimirYLog("╔══════════════════════════════════════════════╗");
        imprimirYLog("║       GENERADOR DE CARGA — MercadoLibre      ║");
        imprimirYLog("╠══════════════════════════════════════════════╣");
        imprimirYLog("║ Clientes simultáneos : " + CLIENTES_SIMULTANEOS);
        imprimirYLog("║ Duración             : " + DURACION_SEGUNDOS + " segundos");
        imprimirYLog("║ Nodos objetivo       : 5001, 5002, 5003");
        imprimirYLog("╚══════════════════════════════════════════════╝");
        imprimirYLog("");
        imprimirYLog("[CARGA] Inicio de prueba de tráfico.");
        imprimirYLog("[CARGA] Durante la prueba se puede cerrar el Nodo 3 para inducir falla.");

        ExecutorService pool = Executors.newFixedThreadPool(CLIENTES_SIMULTANEOS);
        AtomicInteger roundRobin = new AtomicInteger(0);

        long inicio = System.currentTimeMillis();

        for (int i = 0; i < CLIENTES_SIMULTANEOS; i++) {
            final int idCliente = i + 1;

            pool.submit(() -> {
                while (corriendo) {
                    int nodoIdx = Math.abs(roundRobin.getAndIncrement() % NODOS.length);
                    int puerto = NODOS[nodoIdx][0];

                    ejecutarPeticion(idCliente, puerto);
                }
            });
        }

        ScheduledExecutorService reporter = Executors.newSingleThreadScheduledExecutor();

        reporter.scheduleAtFixedRate(() -> {
            long elapsed = (System.currentTimeMillis() - inicio) / 1000;
            long total = totalPeticiones.get();
            double tps = elapsed > 0 ? (double) total / elapsed : 0;

            String linea = String.format(
                    "[%2ds] Peticiones: %d | OK: %d | Errores: %d | Throughput: %.2f req/s",
                    elapsed,
                    total,
                    peticionesOk.get(),
                    peticionesError.get(),
                    tps
            );

            imprimirYLog(linea);

        }, 10, 10, TimeUnit.SECONDS);

        Thread.sleep(DURACION_SEGUNDOS * 1000L);
        corriendo = false;

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        reporter.shutdown();

        long duracionReal = System.currentTimeMillis() - inicio;

        imprimirResultados(duracionReal);
    }

    private static void ejecutarPeticion(int idCliente, int puerto) {
        boolean esBusqueda = Math.random() < 0.6;

        long t0 = System.currentTimeMillis();

        try (
            Socket socket = new Socket("localhost", puerto);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream())
        ) {
            socket.setSoTimeout(5000);

            Peticion req = new Peticion();

            if (esBusqueda) {
                req.tipo = "BUSCAR";
                req.dato = TERMINOS_BUSCA[(int) (Math.random() * TERMINOS_BUSCA.length)];
            } else {
                req.tipo = "COMPRAR";
                req.dato = String.valueOf(
                        IDS_PRODUCTOS[(int) (Math.random() * IDS_PRODUCTOS.length)]
                );
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

            Peticion salir = new Peticion();
            salir.tipo = "SALIR";
            out.writeObject(salir);
            out.flush();

        } catch (ConnectException e) {
            registrarError(idCliente, puerto, "CONEXION", e.getMessage());

        } catch (SocketTimeoutException e) {
            registrarError(idCliente, puerto, "TIMEOUT", e.getMessage());

        } catch (Exception e) {
            registrarError(idCliente, puerto, "ERROR_GENERAL", e.getMessage());
        }
    }

    private static void registrarError(int idCliente, int puerto, String tipoError, String detalle) {
        totalPeticiones.incrementAndGet();
        peticionesError.incrementAndGet();
        latencias.add(5000L);

        if ("CONEXION".equals(tipoError)) {
            erroresConexion.incrementAndGet();
        } else if ("TIMEOUT".equals(tipoError)) {
            erroresTimeout.incrementAndGet();
        } else {
            erroresOtros.incrementAndGet();
        }

        String linea = "[CARGA][Cliente " + idCliente + "] "
                + "Error tipo " + tipoError
                + " contra puerto " + puerto
                + " | Detalle: " + detalle;

        imprimirYLog(linea);
    }

    private static void imprimirResultados(long duracionMs) {
        long total = totalPeticiones.get();
        long ok = peticionesOk.get();
        long errores = peticionesError.get();

        double durSeg = duracionMs / 1000.0;
        double tps = durSeg > 0 ? total / durSeg : 0;
        double latProm = ok > 0 ? (double) latenciaTotalMs.get() / ok : 0;
        double tasaError = total > 0 ? (100.0 * errores / total) : 0;

        List<Long> sorted;

        synchronized (latencias) {
            sorted = new ArrayList<>(latencias);
        }

        Collections.sort(sorted);

        long p95 = 0;

        if (!sorted.isEmpty()) {
            int indiceP95 = (int) Math.ceil(sorted.size() * 0.95) - 1;
            indiceP95 = Math.max(0, Math.min(indiceP95, sorted.size() - 1));
            p95 = sorted.get(indiceP95);
        }

        imprimirYLog("");
        imprimirYLog("╔══════════════════════════════════════════════╗");
        imprimirYLog("║           RESULTADOS DE LA PRUEBA            ║");
        imprimirYLog("╠══════════════════════════════════════════════╣");
        imprimirYLog(String.format("Duración real        : %.1f segundos", durSeg));
        imprimirYLog("Total peticiones     : " + total);
        imprimirYLog("Peticiones OK        : " + ok);
        imprimirYLog("Peticiones con error : " + errores);
        imprimirYLog(String.format("Tasa de error        : %.2f%%", tasaError));
        imprimirYLog(String.format("Throughput           : %.2f req/seg", tps));
        imprimirYLog(String.format("Latencia promedio    : %.2f ms", latProm));
        imprimirYLog("Latencia p95         : " + p95 + " ms");
        imprimirYLog("Errores conexión     : " + erroresConexion.get());
        imprimirYLog("Errores timeout      : " + erroresTimeout.get());
        imprimirYLog("Errores otros        : " + erroresOtros.get());
        imprimirYLog("╚══════════════════════════════════════════════╝");

        imprimirYLog("");
        imprimirYLog("[CARGA] Fin de prueba de tráfico.");
        imprimirYLog("[CARGA] Si se cerró un nodo durante la prueba, revisar logs/log_nodoX.txt para ver detección de falla y elección Bully.");
    }

    private static void imprimirYLog(String linea) {
        System.out.println(linea);
        LoggerSistema.logCarga(linea);
    }
}