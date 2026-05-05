package com.mycompany.mercadolibre;

import java.io.Serializable;

public class Producto implements Serializable {
    public int id;
    public String nombre;
    public int stock;
    public int precio;

    public Producto(int id, String nombre, int stock, int precio) {
        this.id = id;
        this.nombre = nombre;
        this.stock = stock;
        this.precio = precio;
    }
}