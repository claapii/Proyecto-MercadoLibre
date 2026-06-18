package com.mycompany.mercadolibre;

import java.io.Serializable;

public class NodoInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private int idNodo;
    private String host;
    private int puerto;
    private boolean activo;

    public NodoInfo(int idNodo, String host, int puerto) {
        this.idNodo = idNodo;
        this.host = host;
        this.puerto = puerto;
        this.activo = true;
    }

    public int getIdNodo() {
        return idNodo;
    }

    public String getHost() {
        return host;
    }

    public int getPuerto() {
        return puerto;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    @Override
    public String toString() {
        return "NodoInfo{" +
                "idNodo=" + idNodo +
                ", host='" + host + '\'' +
                ", puerto=" + puerto +
                ", activo=" + activo +
                '}';
    }
}