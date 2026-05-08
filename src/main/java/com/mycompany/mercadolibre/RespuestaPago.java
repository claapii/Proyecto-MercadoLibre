package com.mycompany.mercadolibre;

import java.io.Serializable;

public class RespuestaPago implements Serializable {

    private static final long serialVersionUID = 1L;

    public boolean aprobado;
    public String mensaje;
    public int saldoRestante;
}