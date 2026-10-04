export enum Prioridad {
  P1 = 'P1',
  P2 = 'P2',
  P3 = 'P3',
  P4 = 'P4',
}
export enum Categoria {
  INCIDENTE = 'INCIDENTE',
  SOLICITUD = 'SOLICITUD',
  CONSULTA = 'CONSULTA',
  BUG = 'BUG',
}
export enum EstadoTicket {
  NUEVO = 'NUEVO',
  PENDIENTE_CLASIFICACION = 'PENDIENTE_CLASIFICACION',
  CLASIFICADO = 'CLASIFICADO',
  ASIGNADO = 'ASIGNADO',
  EN_CURSO = 'EN_CURSO',
  ESCALADO = 'ESCALADO',
  RESUELTO = 'RESUELTO',
  CERRADO = 'CERRADO',
}
export enum ModuloAfectado {
  AUTENTICACION = 'AUTENTICACION',
  FACTURACION = 'FACTURACION',
  PAGOS = 'PAGOS',
  REPORTES = 'REPORTES',
  INFRAESTRUCTURA = 'INFRAESTRUCTURA',
  BASE_DE_DATOS = 'BASE_DE_DATOS',
  INTEGRACIONES = 'INTEGRACIONES',
  OTRO = 'OTRO',
}
export enum TipoEvento {
  TicketCreado = 'TicketCreado',
  TicketClasificado = 'TicketClasificado',
  TicketEscalado = 'TicketEscalado',
  TicketAsignado = 'TicketAsignado',
  TicketEstadoCambiado = 'TicketEstadoCambiado',
  TicketResuelto = 'TicketResuelto',
}
export const ESTADOS_TERMINADOS = [EstadoTicket.RESUELTO, EstadoTicket.CERRADO];
