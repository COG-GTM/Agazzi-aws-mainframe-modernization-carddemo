import { spawn, type ChildProcess } from 'node:child_process';
import { join } from 'node:path';
import type { GlobalSetupContext } from 'vitest/node';
import { FRONTEND, OPENAPI } from '../paths';

let prism: ChildProcess | undefined;

/** Starts a Prism mock generated from openapi.yaml (request validation on) for the contract tests. */
export default async function setup({ provide }: GlobalSetupContext) {
  const port = Number(process.env.PRISM_PORT ?? 4010 + Math.floor(Math.random() * 500));
  const bin = join(FRONTEND, 'node_modules/.bin/prism');
  prism = spawn(bin, ['mock', '-p', String(port), '-h', '127.0.0.1', OPENAPI], { stdio: ['ignore', 'pipe', 'pipe'] });
  let log = '';
  await new Promise<void>((resolve, reject) => {
    const t = setTimeout(() => reject(new Error(`Prism did not start:\n${log}`)), 55000);
    const on = (b: Buffer) => {
      log += b.toString();
      if (/Prism is listening/.test(log)) {
        clearTimeout(t);
        resolve();
      }
    };
    prism!.stdout!.on('data', on);
    prism!.stderr!.on('data', on);
    prism!.on('exit', (c) => reject(new Error(`Prism exited ${c}:\n${log}`)));
  });
  provide('prismUrl', `http://127.0.0.1:${port}`);
  return () => {
    prism?.kill();
  };
}

declare module 'vitest' {
  export interface ProvidedContext {
    prismUrl: string;
  }
}
