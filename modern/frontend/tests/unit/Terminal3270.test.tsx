import { fireEvent, render } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { Terminal3270 } from '../../src/terminal/Terminal3270';
import { colorOf, layoutOf, type BmsField } from '../../src/terminal/layout';

const sgn = layoutOf('COSGN00');

function renderSignOn(over: Partial<Parameters<typeof Terminal3270>[0]> = {}) {
  const props = {
    map: sgn,
    output: {},
    input: { USERID: '', PASSWD: '' },
    fieldErrors: [],
    onInput: vi.fn(),
    onAid: vi.fn(),
    ...over,
  };
  return { ...render(<Terminal3270 {...props} />), props };
}

describe('Terminal3270', () => {
  it('renders unprotected fields as inputs at their BMS position and length', () => {
    const { container } = renderSignOn();
    const user = container.querySelector<HTMLInputElement>('input[name="USERID"]')!;
    const pass = container.querySelector<HTMLInputElement>('input[name="PASSWD"]')!;
    expect(user.maxLength).toBe(8);
    expect(user.dataset.row).toBe('19');
    expect(user.dataset.col).toBe('43');
    expect(user.style.left).toBe('43ch');
    expect(pass.type).toBe('password'); // ATTRB=DRK
    expect(document.activeElement).toBe(user); // ATTRB=IC
    expect(container.querySelectorAll('input')).toHaveLength(2);
  });

  it('renders literals (INITIAL) and output data, ERRMSG bright red', () => {
    const { container } = renderSignOn({ output: { TRNNAME: 'CC00', ERRMSG: 'Wrong Password. Try again ...' } });
    expect(container.textContent).toContain('Type your User ID and Password, then press ENTER:');
    expect(container.querySelector('[data-field="TRNNAME"]')!.textContent).toBe('CC00');
    const err = container.querySelector('[data-field="ERRMSG"]')!;
    expect(err.textContent).toBe('Wrong Password. Try again ...');
    expect(err.className).toContain('c-red');
    expect(err.className).toContain('brt');
  });

  it('maps keyboard AID keys to onAid', () => {
    const { container, props } = renderSignOn();
    const user = container.querySelector('input[name="USERID"]')!;
    fireEvent.keyDown(user, { key: 'Enter' });
    fireEvent.keyDown(user, { key: 'F3' });
    fireEvent.keyDown(user, { key: 'x' });
    expect(vi.mocked(props.onAid).mock.calls).toEqual([['ENTER'], ['PF3']]);
  });

  it('does not send AID keys while busy', () => {
    const { container, props } = renderSignOn({ busy: true });
    fireEvent.keyDown(container.querySelector('input')!, { key: 'Enter' });
    expect(props.onAid).not.toHaveBeenCalled();
  });

  it('reports typing and auto-skips to the next unprotected field when full', () => {
    const { container, props } = renderSignOn();
    const user = container.querySelector<HTMLInputElement>('input[name="USERID"]')!;
    fireEvent.change(user, { target: { value: 'ADMIN001' } });
    expect(props.onInput).toHaveBeenCalledWith('USERID', 'ADMIN001');
    expect(document.activeElement).toBe(container.querySelector('input[name="PASSWD"]'));
  });

  it('marks fields in error and puts the cursor there', () => {
    const { container } = renderSignOn({ fieldErrors: [{ field: 'PASSWD', message: 'Please enter Password ...' }] });
    const pass = container.querySelector<HTMLInputElement>('input[name="PASSWD"]')!;
    expect(pass.getAttribute('aria-invalid')).toBe('true');
    expect(pass.title).toBe('Please enter Password ...');
    expect(document.activeElement).toBe(pass);
  });

  it('keeps DRK output fields hidden until the program sends a value', () => {
    const map = layoutOf('COACTUP');
    const base = { map, input: {}, fieldErrors: [], onInput: vi.fn(), onAid: vi.fn() };
    const hidden = render(<Terminal3270 {...base} output={{}} />);
    expect(hidden.container.querySelector('[data-field="FKEY05"]')).toBeNull();
    hidden.unmount();
    const shown = render(<Terminal3270 {...base} output={{ FKEY05: 'F5=Save' }} />);
    expect(shown.container.querySelector('[data-field="FKEY05"]')!.textContent).toBe('F5=Save');
  });

  it('accepts only digits in NUM fields', () => {
    const map = layoutOf('COACTUP');
    const num = map.fields.find((f) => f.attrb.includes('NUM') && f.attrb.includes('UNPROT'));
    if (!num) return;
    const onInput = vi.fn();
    const { container } = render(
      <Terminal3270 map={map} output={{}} input={{}} fieldErrors={[]} onInput={onInput} onAid={vi.fn()} />,
    );
    fireEvent.change(container.querySelector(`input[name="${num.name}"]`)!, { target: { value: '12ab3' } });
    expect(onInput).toHaveBeenCalledWith(num.name, '123');
  });
});

describe('DFHBMSCA colours', () => {
  const f = (attrb: string[], color?: string): BmsField => ({ name: 'X', row: 1, col: 1, length: 1, attrb, color });
  it('uses COLOR= when present', () => expect(colorOf(f(['ASKIP'], 'YELLOW'))).toBe('YELLOW'));
  it('falls back to the 4-colour base defaults', () => {
    expect(colorOf(f(['ASKIP', 'NORM']))).toBe('BLUE');
    expect(colorOf(f(['ASKIP', 'BRT']))).toBe('NEUTRAL');
    expect(colorOf(f(['UNPROT']))).toBe('GREEN');
    expect(colorOf(f(['UNPROT', 'BRT'], 'DEFAULT'))).toBe('RED');
  });
});
