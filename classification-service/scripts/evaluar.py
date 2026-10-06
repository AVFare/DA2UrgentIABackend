"""Evaluacion del componente de IA (RIA01) con el set etiquetado tests/data/tickets_eval.json.

Uso (desde classification-service/, con la configuracion del proveedor en variables de entorno):
    LLM_PROVIDER=ollama LLM_BASE_URL=http://localhost:11434/v1 python -m scripts.evaluar

Cada ticket recorre el mismo camino que en produccion (enmascarado, plantilla del prompt y
proveedor elegido por LlmProviderFactory). Guarda el detalle en
tests/data/resultados/evaluacion_<proveedor>_<modelo>_<prompt>.json y un resumen en .md para el informe.
"""

import asyncio
import json
import re
import statistics
import sys
import time
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path

from app.application.clasificacion_facade import armar_prompt
from app.application.errors import LlmError, LlmNoDisponibleError
from app.application.masking import enmascarar
from app.application.ports.llm_provider import LlmProvider, PedidoLlm, RespuestaLlm
from app.config import Settings
from app.infrastructure.llm.factory import crear_llm_provider
from app.infrastructure.prompts import cargar_plantilla

CARPETA_DATOS = Path(__file__).resolve().parents[1] / "tests" / "data"
ARCHIVO_CASOS = CARPETA_DATOS / "tickets_eval.json"
CARPETA_RESULTADOS = CARPETA_DATOS / "resultados"

CAMPOS = ("categoria", "urgencia", "impacto", "moduloAfectado", "requiereEscalamiento", "prioridad")

# Criterio de aceptacion RIA01 (CONTEXTO_PROYECTO.md, 12.3).
MINIMO_CATEGORIA = 0.80
MINIMO_ESCALAMIENTO = 0.90
LIMITE_LATENCIA_MS = 5000

# Los planes gratuitos limitan los pedidos por minuto (Groq responde 429).
ESPERA_POR_LIMITE_S = 15
REINTENTOS_POR_LIMITE = 8

# Matriz de la seccion 8.1. La prioridad la decide ticket-service; aca se calcula solo para
# medir que prioridad final resultaria de cada sugerencia de la IA.
_MATRIZ = {
    ("ALTA", "ALTO"): "P1", ("ALTA", "MEDIO"): "P2", ("ALTA", "BAJO"): "P3",
    ("MEDIA", "ALTO"): "P2", ("MEDIA", "MEDIO"): "P3", ("MEDIA", "BAJO"): "P4",
    ("BAJA", "ALTO"): "P3", ("BAJA", "MEDIO"): "P4", ("BAJA", "BAJO"): "P4",
}


def prioridad(urgencia: str, impacto: str, requiere_escalamiento: bool) -> str:
    return "P1" if requiere_escalamiento else _MATRIZ[(urgencia, impacto)]


@dataclass
class ResultadoCaso:
    id: int
    titulo: str
    esperado: dict
    obtenido: dict | None
    latencia_ms: int
    error: str | None = None
    aciertos: dict = field(default_factory=dict)


@dataclass
class Evaluacion:
    proveedor: str
    modelo: str
    version_prompt: str
    fecha: str
    casos: list[ResultadoCaso]

    def acierto(self, campo: str) -> float:
        return sum(caso.aciertos.get(campo, False) for caso in self.casos) / len(self.casos)

    @property
    def latencias(self) -> list[int]:
        return [caso.latencia_ms for caso in self.casos if caso.error is None]

    @property
    def cumple_ria01(self) -> bool:
        return (self.acierto("categoria") >= MINIMO_CATEGORIA
                and self.acierto("requiereEscalamiento") >= MINIMO_ESCALAMIENTO)


def cargar_casos(archivo: Path = ARCHIVO_CASOS) -> list[dict]:
    return json.loads(archivo.read_text(encoding="utf-8"))


