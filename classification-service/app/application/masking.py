"""Enmascarado de datos personales antes de mandar el texto al LLM (CONTEXTO_PROYECTO.md, 9.3).

- emails -> [EMAIL]
- DNI: 7 u 8 digitos seguidos, o con puntos (30.123.456) -> [DNI]
- telefonos: 8 o mas digitos con espacios, guiones o + (11-5555-1234, +54 9 11 5555-1234),
  o 9 o mas digitos seguidos -> [TELEFONO]

Un numero suelto de 7 u 8 digitos es ambiguo (DNI o telefono fijo): se enmascara como DNI.
Lo importante es que no salga, no la etiqueta. No se tocan montos con $ ni fechas ISO.
"""

import re

_EMAIL = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}")

# Que el numero no este pegado a una letra, a otro numero con punto (montos, decimales) ni a un $.
_ANTES = r"(?<![\w$])(?<!\d\.)(?<!\$ )"
_DESPUES = r"(?!\w)(?!\.\d)"

_DNI_CON_PUNTOS = re.compile(_ANTES + r"\d{1,2}\.\d{3}\.\d{3}" + _DESPUES)

# Uno o mas digitos con un espacio o guion opcional entre medio; minimo 7 digitos.
_NUMERO_LARGO = re.compile(_ANTES + r"\+?\d(?:[ -]?\d){6,}" + _DESPUES)

_FECHA_ISO = re.compile(r"\d{4}-\d{2}-\d{2}")


def enmascarar(texto: str) -> str:
    texto = _EMAIL.sub("[EMAIL]", texto)
    texto = _DNI_CON_PUNTOS.sub("[DNI]", texto)
    return _NUMERO_LARGO.sub(_reemplazar_numero, texto)


def _reemplazar_numero(match: re.Match[str]) -> str:
    numero = match.group()
    digitos = sum(caracter.isdigit() for caracter in numero)
    if numero.isdigit() and digitos <= 8:
        return "[DNI]"
    if digitos >= 8 and not _FECHA_ISO.fullmatch(numero):
        return "[TELEFONO]"
    return numero
