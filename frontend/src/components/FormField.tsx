import { useId } from 'react';

import { input } from './styles';

interface FormFieldProps {
  readonly label: string;
  readonly value: string;
  readonly onChange: (value: string) => void;
  readonly error?: string | undefined;
  readonly hint?: string;
  readonly required?: boolean;
  readonly type?: 'text' | 'email' | 'url' | 'tel' | 'search' | 'password';
  readonly multiline?: boolean;
  readonly maxLength?: number;
  readonly autoComplete?: string;
  /** Values already in use, suggested while typing (existing segments, say); not a restriction. */
  readonly suggestions?: readonly string[];
}

/**
 * A field with its label, hint and error associated (doc 08 §12).
 *
 * The error is tied to the field through `aria-describedby`, so a screen
 * reader announces it when the field takes focus, rather than leaving it as
 * loose text on the page.
 */
export function FormField({
  label,
  value,
  onChange,
  error,
  hint,
  required = false,
  type = 'text',
  multiline = false,
  maxLength,
  autoComplete,
  suggestions,
}: FormFieldProps): React.JSX.Element {
  const id = useId();
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const describedBy = [hint === undefined ? null : hintId, error === undefined ? null : errorId]
    .filter((item) => item !== null)
    .join(' ');

  const common = {
    id,
    value,
    required,
    'aria-invalid': error !== undefined,
    'aria-describedby': describedBy === '' ? undefined : describedBy,
    className: input,
    ...(maxLength === undefined ? {} : { maxLength }),
  };

  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium text-stone-700">
        {label}
        {required && (
          <span className="text-red-700" aria-hidden="true">
            {' '}
            *
          </span>
        )}
      </label>
      {multiline ? (
        <textarea
          {...common}
          rows={3}
          onChange={(event) => {
            onChange(event.target.value);
          }}
        />
      ) : (
        <input
          {...common}
          type={type}
          {...(autoComplete === undefined ? {} : { autoComplete })}
          {...(suggestions === undefined || suggestions.length === 0 ? {} : { list: `${id}-list` })}
          onChange={(event) => {
            onChange(event.target.value);
          }}
        />
      )}
      {suggestions !== undefined && suggestions.length > 0 && (
        <datalist id={`${id}-list`}>
          {suggestions.map((suggestion) => (
            <option key={suggestion} value={suggestion}>
              {suggestion}
            </option>
          ))}
        </datalist>
      )}
      {hint !== undefined && (
        <p id={hintId} className="mt-1 text-xs text-stone-500">
          {hint}
        </p>
      )}
      {error !== undefined && (
        <p id={errorId} className="mt-1 text-sm text-red-700">
          {error}
        </p>
      )}
    </div>
  );
}
