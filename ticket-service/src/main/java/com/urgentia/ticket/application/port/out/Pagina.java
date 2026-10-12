package com.urgentia.ticket.application.port.out;

import java.util.List;
import java.util.function.Function;

/** Una pagina de resultados. {@code pagina} empieza en 0. */
public record Pagina<T>(List<T> contenido, int pagina, int tamanio, long totalElementos, int totalPaginas) {

    public Pagina {
        contenido = List.copyOf(contenido);
    }

    public <R> Pagina<R> map(Function<? super T, ? extends R> conversor) {
        List<R> convertidos = contenido.stream().<R>map(conversor).toList();
        return new Pagina<>(convertidos, pagina, tamanio, totalElementos, totalPaginas);
    }
}
