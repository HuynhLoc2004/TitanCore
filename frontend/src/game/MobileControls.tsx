import { useEffect, useRef } from 'react';
import type { InputAction, UnifiedInputState } from './world/input/UnifiedInputState';

type MobileControlsProps = {
  input: UnifiedInputState;
  disabled: boolean;
};

export function MobileControls({ input, disabled }: MobileControlsProps) {
  const padRef = useRef<HTMLDivElement>(null);
  const knobRef = useRef<HTMLDivElement>(null);

  useEffect(() => () => input.releaseSource('TOUCH'), [input]);

  const updateJoystick = (event: React.PointerEvent<HTMLDivElement>) => {
    if (disabled || !padRef.current || !knobRef.current) return;
    const bounds = padRef.current.getBoundingClientRect();
    const radius = bounds.width / 2;
    const x = (event.clientX - (bounds.left + radius)) / radius;
    const y = (event.clientY - (bounds.top + radius)) / radius;
    const length = Math.hypot(x, y);
    const scale = length > 1 ? 1 / length : 1;
    const moveX = x * scale;
    const moveY = y * scale;
    const deadZone = 0.16;
    input.setMove('TOUCH', length < deadZone ? 0 : moveX, length < deadZone ? 0 : moveY);
    knobRef.current.style.transform = `translate(${moveX * 34}px, ${moveY * 34}px)`;
  };
  const releaseJoystick = (event: React.PointerEvent<HTMLDivElement>) => {
    input.setMove('TOUCH', 0, 0);
    if (knobRef.current) knobRef.current.style.transform = 'translate(0, 0)';
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
  };

  return (
    <div className="tc-mobile-controls" aria-label="Mobile game controls">
      <div
        ref={padRef}
        className="tc-mobile-controls__joystick"
        role="application"
        aria-label="Movement joystick"
        onPointerDown={(event) => {
          event.currentTarget.setPointerCapture(event.pointerId);
          updateJoystick(event);
        }}
        onPointerMove={(event) => {
          if (event.currentTarget.hasPointerCapture(event.pointerId)) updateJoystick(event);
        }}
        onPointerUp={releaseJoystick}
        onPointerCancel={releaseJoystick}
      >
        <div ref={knobRef} className="tc-mobile-controls__knob" />
      </div>
      <div className="tc-mobile-controls__actions">
        <ActionButton label="Skill 1" text="1" action="SKILL_1" input={input} disabled={disabled} />
        <ActionButton label="Skill 2" text="2" action="SKILL_2" input={input} disabled={disabled} />
        <ActionButton label="Skill 3" text="3" action="SKILL_3" input={input} disabled={disabled} />
        <ActionButton label="Skill 4" text="4" action="SKILL_4" input={input} disabled={disabled} />
        <ActionButton label="Dodge" text="D" action="DODGE" input={input} disabled={disabled} />
        <ActionButton label="Basic attack" text="ATK" action="ATTACK" input={input} disabled={disabled} primary />
      </div>
    </div>
  );
}

function ActionButton({
  label,
  text,
  action,
  input,
  disabled,
  primary = false,
}: {
  label: string;
  text: string;
  action: InputAction;
  input: UnifiedInputState;
  disabled: boolean;
  primary?: boolean;
}) {
  const release = () => input.setAction('TOUCH', action, false);
  return (
    <button
      type="button"
      className={primary ? 'tc-mobile-controls__button is-primary' : 'tc-mobile-controls__button'}
      aria-label={label}
      disabled={disabled}
      onPointerDown={(event) => {
        event.currentTarget.setPointerCapture(event.pointerId);
        input.setAction('TOUCH', action, true);
      }}
      onPointerUp={release}
      onPointerCancel={release}
      onLostPointerCapture={release}
    >
      {text}
    </button>
  );
}
