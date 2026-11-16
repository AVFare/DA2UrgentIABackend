package com.urgentia.ticket.infrastructure.soap;

import static org.assertj.core.api.Assertions.assertThat;

import com.urgentia.ticket.infrastructure.IntegracionBase;
import com.urgentia.ticket.infrastructure.RespuestasIa;
import com.urgentia.ticket.infrastructure.ServidorFalso;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.UUID;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Prueba el servicio SOAP de punta a punta por HTTP (servlet de Spring-WS + endpoint + caso de uso
 * + JPA), con la IA de mentira de IntegracionBase. Los tickets se crean por el REST, como en la demo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class TicketsSoapTest extends IntegracionBase {

    private static final String NS = "http://urgentia.local/ws/tickets";
    private static final String ACCION = "http://urgentia.local/ws/tickets/ConsultarEstadoTicket";
    private static final String OTRO_SOLICITANTE = "b1c2d3e4-0000-4000-8000-000000000005";

    @LocalServerPort
    private int puerto;

    private final TestRestTemplate http = new TestRestTemplate();

    // ----- Consulta correcta -----

    @Nested
    class Consulta {

        @Test
        void elTicketDeLaDemoDevuelveEscaladoP1ConSuSla() throws Exception {
            String id = crearConIa(RespuestasIa.laDeLaDemo()).get("id").asText();

            RespuestaSoap soap = consultar(id, null, null, "cid-soap-demo");

            assertThat(soap.status).isEqualTo(200);
            assertThat(soap.xpath("//t:ConsultarEstadoTicketResponse/t:ticketId")).isEqualTo(id);
            assertThat(soap.xpath("//t:ConsultarEstadoTicketResponse/t:estado")).isEqualTo("ESCALADO");
            assertThat(soap.xpath("//t:ConsultarEstadoTicketResponse/t:prioridad")).isEqualTo("P1");
            assertThat(soap.xpath("//t:ConsultarEstadoTicketResponse/t:fechaLimiteSla")).isEqualTo("2026-10-05T15:03:11Z");
            assertThat(soap.cabecera("X-Correlation-Id")).isEqualTo("cid-soap-demo");
            soap.validaContraElXsd("ConsultarEstadoTicketResponse");
        }

        @Test
        void unTicketComunClasificadoDevuelveSuPrioridadYSla() throws Exception {
            String id = crearConIa(RespuestasIa.comun()).get("id").asText();

            RespuestaSoap soap = consultar(id, null, null, null);

            assertThat(soap.xpath("//t:estado")).isEqualTo("CLASIFICADO");
            assertThat(soap.xpath("//t:prioridad")).isEqualTo("P3");
            assertThat(soap.xpath("//t:fechaLimiteSla")).isEqualTo("2026-10-05T22:03:11Z");
            soap.validaContraElXsd("ConsultarEstadoTicketResponse");
        }

        @Test
        void conLaIaCaidaElTicketPendienteNoTraePrioridadNiSla() throws Exception {
            IA.responder(RUTA_IA, ServidorFalso.Respuesta.json(500, "{\"codigo\":\"ERROR_INTERNO\"}"));
            String id = leer(crear("No puede ingresar nadie", "Producción caída, todos los usuarios bloqueados"))
                    .get("id").asText();

            RespuestaSoap soap = consultar(id, null, null, null);

            assertThat(soap.status).isEqualTo(200);
            assertThat(soap.xpath("//t:estado")).isEqualTo("PENDIENTE_CLASIFICACION");
            assertThat(soap.cantidad("//t:prioridad")).isZero();
            assertThat(soap.cantidad("//t:fechaLimiteSla")).isZero();
            soap.validaContraElXsd("ConsultarEstadoTicketResponse");
        }

        @Test
        void registraLaConsultaConElCorrelationIdEnLosLogs(CapturedOutput salida) throws Exception {
            String id = crearConIa(RespuestasIa.comun()).get("id").asText();

            consultar(id, null, null, "cid-soap-log");

            assertThat(salida.getAll()).contains("SOAP ConsultarEstadoTicket ticketId=" + id);
            assertThat(salida.getAll().lines()
                    .filter(linea -> linea.contains("SOAP ConsultarEstadoTicket"))
                    .anyMatch(linea -> linea.contains("cid-soap-log"))).isTrue();
        }
    }

    // ----- SOAP Fault -----

    @Nested
    class Errores {

        @Test
        void unTicketInexistenteDaFaultNoEncontradoConElCorrelationId() throws Exception {
            RespuestaSoap soap = consultar(UUID.randomUUID().toString(), null, null, "cid-soap-404");

            assertThat(soap.status).isEqualTo(500);
            assertThat(soap.xpath("//soap:Fault/faultcode")).endsWith("Client");
            assertThat(soap.xpath("//soap:Fault/detail/t:ErrorDetalle/t:codigo")).isEqualTo("NO_ENCONTRADO");
            assertThat(soap.xpath("//soap:Fault/detail/t:ErrorDetalle/t:mensaje")).startsWith("No existe el ticket");
            assertThat(soap.xpath("//soap:Fault/detail/t:ErrorDetalle/t:correlationId")).isEqualTo("cid-soap-404");
            soap.validaContraElXsd("ErrorDetalle");
        }

        @Test
        void unIdMalFormadoDaFaultValidacion() throws Exception {
            RespuestaSoap soap = consultar("esto-no-es-un-uuid", null, null, null);

            assertThat(soap.status).isEqualTo(500);
            assertThat(soap.xpath("//soap:Fault/faultcode")).endsWith("Client");
            assertThat(soap.xpath("//soap:Fault/detail/t:ErrorDetalle/t:codigo")).isEqualTo("VALIDACION");
            soap.validaContraElXsd("ErrorDetalle");
        }

        @Test
        void unPedidoSinTicketIdDaFaultValidacion() throws Exception {
            RespuestaSoap soap = enviar("<t:ConsultarEstadoTicketRequest xmlns:t=\"" + NS + "\"/>", null, null, null);

            assertThat(soap.xpath("//soap:Fault/detail/t:ErrorDetalle/t:codigo")).isEqualTo("VALIDACION");
        }

        @Test
        void unRolDesconocidoEnLosHeadersDaFaultValidacion() throws Exception {
            String id = crearConIa(RespuestasIa.comun()).get("id").asText();

            RespuestaSoap soap = consultar(id, SOLICITANTE, "SUPERUSUARIO", null);

            assertThat(soap.xpath("//soap:Fault/detail/t:ErrorDetalle/t:codigo")).isEqualTo("VALIDACION");
        }

        @Test
        void elFaultNoMuestraDetallesInternos() throws Exception {
            RespuestaSoap soap = consultar(UUID.randomUUID().toString(), null, null, null);

            assertThat(soap.cuerpo).doesNotContain("Exception").doesNotContain("at com.urgentia");
        }
    }

    // ----- Quien consulta -----

    @Nested
    class Permisos {

        @Test
        void unSolicitanteVeSuPropioTicket() throws Exception {
            String id = crearConIa(RespuestasIa.comun()).get("id").asText();

            RespuestaSoap soap = consultar(id, SOLICITANTE, "SOLICITANTE", null);

            assertThat(soap.status).isEqualTo(200);
            assertThat(soap.xpath("//t:estado")).isEqualTo("CLASIFICADO");
        }

        @Test
        void unSolicitanteNoVeElTicketDeOtroYRecibeNoEncontrado() throws Exception {
            String id = crearConIa(RespuestasIa.comun()).get("id").asText();

            RespuestaSoap soap = consultar(id, OTRO_SOLICITANTE, "SOLICITANTE", null);

            assertThat(soap.status).isEqualTo(500);
            assertThat(soap.xpath("//soap:Fault/detail/t:ErrorDetalle/t:codigo")).isEqualTo("NO_ENCONTRADO");
        }

        @Test
        void unAgenteVeLosTicketsDeCualquiera() throws Exception {
            String id = crearConIa(RespuestasIa.comun()).get("id").asText();

            RespuestaSoap soap = consultar(id, GUARDIA, "AGENTE", null);

            assertThat(soap.status).isEqualTo(200);
        }
    }

    // ----- Contrato publicado -----

    @Nested
    class ContratoPublicado {

        @Test
        void elWsdlSePublicaEnWsTicketsWsdl() {
            ResponseEntity<String> wsdl = http.getForEntity(url("/ws/tickets.wsdl"), String.class);

            assertThat(wsdl.getStatusCode().value()).isEqualTo(200);
            assertThat(wsdl.getBody())
                    .contains("ConsultarEstadoTicket")
                    .contains("TicketFault")
                    .contains("schemaLocation=\"tickets-schema.xsd\"");
        }

        @Test
        void elXsdSePublicaEnWsTicketsSchemaXsd() {
            ResponseEntity<String> xsd = http.getForEntity(url("/ws/tickets-schema.xsd"), String.class);

            assertThat(xsd.getStatusCode().value()).isEqualTo(200);
            assertThat(xsd.getBody()).contains("ConsultarEstadoTicketRequest").contains("ErrorDetalle");
        }
    }

    // ----- Utilidades -----

    private String url(String ruta) {
        return "http://localhost:" + puerto + ruta;
    }

    private RespuestaSoap consultar(String ticketId, String usuarioId, String rol, String correlationId) {
        return enviar("<t:ConsultarEstadoTicketRequest xmlns:t=\"" + NS + "\"><t:ticketId>" + ticketId
                + "</t:ticketId></t:ConsultarEstadoTicketRequest>", usuarioId, rol, correlationId);
    }

    private RespuestaSoap enviar(String payload, String usuarioId, String rol, String correlationId) {
        String sobre = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
                + payload + "</soap:Body></soap:Envelope>";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_XML);
        headers.add("SOAPAction", "\"" + ACCION + "\"");
        if (usuarioId != null) {
            headers.add("X-User-Id", usuarioId);
        }
        if (rol != null) {
            headers.add("X-User-Rol", rol);
        }
        if (correlationId != null) {
            headers.add("X-Correlation-Id", correlationId);
        }
        ResponseEntity<String> respuesta = http.exchange(url("/ws/tickets"), HttpMethod.POST,
                new HttpEntity<>(sobre, headers), String.class);
        return new RespuestaSoap(respuesta.getStatusCode().value(), respuesta.getBody(), respuesta.getHeaders());
    }

    /** Respuesta HTTP de un pedido SOAP, con ayudas para leer el XML. */
    private static final class RespuestaSoap {

        final int status;
        final String cuerpo;
        final HttpHeaders headers;

        RespuestaSoap(int status, String cuerpo, HttpHeaders headers) {
            this.status = status;
            this.cuerpo = cuerpo;
            this.headers = headers;
        }

        String cabecera(String nombre) {
            return headers.getFirst(nombre);
        }

        String xpath(String expresion) throws Exception {
            return evaluador().evaluate(expresion, documento());
        }

        int cantidad(String expresion) throws Exception {
            return ((Number) evaluador().evaluate("count(" + expresion + ")", documento(), XPathConstants.NUMBER)).intValue();
        }

        /** El hijo del Body (la respuesta o el detalle del Fault) cumple el XSD del contrato. */
        void validaContraElXsd(String elementoRaiz) throws Exception {
            Document doc = documento();
            Element raiz = (Element) evaluador().evaluate("//t:" + elementoRaiz, doc, XPathConstants.NODE);
            assertThat(raiz).as("elemento " + elementoRaiz + " en " + cuerpo).isNotNull();
            Schema esquema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                    .newSchema(new ClassPathResource("soap/tickets-schema.xsd").getURL());
            esquema.newValidator().validate(new DOMSource(raiz));
        }

        private Document documento() throws Exception {
            DocumentBuilderFactory fabrica = DocumentBuilderFactory.newInstance();
            fabrica.setNamespaceAware(true);
            return fabrica.newDocumentBuilder().parse(new org.xml.sax.InputSource(new StringReader(cuerpo)));
        }

        private static XPath evaluador() {
            XPath xpath = XPathFactory.newInstance().newXPath();
            xpath.setNamespaceContext(new NamespaceContext() {
                @Override
                public String getNamespaceURI(String prefijo) {
                    return switch (prefijo) {
                        case "t" -> NS;
                        case "soap" -> "http://schemas.xmlsoap.org/soap/envelope/";
                        default -> XMLConstants.NULL_NS_URI;
                    };
                }

                @Override
                public String getPrefix(String uri) {
                    return null;
                }

                @Override
                public Iterator<String> getPrefixes(String uri) {
                    return null;
                }
            });
            return xpath;
        }
    }
}
