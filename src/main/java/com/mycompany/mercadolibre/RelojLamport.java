package com.mycompany.mercadolibre;

public class RelojLamport {

    private int tiempo;

    public RelojLamport() {
        this.tiempo = 0;
    }

    // Se llama cuando ocurre un evento local en el nodo
    public synchronized int incrementar() {
        tiempo++;
        return tiempo;
    }

    // Se llama cuando llega un mensaje desde otro nodo
    public synchronized int actualizar(int tiempoRecibido) {
        tiempo = Math.max(tiempo, tiempoRecibido) + 1;
        return tiempo;
    }

    // Obtiene el valor actual del reloj
    public synchronized int getTiempo() {
        return tiempo;
    }
}