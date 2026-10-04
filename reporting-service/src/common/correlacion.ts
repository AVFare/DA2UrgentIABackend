import { AsyncLocalStorage } from 'node:async_hooks';
export const correlacion = new AsyncLocalStorage<string>();
