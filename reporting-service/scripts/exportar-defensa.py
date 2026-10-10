"""Exporta los borradores de P6 y las respuestas reales de la demo a PDF.

Uso desde reporting-service: python3 scripts/exportar-defensa.py
Requiere reportlab (scripts/requirements-docs.txt). No altera archivos del equipo.
"""
from pathlib import Path
from xml.sax.saxutils import escape
import json
import re
import textwrap

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4, landscape
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Preformatted, PageBreak

RAIZ = Path(__file__).resolve().parents[1]
DOCS = RAIZ / 'docs'
TINTA = colors.HexColor('#15332D')
VERDE = colors.HexColor('#0B765A')

font = Path('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf')
if font.exists():
    pdfmetrics.registerFont(TTFont('P6', str(font)))
    FUENTE = 'P6'
else:
    FUENTE = 'Helvetica'

estilos = getSampleStyleSheet()
estilos.add(ParagraphStyle('P6Texto', fontName=FUENTE, fontSize=10, leading=15, spaceAfter=8, textColor=TINTA))
estilos.add(ParagraphStyle('P6Titulo', fontName=FUENTE, fontSize=23, leading=29, spaceAfter=18, textColor=VERDE))
estilos.add(ParagraphStyle('P6Seccion', fontName=FUENTE, fontSize=14, leading=20, spaceBefore=12, spaceAfter=10, textColor=VERDE))
estilos.add(ParagraphStyle('P6Codigo', fontName='Courier', fontSize=7.5, leading=10, spaceAfter=12))


def pie(c, doc):
    c.saveState()
    c.setFont(FUENTE, 8)
    c.setFillColor(TINTA)
    c.drawString(42, 26, 'UrgentIA · P6 · borrador para revision del equipo')
    c.drawRightString(A4[0] - 42, 26, str(doc.page))
    c.restoreState()


def markdown(texto):
    salida = []
    bloques = []
    codigo = False
    for linea in texto.splitlines() + ['']:
        if linea.startswith('```'):
            codigo = not codigo
            continue
        if codigo:
            salida.append(Preformatted(linea, estilos['P6Codigo']))
            continue
        if linea.startswith('#') or not linea.strip() or linea.startswith('- ') or linea.startswith('|'):
            if bloques:
                salida.append(Paragraph(escape(' '.join(bloques)), estilos['P6Texto']))
                bloques = []
            if linea.startswith('# '):
                salida.append(Paragraph(escape(linea[2:]), estilos['P6Titulo']))
            elif linea.startswith('## '):
                salida.append(Paragraph(escape(linea[3:]), estilos['P6Seccion']))
            elif linea.startswith('- '):
                salida.append(Paragraph('• ' + escape(linea[2:]), estilos['P6Texto']))
            elif linea.startswith('|') and not re.fullmatch(r'[\s|:-]+', linea):
                salida.append(Paragraph(escape(linea.strip('| ').replace(' | ', ' · ')), estilos['P6Texto']))
        else:
            bloques.append(linea.strip())
    return salida


def informe():
    piezas = markdown((DOCS / 'informe-p6.md').read_text())
    evidencia = DOCS / 'evidencias.json'
    if evidencia.exists():
        datos = json.loads(evidencia.read_text())
        piezas += [PageBreak(), Paragraph('Respuestas HTTP reales · demo local P6', estilos['P6Titulo']),
                   Paragraph(escape('Captura textual obtenida en ' + datos['generadoEn']), estilos['P6Texto']),
                   Paragraph(escape(datos['entorno']['modalidad']), estilos['P6Texto'])]
        for r in datos['resultados']:
            piezas.append(Paragraph(escape(f"{r['nombre']} · {r['metodo']} {r['ruta']} → HTTP {r['status']}"), estilos['P6Seccion']))
            codigo = json.dumps(r['respuesta'], ensure_ascii=False, indent=2)
            lineas = []
            for linea in codigo.splitlines():
                lineas.extend(textwrap.wrap(linea, width=88, replace_whitespace=False, drop_whitespace=False) or [''])
            # Un flowable por linea evita bloquear el salto de pagina en JSON largo.
            piezas.extend(Preformatted(linea, estilos['P6Codigo']) for linea in lineas)
            piezas.append(Spacer(1, 10))
    SimpleDocTemplate(str(DOCS / 'informe-p6.pdf'), pagesize=A4, rightMargin=42,
                      leftMargin=42, topMargin=40, bottomMargin=42,
                      title='UrgentIA - Informe P6 (borrador)', author='Francisco Eduardo Nappa').build(
        piezas, onFirstPage=pie, onLaterPages=pie)


