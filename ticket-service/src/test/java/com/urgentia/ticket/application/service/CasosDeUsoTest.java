package com.urgentia.ticket.application.service;

import static com.urgentia.ticket.domain.ClasificacionBuilder.laDeLaDemo;
import static com.urgentia.ticket.domain.ClasificacionBuilder.unaClasificacion;
import static com.urgentia.ticket.domain.TicketBuilder.AGENTE;
import static com.urgentia.ticket.domain.TicketBuilder.SOLICITANTE;
import static com.urgentia.ticket.domain.TicketBuilder.unTicket;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.urgentia.ticket.application.Dobles.ClasificadorFalso;
import com.urgentia.ticket.application.Dobles.PublicadorQueGraba;
import com.urgentia.ticket.application.Dobles.RepositorioEnMemoria;
import com.urgentia.ticket.application.exception.ClasificacionNoDisponibleException;
import com.urgentia.ticket.application.exception.DatosInvalidosException;
import com.urgentia.ticket.application.exception.TicketNoEncontradoException;
import com.urgentia.ticket.application.port.in.CrearTicketUseCase.CrearTicketCommand;
import com.urgentia.ticket.application.port.in.Rol;
import com.urgentia.ticket.application.port.in.UsuarioActual;
import com.urgentia.ticket.application.port.out.FiltroTickets;
import com.urgentia.ticket.application.port.out.Pagina;
import com.urgentia.ticket.domain.exception.TransicionInvalidaException;
import com.urgentia.ticket.domain.factory.TicketFactory;
import com.urgentia.ticket.domain.model.EstadoTicket;
import com.urgentia.ticket.domain.model.Impacto;
import com.urgentia.ticket.domain.model.Prioridad;
import com.urgentia.ticket.domain.model.Ticket;
import com.urgentia.ticket.domain.model.TicketId;
import com.urgentia.ticket.domain.model.Urgencia;
import com.urgentia.ticket.domain.service.MatrizItilStrategy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Los servicios de aplicacion solo orquestan: se prueban con dobles de los puertos. */
class CasosDeUsoTest {

    private static final Instant AHORA = Instant.parse("2026-10-05T14:03:10Z");
    private static final CrearTicketCommand DEMO = new CrearTicketCommand("No puede ingresar nadie",
            "Producción caída, todos los usuarios bloqueados en el login desde las 9", SOLICITANTE);

    private final Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
    private final RepositorioEnMemoria repositorio = new RepositorioEnMemoria();
    private final PublicadorQueGraba publicador = new PublicadorQueGraba(repositorio);
    private final ClasificadorFalso clasificador = new ClasificadorFalso();
    private final MatrizItilStrategy matriz = new MatrizItilStrategy();

    private final CrearTicketService crear = new CrearTicketService(new TicketFactory(reloj), clasificador, matriz,
            repositorio, publicador, reloj);
    private final ReclasificarTicketService reclasificar =
            new ReclasificarTicketService(clasificador, matriz, repositorio, publicador, reloj);
    private final AsignarTicketService asignar = new AsignarTicketService(repositorio, publicador, reloj);
    private final CambiarEstadoService cambiarEstado = new CambiarEstadoService(repositorio, publicador, reloj);
    private final ConsultarTicketsService consultar = new ConsultarTicketsService(repositorio);

    @Nested
    class Crear {

        @Test
        void casoCriticoQuedaEscaladoYPublicaLosTresEventosDespuesDeGuardar() {
            clasificador.responde(laDeLaDemo().build());

            Ticket ticket = crear.crear(DEMO);

            assertThat(ticket.estado()).isEqualTo(EstadoTicket.ESCALADO);
            assertThat(ticket.prioridad()).isEqualTo(Prioridad.P1);
            assertThat(ticket.version()).isZero();
            assertThat(ticket.eventosPendientes()).isEmpty();
            assertThat(repositorio.buscarPorId(ticket.id())).isPresent();
            assertThat(publicador.tipos()).containsExactly("TicketCreado", "TicketClasificado", "TicketEscalado");
            assertThat(publicador.guardadosAlPublicar).containsExactly(1);
        }

        @Test
        void casoNormalQuedaClasificado() {
            clasificador.responde(unaClasificacion().con(Urgencia.BAJA, Impacto.BAJO).build());

            Ticket ticket = crear.crear(DEMO);

            assertThat(ticket.estado()).isEqualTo(EstadoTicket.CLASIFICADO);
            assertThat(ticket.prioridad()).isEqualTo(Prioridad.P4);
            assertThat(publicador.tipos()).containsExactly("TicketCreado", "TicketClasificado");
        }

