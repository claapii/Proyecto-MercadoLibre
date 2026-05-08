package com.mycompany.mercadolibre;

import java.io.Serializable;

public class PeticionPago implements Serializable {

    private static final long serialVersionUID = 1L;

    public int monto;
    public int saldoCliente;

    public PeticionPago(int monto, int saldoCliente) {
        this.monto = monto;
        this.saldoCliente = saldoCliente;
    }
}