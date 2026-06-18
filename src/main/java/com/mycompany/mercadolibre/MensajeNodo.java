package com.mycompany.mercadolibre;

import java.io.Serializable;

public class MensajeNodo implements Serializable {

    private static final long serialVersionUID = 1L;

    private String tipoMensaje;
    private int idOrigen;
    private int puertoOrigen;
    private int idDestino;
    private int relojLamport;
    private String contenido;

    public MensajeNodo(String tipoMensaje, int idOrigen, int puertoOrigen,
                       int idDestino, int relojLamport, String contenido) {
        this.tipoMensaje = tipoMensaje;
        this.idOrigen = idOrigen;
        this.puertoOrigen = puertoOrigen;
        this.idDestino = idDestino;
        this.relojLamport = relojLamport;
        this.contenido = contenido;
    }

    public String getTipoMensaje() {
        return tipoMensaje;
    }

    public int getIdOrigen() {
        return idOrigen;
    }

    public int getPuertoOrigen() {
        return puertoOrigen;
    }

    public int getIdDestino() {
        return idDestino;
    }

    public int getRelojLamport() {
        return relojLamport;
    }

    public String getContenido() {
        return contenido;
    }

    @Override
    public String toString() {
        return "MensajeNodo{" +
                "tipoMensaje='" + tipoMensaje + '\'' +
                ", idOrigen=" + idOrigen +
                ", puertoOrigen=" + puertoOrigen +
                ", idDestino=" + idDestino +
                ", relojLamport=" + relojLamport +
                ", contenido='" + contenido + '\'' +
                '}';
    }
}
