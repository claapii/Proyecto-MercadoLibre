package com.mycompany.mercadolibre;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LoggerSistema {

    private static final String CARPETA_LOGS = "logs";

    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static synchronized void escribir(String nombreArchivo, String linea) {
        try {
            File carpeta = new File(CARPETA_LOGS);

            if (!carpeta.exists()) {
                carpeta.mkdirs();
            }

            File archivo = new File(carpeta, nombreArchivo);

            try (BufferedWriter bw = new BufferedWriter(new FileWriter(archivo, true))) {
                String fechaHora = LocalDateTime.now().format(FORMATO_FECHA);
                bw.write("[" + fechaHora + "] " + linea);
                bw.newLine();
            }

        } catch (IOException e) {
            System.out.println("[LOGGER] Error escribiendo log: " + e.getMessage());
        }
    }

    public static void logNodo(int idNodo, String linea) {
        escribir("log_nodo" + idNodo + ".txt", linea);
    }

    public static void logCarga(String linea) {
        escribir("log_carga.txt", linea);
    }

    public static void logGeneral(String linea) {
        escribir("log_general.txt", linea);
    }
}