async def evaluar(llm: LlmProvider, plantilla: str, version_prompt: str, casos: list[dict]) -> Evaluacion:
    resultados = []
    await llm.precalentar(armar_prompt(plantilla, "Prueba de arranque", "Ticket de prueba"))
    for caso in casos:
        titulo, descripcion = enmascarar(caso["titulo"]), enmascarar(caso["descripcion"])
        pedido = PedidoLlm(titulo, descripcion, armar_prompt(plantilla, titulo, descripcion))
        try:
            respuesta, latencia_ms = await _consultar_respetando_limite(llm, pedido)
        except LlmError as error:
            resultados.append(ResultadoCaso(caso["id"], caso["titulo"], caso["esperado"], None, 0, str(error)))
            continue
        c = respuesta.clasificacion
        obtenido = {
            "categoria": c.categoria.value,
            "urgencia": c.urgencia.value,
            "impacto": c.impacto.value,
            "moduloAfectado": c.modulo_afectado.value,
            "requiereEscalamiento": c.requiere_escalamiento,
            "prioridad": prioridad(c.urgencia.value, c.impacto.value, c.requiere_escalamiento),
            "confianza": c.confianza,
            "justificacion": c.justificacion,
        }
        aciertos = {campo: obtenido[campo] == caso["esperado"][campo] for campo in CAMPOS}
        resultados.append(ResultadoCaso(caso["id"], caso["titulo"], caso["esperado"], obtenido, latencia_ms,
                                        aciertos=aciertos))
    return Evaluacion(llm.nombre, llm.modelo, version_prompt,
                      datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"), resultados)


async def _consultar_respetando_limite(llm: LlmProvider, pedido: PedidoLlm) -> tuple[RespuestaLlm, int]:
    """Consulta y, si el proveedor responde 429 (limite de pedidos por minuto), espera y reintenta.

    La latencia es la del intento que respondio, sin las esperas.
    """
    for intento in range(1, REINTENTOS_POR_LIMITE + 1):
        inicio = time.perf_counter()
        try:
            respuesta = await llm.consultar(pedido)
            return respuesta, round((time.perf_counter() - inicio) * 1000)
        except LlmNoDisponibleError as error:
            if error.status_http != 429 or intento == REINTENTOS_POR_LIMITE:
                raise
            await asyncio.sleep(ESPERA_POR_LIMITE_S)
    raise AssertionError("inalcanzable")


def resumen_markdown(evaluacion: Evaluacion) -> str:
    latencias = evaluacion.latencias
    lineas = [
        f"# Evaluación RIA01: {evaluacion.proveedor} / {evaluacion.modelo}",
        "",
        f"Prompt `{evaluacion.version_prompt}` · {len(evaluacion.casos)} tickets · {evaluacion.fecha}",
        "",
        "| Campo | Acierto |",
        "|---|---|",
        *(f"| {campo} | {evaluacion.acierto(campo):.0%} |" for campo in CAMPOS),
        "",
        f"**RIA01** (categoría ≥ {MINIMO_CATEGORIA:.0%} y escalamiento ≥ {MINIMO_ESCALAMIENTO:.0%}): "
        f"{'cumple' if evaluacion.cumple_ria01 else 'no cumple'}.",
        "",
    ]
    if latencias:
        lento = sum(latencia > LIMITE_LATENCIA_MS for latencia in latencias)
        lineas += [
            f"Latencia: mediana {statistics.median(latencias):.0f} ms, máxima {max(latencias)} ms; "
            f"{lento} de {len(latencias)} respuestas superan {LIMITE_LATENCIA_MS} ms.",
            "",
        ]
    errores = [caso for caso in evaluacion.casos if caso.error]
    if errores:
        lineas += [f"Errores: {len(errores)} ({'; '.join(f'#{c.id}: {c.error}' for c in errores)}).", ""]
    lineas += ["## Diferencias", "", "| # | Ticket | Campo | Esperado | Obtenido |", "|---|---|---|---|---|"]
    for caso in evaluacion.casos:
        if caso.obtenido is None:
            continue
        for campo in CAMPOS:
            if not caso.aciertos[campo]:
                lineas.append(f"| {caso.id} | {caso.titulo} | {campo} | {caso.esperado[campo]} | "
                              f"{caso.obtenido[campo]} |")
    return "\n".join(lineas) + "\n"


def guardar(evaluacion: Evaluacion, carpeta: Path = CARPETA_RESULTADOS) -> Path:
    carpeta.mkdir(parents=True, exist_ok=True)
    modelo = re.sub(r"[^A-Za-z0-9.-]+", "-", evaluacion.modelo)
    nombre = f"evaluacion_{evaluacion.proveedor}_{modelo}_{evaluacion.version_prompt}"
    detalle = {**asdict(evaluacion), "acierto": {campo: evaluacion.acierto(campo) for campo in CAMPOS},
               "cumpleRia01": evaluacion.cumple_ria01}
    (carpeta / f"{nombre}.json").write_text(json.dumps(detalle, ensure_ascii=False, indent=2) + "\n",
                                            encoding="utf-8")
    archivo_md = carpeta / f"{nombre}.md"
    archivo_md.write_text(resumen_markdown(evaluacion), encoding="utf-8")
    return archivo_md


async def evaluar_con_configuracion(settings: Settings) -> Evaluacion:
    llm = crear_llm_provider(settings)
    try:
        return await evaluar(llm, cargar_plantilla(settings.prompt_version), settings.prompt_version, cargar_casos())
    finally:
        await llm.cerrar()


def main() -> None:
    # La consola de Windows no usa UTF-8 por defecto.
    sys.stdout.reconfigure(encoding="utf-8")
    evaluacion = asyncio.run(evaluar_con_configuracion(Settings()))
    archivo = guardar(evaluacion)
    print(resumen_markdown(evaluacion))
    print(f"Resultados guardados en {archivo.parent}")


if __name__ == "__main__":
    main()
