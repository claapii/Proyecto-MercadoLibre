package com.mycompany.mercadolibre;

import java.io.Serializable;
import java.util.List;

public class Respuesta implements Serializable {
    public String mensaje;
    public List<Producto> productos;
}