        @Test
        void conLaIaCaidaElTicketSeCreaIgualYQuedaPendiente() {
            clasificador.caido();

            Ticket ticket = crear.crear(DEMO);

            assertThat(ticket.estado()).isEqualTo(EstadoTicket.PENDIENTE_CLASIFICACION);
            assertThat(ticket.prioridad()).isNull();
            assertThat(ticket.clasificacion()).isNull();
            assertThat(repositorio.buscarPorId(ticket.id())).isPresent();
            assertThat(publicador.tipos()).containsExactly("TicketCreado");
        }

        @Test
        void siFallaElGuardadoNoSePublicaNada() {
            clasificador.responde(laDeLaDemo().build());
            repositorio.fallarAlGuardar = new IllegalStateException("base caida");

            assertThatThrownBy(() -> crear.crear(DEMO)).hasMessage("base caida");
            assertThat(publicador.publicados).isEmpty();
        }
    }

    @Nested
    class Reclasificar {

        @Test
        void unPendienteSeClasificaYPuedeEscalar() {
            Ticket pendiente = repositorio.guardar(unTicket().enEstado(EstadoTicket.PENDIENTE_CLASIFICACION).construir());
            clasificador.responde(laDeLaDemo().build());

            Ticket ticket = reclasificar.reclasificar(pendiente.id());

            assertThat(ticket.estado()).isEqualTo(EstadoTicket.ESCALADO);
            assertThat(publicador.tipos()).containsExactly("TicketClasificado", "TicketEscalado");
        }

        @Test
        void conLaIaCaidaPropagaElErrorYNoTocaElTicket() {
            Ticket pendiente = repositorio.guardar(unTicket().enEstado(EstadoTicket.PENDIENTE_CLASIFICACION).construir());
            int guardadosAntes = repositorio.guardados;
            clasificador.caido();

            assertThatThrownBy(() -> reclasificar.reclasificar(pendiente.id()))
                    .isInstanceOf(ClasificacionNoDisponibleException.class);
            assertThat(repositorio.guardados).isEqualTo(guardadosAntes);
            assertThat(repositorio.buscarPorId(pendiente.id()).orElseThrow().estado())
                    .isEqualTo(EstadoTicket.PENDIENTE_CLASIFICACION);
            assertThat(publicador.publicados).isEmpty();
        }

        @Test
        void enUnEstadoQueNoAdmiteClasificacionNiSiquieraLlamaALaIa() {
            Ticket asignado = repositorio.guardar(unTicket().enEstado(EstadoTicket.ASIGNADO).conAgente(AGENTE).construir());
            clasificador.responde(laDeLaDemo().build());

            assertThatThrownBy(() -> reclasificar.reclasificar(asignado.id()))
                    .isInstanceOf(TransicionInvalidaException.class);
            assertThat(clasificador.llamadas).isZero();
        }

        @Test
        void unTicketQueNoExisteDaNoEncontrado() {
            assertThatThrownBy(() -> reclasificar.reclasificar(TicketId.nuevo()))
                    .isInstanceOf(TicketNoEncontradoException.class);
        }
    }

    @Nested
    class AsignarYCambiarEstado {

        @Test
        void asignarGuardaYPublica() {
            Ticket clasificado = repositorio.guardar(unTicket().enEstado(EstadoTicket.CLASIFICADO).construir());

            Ticket ticket = asignar.asignar(clasificado.id(), AGENTE);

            assertThat(ticket.estado()).isEqualTo(EstadoTicket.ASIGNADO);
            assertThat(ticket.version()).isEqualTo(clasificado.version() + 1);
            assertThat(repositorio.buscarPorId(clasificado.id()).orElseThrow().agenteAsignadoId()).isEqualTo(AGENTE);
            assertThat(publicador.tipos()).containsExactly("TicketAsignado", "TicketEstadoCambiado");
        }

        @Test
        void cambiarEstadoGuardaYPublica() {
            Ticket enCurso = repositorio.guardar(unTicket().enEstado(EstadoTicket.EN_CURSO).conAgente(AGENTE).construir());

            Ticket ticket = cambiarEstado.cambiarEstado(enCurso.id(), EstadoTicket.RESUELTO, null);

            assertThat(ticket.estado()).isEqualTo(EstadoTicket.RESUELTO);
            assertThat(ticket.fechaActualizacion()).isEqualTo(AHORA);
            assertThat(publicador.tipos()).containsExactly("TicketEstadoCambiado", "TicketResuelto");
        }

