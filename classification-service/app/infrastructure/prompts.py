"""Lee las plantillas del prompt versionadas en prompts/clasificacion_{version}.txt."""

from pathlib import Path

CARPETA_PROMPTS = Path(__file__).resolve().parents[2] / "prompts"


def cargar_plantilla(version: str, carpeta: Path = CARPETA_PROMPTS) -> str:
    archivo = carpeta / f"clasificacion_{version}.txt"
    if not archivo.is_file():
        raise FileNotFoundError(f"No existe el prompt {archivo.name} (PROMPT_VERSION={version})")
    return archivo.read_text(encoding="utf-8")
