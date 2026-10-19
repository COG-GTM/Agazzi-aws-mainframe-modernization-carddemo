import { describe, expect, it } from 'vitest';
import { displayValue, type BmsField } from '../../src/terminal/layout';

const field = (justify?: string): BmsField => ({ row: 1, col: 1, length: 8, attrb: ['ASKIP'], justify }) as unknown as BmsField;

describe('displayValue', () => {
  it('pads JUSTIFY=RIGHT values to the field LENGTH on the left', () => {
    expect(displayValue(field('RIGHT'), '42')).toBe('      42');
  });
  it('leaves other fields left-aligned and truncates to LENGTH', () => {
    expect(displayValue(field(), '42')).toBe('42');
    expect(displayValue(field(), '1234567890')).toBe('12345678');
  });
});
