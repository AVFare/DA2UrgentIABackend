"""Base de los DTOs: atributos en snake_case que se serializan en camelCase (seccion 5)."""

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel


class CamelModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)
