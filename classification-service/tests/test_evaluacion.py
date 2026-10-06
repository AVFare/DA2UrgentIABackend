"""Evaluacion del componente de IA (RIA01).

Los tests del set etiquetado y del evaluador corren siempre. El de RIA01 consulta al proveedor
real y corre solo con EVALUAR_RIA01=1 y la configuracion del proveedor, por ejemplo:
    EVALUAR_RIA01=1 LLM_PROVIDER=ollama LLM_BASE_URL=http://localhost:11434/v1 pytest tests/test_evaluacion.py
Guarda los resultados en tests/data/resultados/.
"""

import asyncio
import os
from collections import Counter

import pytest

from app.config import Settings
from app.domain.enums import Categoria, Impacto, ModuloAfectado, Urgencia
from app.infrastructure.llm.mock_provider import MockLlmProvider
from app.infrastructure.prompts import cargar_plantilla
from scripts.evaluar import (
    MINIMO_CATEGORIA,
    MINIMO_ESCALAMIENTO,
    cargar_casos,
    evaluar,
    evaluar_con_configuracion,
    guardar,
    prioridad,
    resumen_markdown,
)

CASOS = cargar_casos()


def test_el_set_tiene_20_tickets_con_ids_unicos():
    assert len(CASOS) == 20
    assert sorted(caso["id"] for caso in CASOS) == list(range(1, 21))


@pytest.mark.parametrize("caso", CASOS, ids=lambda caso: f"#{caso['id']}")
def test_cada_etiqueta_es_valida_y_la_prioridad_sigue_la_matriz(caso):
    esperado = caso["esperado"]

    assert esperado["categoria"] in set(Categoria)
    assert esperado["urgencia"] in set(Urgencia)
    assert esperado["impacto"] in set(Impacto)
    assert esperado["moduloAfectado"] in set(ModuloAfectado)
    assert isinstance(esperado["requiereEscalamiento"], bool)
    assert esperado["prioridad"] == prioridad(esperado["urgencia"], esperado["impacto"],
                                              esperado["requiereEscalamiento"])


def test_el_set_cubre_todas_las_categorias_y_casos_de_escalamiento():
    categorias = Counter(caso["esperado"]["categoria"] for caso in CASOS)
    escalados = sum(caso["esperado"]["requiereEscalamiento"] for caso in CASOS)

    assert set(categorias) == set(Categoria)
    assert escalados >= 4


def test_el_evaluador_mide_y_resume_con_el_mock(tmp_path):
    evaluacion = asyncio.run(evaluar(MockLlmProvider(), cargar_plantilla("v1"), "v1", CASOS))

    assert (evaluacion.proveedor, evaluacion.modelo) == ("mock", "mock-v1")
    assert len(evaluacion.casos) == 20
    assert all(caso.error is None for caso in evaluacion.casos)
    assert 0 <= evaluacion.acierto("categoria") <= 1
    resumen = resumen_markdown(evaluacion)
    assert "| categoria |" in resumen
    archivo = guardar(evaluacion, tmp_path)
    assert archivo.name == "evaluacion_mock_mock-v1_v1.md"
    assert (tmp_path / "evaluacion_mock_mock-v1_v1.json").is_file()


@pytest.mark.skipif(os.getenv("EVALUAR_RIA01") != "1", reason="Definir EVALUAR_RIA01=1 para evaluar el proveedor real")
def test_ria01_con_el_proveedor_real():
    evaluacion = asyncio.run(evaluar_con_configuracion(Settings()))
    guardar(evaluacion)

    assert evaluacion.acierto("categoria") >= MINIMO_CATEGORIA, resumen_markdown(evaluacion)
    assert evaluacion.acierto("requiereEscalamiento") >= MINIMO_ESCALAMIENTO, resumen_markdown(evaluacion)
