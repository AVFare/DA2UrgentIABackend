"""Exporta el OpenAPI de la app a contracts/classification-service.yaml.

Uso (desde classification-service/, con requirements-dev.txt instalado):
    python -m scripts.exportar_openapi

Correrlo cada vez que cambie la API y commitear el contrato junto con el codigo.
"""

from pathlib import Path

import yaml

from app.main import app

DESTINO = Path(__file__).resolve().parents[2] / "contracts" / "classification-service.yaml"

ENCABEZADO = (
    "# Generado desde el codigo con: python -m scripts.exportar_openapi (classification-service).\n"
    "# No editar a mano: cambiar la API y volver a exportar.\n"
)


class _Dumper(yaml.SafeDumper):
    """Escribe los textos de varias lineas como bloque literal (|), mas faciles de leer."""


def _representar_texto(dumper: yaml.SafeDumper, texto: str) -> yaml.ScalarNode:
    estilo = "|" if "\n" in texto else None
    return dumper.represent_scalar("tag:yaml.org,2002:str", texto, style=estilo)


_Dumper.add_representer(str, _representar_texto)


def main() -> None:
    contenido = yaml.dump(app.openapi(), Dumper=_Dumper, sort_keys=False, allow_unicode=True, width=110)
    DESTINO.write_text(ENCABEZADO + contenido, encoding="utf-8")
    print(f"Contrato exportado a {DESTINO}")


if __name__ == "__main__":
    main()
