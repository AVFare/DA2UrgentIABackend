class ApiException(Exception):
    """Error controlado. Sale con el formato comun de error (contexto, seccion 5.3)."""

    def __init__(self, status: int, codigo: str, mensaje: str, detalles: list[dict[str, str]] | None = None):
        super().__init__(mensaje)
        self.status = status
        self.codigo = codigo
        self.mensaje = mensaje
        self.detalles = detalles
