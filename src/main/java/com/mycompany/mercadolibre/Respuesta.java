package com.mycompany.mercadolibre;

import java.io.Serializable;
import java.util.List;

public class Respuesta implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    public String mensaje;
    public List<Producto> productos;
    public int nuevoSaldo = -1; // -1 indica que el saldo no sufrió modificaciones
}