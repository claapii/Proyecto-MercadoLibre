package com.mycompany.mercadolibre;

import java.util.ArrayList;
import java.util.List;

public class Membresia {

    private static List<NodoInfo> nodos = new ArrayList<>();

    static {
        nodos.add(new NodoInfo(1, "localhost", 7001));
        nodos.add(new NodoInfo(2, "localhost", 7002));
        nodos.add(new NodoInfo(3, "localhost", 7003));
    }

    public static List<NodoInfo> obtenerNodos() {
        return nodos;
    }

    public static NodoInfo buscarPorId(int idNodo) {
        for (NodoInfo nodo : nodos) {
            if (nodo.getIdNodo() == idNodo) {
                return nodo;
            }
        }

        return null;
    }

    public static List<NodoInfo> obtenerOtrosNodos(int miIdNodo) {
        List<NodoInfo> otros = new ArrayList<>();

        for (NodoInfo nodo : nodos) {
            if (nodo.getIdNodo() != miIdNodo) {
                otros.add(nodo);
            }
        }

        return otros;
    }

    public static List<NodoInfo> obtenerNodosActivos() {
        List<NodoInfo> activos = new ArrayList<>();

        for (NodoInfo nodo : nodos) {
            if (nodo.isActivo()) {
                activos.add(nodo);
            }
        }

        return activos;
    }

    public static void marcarNodoInactivo(int idNodo) {
        NodoInfo nodo = buscarPorId(idNodo);

        if (nodo != null) {
            nodo.setActivo(false);
            System.out.println("[MEMBRESIA] Nodo " + idNodo + " marcado como inactivo.");
        }
    }

    public static void marcarNodoActivo(int idNodo) {
        NodoInfo nodo = buscarPorId(idNodo);

        if (nodo != null) {
            nodo.setActivo(true);
            System.out.println("[MEMBRESIA] Nodo " + idNodo + " marcado como activo.");
        }
    }

    public static NodoInfo obtenerCoordinadorInicial() {
        return buscarPorId(3);
    }

    public static void mostrarMembresia() {
        System.out.println("========== LISTA DE MEMBRESIA ==========");

        for (NodoInfo nodo : nodos) {
            System.out.println(nodo);
        }

        System.out.println("========================================");
    }
}