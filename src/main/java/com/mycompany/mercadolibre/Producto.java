package com.mycompany.mercadolibre;

import java.io.Serializable;

public class Producto implements Serializable {

    private static final long serialVersionUID = 1L;

    public int id;
    public String nombre;
    public int stock;
    public int precio;

    // NUEVO: Guarda el tiempo lógico de Lamport de la última actualización
    // Permite descartar mensajes SYNC_STOCK desordenados por la red
    public int ultimoLamport;

    public Producto(int id, String nombre, int stock, int precio) {
        this.id = id;
        this.nombre = nombre;
        this.stock = stock;
        this.precio = precio;
        this.ultimoLamport = 0;
    }
}