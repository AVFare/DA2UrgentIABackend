FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /service

# Primero las dependencias: si solo cambia el codigo, Docker reusa esta capa.
COPY requirements.txt .
RUN pip install -r requirements.txt

COPY app ./app
COPY prompts ./prompts

RUN useradd --create-home --uid 10001 servicio
USER servicio

EXPOSE 8082

CMD ["python", "-m", "app.main"]