def diagrama(c, width):
    """Representacion vectorial de la arquitectura acordada para la diapositiva."""
    nombres = ['Usuarios\nJava · PostgreSQL', 'Tickets\nJava · PostgreSQL', 'Clasificacion\nPython · MongoDB',
               'Notificaciones\nPython · MongoDB', 'Reportes (P6)\nNestJS · MongoDB']
    x0, y, ancho, alto, espacio = 45, 82, 134, 68, 15
    centro = width / 2
    c.setStrokeColor(VERDE)
    c.setFillColor(colors.HexColor('#EAF5F0'))
    c.roundRect(centro - 130, y + 124, 260, 45, 8, fill=1)
    c.setFillColor(TINTA); c.setFont(FUENTE, 12)
    c.drawCentredString(centro, y + 141, 'Gateway · entrada publica / JWT')
    for i, nombre in enumerate(nombres):
        x = x0 + i * (ancho + espacio)
        c.setFillColor(colors.HexColor('#D3EDE1') if i == 4 else colors.HexColor('#F1F4F3'))
        c.roundRect(x, y, ancho, alto, 8, fill=1)
        c.line(centro, y + 124, x + ancho / 2, y + alto)
        c.setFillColor(TINTA); c.setFont(FUENTE, 9)
        for j, linea in enumerate(nombre.split('\n')):
            c.drawCentredString(x + ancho / 2, y + 40 - j * 15, linea)
    c.setFont(FUENTE, 8)
    c.drawString(45, y - 20, 'Tickets → Clasificacion (REST); Tickets → Notificaciones y Reportes (eventos HTTP).')


def slides():
    texto = (DOCS / 'slides-p6.md').read_text()
    secciones = re.split(r'^## ', texto, flags=re.M)[1:]
    width, height = landscape(A4)
    c = canvas.Canvas(str(DOCS / 'slides-p6.pdf'), pagesize=(width, height))
    c.setTitle('UrgentIA - Slides de defensa (borrador)')
    c.setAuthor('Francisco Eduardo Nappa')
    for n, bloque in enumerate(secciones, 1):
        titulo, _, cuerpo = bloque.partition('\n')
        c.setFillColor(colors.HexColor('#F7FAF8')); c.rect(0, 0, width, height, fill=1, stroke=0)
        c.setFillColor(VERDE); c.rect(0, height - 15, width, 15, fill=1, stroke=0)
        c.setFont(FUENTE, 10); c.drawString(45, height - 47, 'URGENTIA / DEFENSA 1')
        c.setFillColor(TINTA); c.setFont(FUENTE, 24); c.drawString(45, height - 95, titulo)
        # Une las continuaciones de cada viñeta del Markdown.
        bullets = []
        for linea in cuerpo.splitlines():
            if linea.startswith('- '): bullets.append(linea[2:])
            elif linea.strip() and bullets: bullets[-1] += ' ' + linea.strip()
        y = height - 148
        for bullet in bullets:
            parrafo = Paragraph('• ' + escape(bullet), ParagraphStyle('Slide', fontName=FUENTE, fontSize=14, leading=21, textColor=TINTA))
            _, h = parrafo.wrap(width - 100, height)
            parrafo.drawOn(c, 50, y - h); y -= h + 18
        if n == 2:
            diagrama(c, width)
        c.setFillColor(VERDE); c.setFont(FUENTE, 8)
        c.drawString(45, 26, 'BORRADOR · P1–P5 requieren revision; solo P6 verificado en esta rama')
        c.drawRightString(width - 45, 26, f'{n} / {len(secciones)}')
        c.showPage()
    c.save()


if __name__ == '__main__':
    informe()
    slides()
    print('Exportados docs/informe-p6.pdf y docs/slides-p6.pdf (borradores).')
