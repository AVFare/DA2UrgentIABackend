import { connect } from 'amqplib';

export const RABBITMQ_URL = Symbol('RABBITMQ_URL');
export const CONECTAR_RABBITMQ = Symbol('CONECTAR_RABBITMQ');
export type ConectarRabbitmq = (url: string) => ReturnType<typeof connect>;

export const proveedoresRabbitmq = [
  { provide: RABBITMQ_URL, useFactory: () => process.env.RABBITMQ_URL },
  {
    provide: CONECTAR_RABBITMQ,
    useValue: (url: string) => connect(url, { timeout: 5000 }),
  },
];
