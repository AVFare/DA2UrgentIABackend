"""Adapter para cualquier LLM que exponga la API de OpenAI (POST /chat/completions).

Lo usan Ollama y Groq: cambian la URL base, el modelo y la API key.
"""

import logging

import httpx2

from app.application.errors import LlmNoDisponibleError, LlmRespuestaInvalidaError
from app.application.ports.llm_provider import PedidoLlm, RespuestaLlm
from app.infrastructure.llm.response_parser import parsear_clasificacion

log = logging.getLogger(__name__)

# El limite real lo pone ClasificacionFacade; este es solo un tope de seguridad.
_TIMEOUT_HTTP_S = 60.0


class OpenAICompatibleProvider:
    def __init__(
        self,
        nombre: str,
        base_url: str,
        modelo: str,
        api_key: str = "",
        precalentar_al_iniciar: bool = False,
        cliente: httpx2.AsyncClient | None = None,
    ) -> None:
        self.nombre = nombre
        self.modelo = modelo
        self._precalentar_al_iniciar = precalentar_al_iniciar
        self._url = base_url.rstrip("/") + "/chat/completions"
        self._headers = {"Authorization": f"Bearer {api_key}"} if api_key else {}
        self._cliente = cliente or httpx2.AsyncClient(timeout=_TIMEOUT_HTTP_S)

    async def consultar(self, pedido: PedidoLlm) -> RespuestaLlm:
        cuerpo = {
            "model": self.modelo,
            "messages": [{"role": "user", "content": pedido.prompt}],
            "temperature": 0,
            "response_format": {"type": "json_object"},
        }
        datos = await self._post(cuerpo)
        try:
            texto = datos["choices"][0]["message"]["content"]
        except (KeyError, IndexError, TypeError):
            raise LlmRespuestaInvalidaError("la respuesta no tiene el formato de chat/completions", str(datos)[:500])
        if not isinstance(texto, str):
            raise LlmRespuestaInvalidaError("la respuesta no trae texto", str(datos)[:500])
        return RespuestaLlm(
            clasificacion=parsear_clasificacion(texto),
            modelo=datos.get("model") or self.modelo,
            texto_crudo=texto,
        )

    async def precalentar(self, prompt: str) -> None:
        """Si esta habilitado, consulta con el prompt y un solo token de respuesta, para que el modelo
        quede cargado y la parte fija del prompt quede procesada en la cache del servidor.
        """
        if not self._precalentar_al_iniciar:
            return
        cuerpo = {"model": self.modelo, "messages": [{"role": "user", "content": prompt}], "max_tokens": 1}
        try:
            await self._post(cuerpo)
            log.info("Modelo %s de %s precalentado", self.modelo, self.nombre)
        except LlmNoDisponibleError as error:
            log.warning("No se pudo precalentar el modelo %s de %s: %s", self.modelo, self.nombre, error)

    async def cerrar(self) -> None:
        await self._cliente.aclose()

    async def _post(self, cuerpo: dict) -> dict:
        try:
            respuesta = await self._cliente.post(self._url, json=cuerpo, headers=self._headers)
            respuesta.raise_for_status()
        except httpx2.HTTPStatusError as error:
            raise LlmNoDisponibleError(f"{self.nombre} respondio HTTP {error.response.status_code}") from error
        except httpx2.RequestError as error:
            raise LlmNoDisponibleError(f"no se pudo conectar con {self.nombre} ({type(error).__name__})") from error
        try:
            datos = respuesta.json()
        except ValueError:
            raise LlmRespuestaInvalidaError("la respuesta no es JSON", respuesta.text[:500]) from None
        if not isinstance(datos, dict):
            raise LlmRespuestaInvalidaError("la respuesta no tiene el formato de chat/completions", respuesta.text[:500])
        return datos
