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
}

/**
 * Campo com rótulo, dica e erro associados (doc 08 §12).
 *
 * O erro fica ligado ao campo por `aria-describedby`, de modo que o leitor de
 * tela o anuncia ao focar o campo, e não apenas como texto solto na página.
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
      <label htmlFor={id} className="block text-sm font-medium text-slate-700">
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
          onChange={(event) => {
            onChange(event.target.value);
          }}
        />
      )}
      {hint !== undefined && (
        <p id={hintId} className="mt-1 text-xs text-slate-500">
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
