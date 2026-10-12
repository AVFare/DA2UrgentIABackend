package com.urgentia.ticket.infrastructure;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

/**
 * Servidor HTTP de mentira para los tests (hace de classification-service o de los
 * suscriptores de eventos). Responde lo que se le programe por ruta y guarda cada
 * pedido en el orden en que llego.
 */
public final class ServidorFalso {

    /** Pedido que recibio el servidor. */
    public record Pedido(String metodo, String ruta, Map<String, String> headers, String cuerpo) {

        public String header(String nombre) {
            return headers.get(nombre.toLowerCase());
        }
    }

    /** Respuesta programada. */
    public record Respuesta(int status, String cuerpo, Duration demora) {

        public static Respuesta json(int status, String cuerpo) {
            return new Respuesta(status, cuerpo, Duration.ZERO);
        }

        public static Respuesta vacia(int status) {
            return new Respuesta(status, "", Duration.ZERO);
        }

        public Respuesta conDemora(Duration demora) {
            return new Respuesta(status, cuerpo, demora);
        }
    }

    private final HttpServer servidor;
    private final List<Pedido> pedidos = new CopyOnWriteArrayList<>();
    private final Map<String, Deque<Respuesta>> secuencias = new ConcurrentHashMap<>();
    private final Map<String, Respuesta> fijas = new ConcurrentHashMap<>();
    private volatile Respuesta porDefecto = Respuesta.vacia(404);

    public ServidorFalso() {
        try {
            servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        servidor.setExecutor(Executors.newCachedThreadPool(tarea -> {
            Thread hilo = new Thread(tarea, "servidor-falso");
            hilo.setDaemon(true);
            return hilo;
        }));
        servidor.createContext("/", this::atender);
        servidor.start();
    }

    public String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    /** Responde siempre lo mismo en esa ruta. */
    public ServidorFalso responder(String ruta, Respuesta respuesta) {
        secuencias.remove(ruta);
        fijas.put(ruta, respuesta);
        return this;
    }

    /** Responde en orden; cuando se acaban, sigue con la ultima. */
    public ServidorFalso responderEnOrden(String ruta, Respuesta... respuestas) {
        Deque<Respuesta> cola = new ArrayDeque<>(List.of(respuestas));
        fijas.put(ruta, respuestas[respuestas.length - 1]);
        secuencias.put(ruta, cola);
        return this;
    }

    public ServidorFalso responderPorDefecto(Respuesta respuesta) {
        this.porDefecto = respuesta;
        return this;
    }

    /** Olvida los pedidos y las respuestas programadas. */
    public void limpiar() {
        pedidos.clear();
        secuencias.clear();
        fijas.clear();
        porDefecto = Respuesta.vacia(404);
    }

    public List<Pedido> pedidos() {
        return List.copyOf(pedidos);
    }

    public List<Pedido> pedidos(Predicate<Pedido> condicion) {
        return pedidos.stream().filter(condicion).toList();
    }

    /** Espera (hasta el tiempo dado) a que lleguen al menos {@code cantidad} pedidos que cumplan la condicion. */
    public List<Pedido> esperarPedidos(Predicate<Pedido> condicion, int cantidad, Duration hasta) {
        long limite = System.nanoTime() + hasta.toNanos();
        while (System.nanoTime() < limite) {
            List<Pedido> encontrados = pedidos(condicion);
            if (encontrados.size() >= cantidad) {
                return encontrados;
            }
            dormir(20);
        }
        return pedidos(condicion);
    }

    public void detener() {
        servidor.stop(0);
    }

    private void atender(HttpExchange intercambio) throws IOException {
        String ruta = intercambio.getRequestURI().getPath();
        Map<String, String> headers = new ConcurrentHashMap<>();
        intercambio.getRequestHeaders().forEach((nombre, valores) -> {
            if (!valores.isEmpty()) {
                headers.put(nombre.toLowerCase(), valores.get(0));
            }
        });
        String cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        pedidos.add(new Pedido(intercambio.getRequestMethod(), ruta, Map.copyOf(headers), cuerpo));

        Respuesta respuesta = siguiente(ruta);
        if (!respuesta.demora().isZero()) {
            dormir(respuesta.demora().toMillis());
        }
        byte[] bytes = respuesta.cuerpo().getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().set("Content-Type", "application/json");
        try {
            intercambio.sendResponseHeaders(respuesta.status(), bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream salida = intercambio.getResponseBody()) {
                    salida.write(bytes);
                }
            }
        } catch (IOException e) {
            // El cliente corto antes (por ejemplo, por timeout): no es un error del test.
        } finally {
            intercambio.close();
        }
    }

    private synchronized Respuesta siguiente(String ruta) {
        Deque<Respuesta> cola = secuencias.get(ruta);
        if (cola != null && !cola.isEmpty()) {
            return cola.poll();
        }
        return fijas.getOrDefault(ruta, porDefecto);
    }

    private static void dormir(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
