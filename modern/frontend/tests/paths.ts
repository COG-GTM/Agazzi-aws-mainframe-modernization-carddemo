import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parse } from 'yaml';

export const FRONTEND = join(dirname(fileURLToPath(import.meta.url)), '..');
export const MODERN = join(FRONTEND, '..');
export const OPENAPI = join(MODERN, 'carddemo-contracts/src/main/resources/openapi/openapi.yaml');
export const SCREEN_ID_JAVA = join(MODERN, 'carddemo-contracts/src/main/java/com/carddemo/contracts/screen/ScreenId.java');
export const BMS_MD = join(MODERN, 'docs/inventory/bms.md');

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type Json = any;
export const openapi = (): Json => parse(readFileSync(OPENAPI, 'utf8'));
