import { ServiceUnavailableException } from '@nestjs/common';
import { Connection } from 'mongoose';
import { HealthController } from './health.controller';

describe('HealthController', () => {
  it('responde UP cuando MongoDB responde al ping', async () => {
    const ping = jest.fn().mockResolvedValue({ ok: 1 });
    const controller = new HealthController({
      db: { admin: () => ({ ping }) },
    } as unknown as Connection);
    await expect(controller.consultar()).resolves.toEqual({
      status: 'UP',
      service: 'reporting-service',
      version: '0.1.0',
    });
    expect(ping).toHaveBeenCalled();
  });
  it('no informa UP si MongoDB falla', async () => {
    const controller = new HealthController({
      db: {
        admin: () => ({ ping: () => Promise.reject(new Error('conexion')) }),
      },
    } as unknown as Connection);
    await expect(controller.consultar()).rejects.toBeInstanceOf(
      ServiceUnavailableException,
    );
  });
});