        @Test
        void unaTransicionInvalidaNoGuardaNiPublica() {
            Ticket cerrado = repositorio.guardar(unTicket().enEstado(EstadoTicket.CERRADO).conAgente(AGENTE).construir());
            int guardadosAntes = repositorio.guardados;

            assertThatThrownBy(() -> cambiarEstado.cambiarEstado(cerrado.id(), EstadoTicket.EN_CURSO, null))
                    .isInstanceOf(TransicionInvalidaException.class);
            assertThat(repositorio.guardados).isEqualTo(guardadosAntes);
            assertThat(publicador.publicados).isEmpty();
        }

        @Test
        void sobreUnTicketQueNoExisteDaNoEncontrado() {
            assertThatThrownBy(() -> asignar.asignar(TicketId.nuevo(), AGENTE))
                    .isInstanceOf(TicketNoEncontradoException.class);
            assertThatThrownBy(() -> cambiarEstado.cambiarEstado(TicketId.nuevo(), EstadoTicket.EN_CURSO, null))
                    .isInstanceOf(TicketNoEncontradoException.class);
        }
    }

    @Nested
    class Consultar {

        private final UUID otroSolicitante = UUID.randomUUID();
        private Ticket propio;
        private Ticket ajeno;

        @org.junit.jupiter.api.BeforeEach
        void cargarTickets() {
            propio = repositorio.guardar(unTicket().crear());
            ajeno = repositorio.guardar(unTicket().deSolicitante(otroSolicitante).crear());
        }

        @Test
        void unSolicitanteSoloVeLosSuyosEnElListado() {
            Pagina<Ticket> pagina = consultar.buscar(FiltroTickets.sinFiltros(),
                    new UsuarioActual(SOLICITANTE, Rol.SOLICITANTE), 0, 20);

            assertThat(pagina.contenido()).extracting(Ticket::id).containsExactly(propio.id());
            assertThat(pagina.totalElementos()).isEqualTo(1);
        }

        @Test
        void unAgenteVeTodos() {
            Pagina<Ticket> pagina = consultar.buscar(FiltroTickets.sinFiltros(),
                    new UsuarioActual(AGENTE, Rol.AGENTE), 0, 20);

            assertThat(pagina.contenido()).hasSize(2);
        }

        @Test
        void unSolicitanteNoPuedeVerElDetalleDeUnTicketAjeno() {
            UsuarioActual solicitante = new UsuarioActual(SOLICITANTE, Rol.SOLICITANTE);

            assertThat(consultar.obtener(propio.id(), solicitante).id()).isEqualTo(propio.id());
            assertThatThrownBy(() -> consultar.obtener(ajeno.id(), solicitante))
                    .isInstanceOf(TicketNoEncontradoException.class);
        }

        @Test
        void unAdminOUnaLlamadaInternaVenCualquierDetalle() {
            assertThat(consultar.obtener(ajeno.id(), new UsuarioActual(UUID.randomUUID(), Rol.ADMIN))).isNotNull();
            assertThat(consultar.obtener(ajeno.id(), UsuarioActual.interno())).isNotNull();
        }

        @Test
        void unTicketQueNoExisteDaNoEncontrado() {
            assertThatThrownBy(() -> consultar.obtener(TicketId.nuevo(), UsuarioActual.interno()))
                    .isInstanceOf(TicketNoEncontradoException.class)
                    .hasMessageContaining("No existe el ticket");
        }

        @Test
        void unSolicitanteSinIdEsUnPedidoInvalido() {
            assertThatThrownBy(() -> new UsuarioActual(null, Rol.SOLICITANTE))
                    .isInstanceOf(DatosInvalidosException.class)
                    .satisfies(e -> assertThat(((DatosInvalidosException) e).campo()).isEqualTo("X-User-Id"));
        }

        @Test
        void laPaginaSePuedeConvertir() {
            Pagina<Ticket> pagina = consultar.buscar(FiltroTickets.sinFiltros(), UsuarioActual.interno(), 0, 1);
            Pagina<String> titulos = pagina.map(Ticket::titulo);

            assertThat(titulos.contenido()).hasSize(1);
            assertThat(titulos.totalElementos()).isEqualTo(2);
            assertThat(titulos.totalPaginas()).isEqualTo(2);
        }
    }
}
