package com.urgentia.ticket.domain;

import com.urgentia.ticket.domain.model.Categoria;
import com.urgentia.ticket.domain.model.Clasificacion;
import com.urgentia.ticket.domain.model.Impacto;
import com.urgentia.ticket.domain.model.ModuloAfectado;
import com.urgentia.ticket.domain.model.Urgencia;
import java.time.Instant;

/** Builder de clasificaciones para los tests (por defecto: un caso P3 que no escala). */
public final class ClasificacionBuilder {

    public static final Instant FECHA = Instant.parse("2026-10-05T14:03:11Z");

    private Categoria categoria = Categoria.INCIDENTE;
    private Urgencia urgencia = Urgencia.MEDIA;
    private Impacto impacto = Impacto.MEDIO;
    private ModuloAfectado modulo = ModuloAfectado.PAGOS;
    private boolean requiereEscalamiento = false;
    private double confianza = 0.9;
    private String justificacion = "Caso de prueba";
    private String proveedor = "mock";
    private Instant fecha = FECHA;

    private ClasificacionBuilder() {
    }

    public static ClasificacionBuilder unaClasificacion() {
        return new ClasificacionBuilder();
    }

    /** La del guion de la demo: produccion caida, todos bloqueados en el login. */
    public static ClasificacionBuilder laDeLaDemo() {
        return unaClasificacion()
                .con(Urgencia.ALTA, Impacto.ALTO)
                .conModulo(ModuloAfectado.AUTENTICACION)
                .critica()
                .conConfianza(0.93);
    }

    public ClasificacionBuilder con(Urgencia urgencia, Impacto impacto) {
        this.urgencia = urgencia;
        this.impacto = impacto;
        return this;
    }

    public ClasificacionBuilder conCategoria(Categoria categoria) {
        this.categoria = categoria;
        return this;
    }

    public ClasificacionBuilder conModulo(ModuloAfectado modulo) {
        this.modulo = modulo;
        return this;
    }

    public ClasificacionBuilder critica() {
        this.requiereEscalamiento = true;
        return this;
    }

    public ClasificacionBuilder conConfianza(double confianza) {
        this.confianza = confianza;
        return this;
    }

    public ClasificacionBuilder conFecha(Instant fecha) {
        this.fecha = fecha;
        return this;
    }

    public Clasificacion build() {
        return new Clasificacion(categoria, urgencia, impacto, modulo, requiereEscalamiento, confianza,
                justificacion, proveedor, fecha);
    }
}
