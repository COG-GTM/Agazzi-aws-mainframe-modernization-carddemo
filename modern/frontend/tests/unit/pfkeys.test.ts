import { describe, expect, it } from 'vitest';
import { actionForKey, AID_KEYS } from '../../src/keyboard/pfkeys';
import { openapi } from '../paths';

describe('PF key shortcuts', () => {
  it('maps keyboard keys to DFHAID actions', () => {
    expect(actionForKey({ key: 'Enter' })).toBe('ENTER');
    expect(actionForKey({ key: 'Escape' })).toBe('CLEAR');
    expect(actionForKey({ key: 'Pause' })).toBe('CLEAR');
    expect(actionForKey({ key: 'F3' })).toBe('PF3');
    expect(actionForKey({ key: 'F12' })).toBe('PF12');
    expect(actionForKey({ key: 'F7', altKey: true })).toBe('PF7');
    expect(actionForKey({ key: '1', altKey: true })).toBe('PA1');
    expect(actionForKey({ key: '2', altKey: true })).toBe('PA2');
  });

  it('ignores ordinary typing and browser shortcuts', () => {
    expect(actionForKey({ key: 'a' })).toBeUndefined();
    expect(actionForKey({ key: '1' })).toBeUndefined();
    expect(actionForKey({ key: 'F5', ctrlKey: true })).toBeUndefined();
    expect(actionForKey({ key: 'F13' })).toBeUndefined();
    expect(actionForKey({ key: 'Enter', shiftKey: true })).toBeUndefined();
  });

  it('covers exactly the Action enum of openapi.yaml', () => {
    expect([...AID_KEYS].sort()).toEqual([...openapi().components.schemas.Action.enum].sort());
  });
});
