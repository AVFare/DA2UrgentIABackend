package com.urgentia.gateway;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/** Servicio de mentira para los tests: responde 200 con la ruta y recuerda los headers que recibio. */
final class ServicioFalso {

    private final HttpServer servidor;
    private volatile Headers ultimosHeaders;

    ServicioFalso() {
        try {
            servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        servidor.createContext("/", exchange -> {
            ultimosHeaders = exchange.getRequestHeaders();
            byte[] cuerpo = exchange.getRequestURI().getPath().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, cuerpo.length);
            try (OutputStream salida = exchange.getResponseBody()) {
                salida.write(cuerpo);
            }
        });
        servidor.start();
    }

    String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    /** Valor del header que llego en el ultimo pedido, o null si no vino. */
    String headerRecibido(String nombre) {
        return ultimosHeaders == null ? null : ultimosHeaders.getFirst(nombre);
    }

    void detener() {
        servidor.stop(0);
    }
}