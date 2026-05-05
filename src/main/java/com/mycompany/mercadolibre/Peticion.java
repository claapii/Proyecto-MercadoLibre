package com.mycompany.mercadolibre;

import java.io.Serializable;

public class Peticion implements Serializable {
    public String tipo; // "BUSCAR" o "COMPRAR"
    public String dato;
}