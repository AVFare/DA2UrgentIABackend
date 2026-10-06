"""Errores de la capa de aplicacion al consultar el LLM."""


class LlmError(Exception):
    """No se pudo obtener una clasificacion del LLM."""


class LlmRespuestaInvalidaError(LlmError):
    """El LLM respondio algo que no es una clasificacion valida."""

    def __init__(self, mensaje: str, texto_crudo: str = "") -> None:
        super().__init__(mensaje)
        self.texto_crudo = texto_crudo


class LlmNoDisponibleError(LlmError):
    """No se pudo consultar al LLM: error de conexion o respuesta HTTP con error."""


class LlmTimeoutError(LlmError):
    """El LLM no respondio dentro del tiempo maximo."""

    def __init__(self, timeout_ms: int) -> None:
        super().__init__(f"El LLM no respondio en {timeout_ms} ms")
        self.timeout_ms = timeout_ms
