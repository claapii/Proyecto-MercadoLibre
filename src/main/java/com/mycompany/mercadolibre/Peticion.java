package com.mycompany.mercadolibre;

import java.io.Serializable;

public class Peticion implements Serializable {

    private static final long serialVersionUID = 1L;

    public String tipo; // "BUSCAR" o "COMPRAR"
    public String dato;
    public int saldoCliente;
}