import { setupServer } from 'msw/node';
import { handlers } from './handlers';

/** Node server used by Vitest. */
export const server = setupServer(...handlers);
