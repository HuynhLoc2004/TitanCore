import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MobileControls } from './MobileControls';
import { UnifiedInputState } from './world/input/UnifiedInputState';

describe('MobileControls', () => {
  it('captures joystick movement and releases it on pointer up', () => {
    const input = new UnifiedInputState();
    const { container } = render(<MobileControls input={input} disabled={false} />);
    const joystick = screen.getByRole('application', { name: /movement joystick/i });
    stubPointerCapture(joystick);
    vi.spyOn(joystick, 'getBoundingClientRect').mockReturnValue(rect(0, 0, 100, 100));

    fireEvent.pointerDown(joystick, { pointerId: 7, clientX: 100, clientY: 50 });
    expect(input.snapshot()).toMatchObject({ moveX: 1, moveY: 0 });

    fireEvent.pointerUp(joystick, { pointerId: 7 });
    expect(input.snapshot()).toMatchObject({ moveX: 0, moveY: 0 });
    expect(container.querySelector('.tc-mobile-controls__knob')).toHaveStyle({
      transform: 'translate(0, 0)',
    });
  });

  it('tracks action presses and clears touch state when unmounted', () => {
    const input = new UnifiedInputState();
    const view = render(<MobileControls input={input} disabled={false} />);
    const attack = screen.getByRole('button', { name: /basic attack/i });
    stubPointerCapture(attack);

    fireEvent.pointerDown(attack, { pointerId: 3 });
    expect(input.snapshot().actions.has('ATTACK')).toBe(true);

    view.unmount();
    expect(input.snapshot().actions.size).toBe(0);
  });
});

function stubPointerCapture(element: HTMLElement) {
  let captured = false;
  element.setPointerCapture = vi.fn(() => {
    captured = true;
  });
  element.hasPointerCapture = vi.fn(() => captured);
  element.releasePointerCapture = vi.fn(() => {
    captured = false;
  });
}

function rect(x: number, y: number, width: number, height: number): DOMRect {
  return {
    x,
    y,
    width,
    height,
    top: y,
    right: x + width,
    bottom: y + height,
    left: x,
    toJSON: () => ({}),
  };
}
