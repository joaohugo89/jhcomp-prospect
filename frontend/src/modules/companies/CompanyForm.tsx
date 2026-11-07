import { useState } from 'react';

import { actionErrorMessage, fieldErrorsOf } from '../../app/errors';
import { useLoad } from '../../app/useLoad';
import { FormField } from '../../components/FormField';
import { buttonPrimary, buttonSecondary } from '../../components/styles';
import { getSegments } from '../../services/worklistService';
import type { Company, CompanyInput, SourceInput } from '../../types/company';
import { DuplicateWarning } from './DuplicateWarning';

export const EMPTY_COMPANY: CompanyInput = {
  name: '',
  tradeName: '',
  segment: '',
  city: '',
  state: '',
  websiteUrl: '',
  instagramUrl: '',
  phone: '',
  businessEmail: '',
  address: '',
  notes: '',
};

const EMPTY_SOURCE: SourceInput = { name: '', reference: '', url: '' };

export function toInput(company: Company): CompanyInput {
  return {
    name: company.name,
    tradeName: company.tradeName ?? '',
    segment: company.segment ?? '',
    city: company.city,
    state: company.state,
    websiteUrl: company.websiteUrl ?? '',
    instagramUrl: company.instagramUrl ?? '',
    phone: company.phone ?? '',
    businessEmail: company.businessEmail ?? '',
    address: company.address ?? '',
    notes: company.notes ?? '',
  };
}

interface CompanyFormProps {
  readonly initial: CompanyInput;
  /** The origin is only asked on creation: it describes where the company came from (RF-020). */
  readonly withSource: boolean;
  readonly submitLabel: string;
  readonly onSubmit: (input: CompanyInput, source: SourceInput) => Promise<void>;
  readonly onCancel: () => void;
  /** When editing, the company itself does not count as a duplicate. */
  readonly companyId?: string;
}

/** The company form (RF-010), used both to create and to edit. */
export function CompanyForm({
  initial,
  withSource,
  submitLabel,
  onSubmit,
  onCancel,
  companyId,
}: CompanyFormProps): React.JSX.Element {
  // Segments already in use become suggestions, which stops "Barbearia" and "Barbearias" becoming two segments.
  const [segments] = useLoad('segments', (signal) => getSegments(signal));
  const [values, setValues] = useState<CompanyInput>(initial);
  const [source, setSource] = useState<SourceInput>(EMPTY_SOURCE);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [errors, setErrors] = useState<ReadonlyMap<string, string>>(new Map());

  function field(name: keyof CompanyInput): {
    value: string;
    onChange: (value: string) => void;
    error: string | undefined;
  } {
    return {
      value: values[name],
      onChange: (value) => {
        setValues((current) => ({ ...current, [name]: value }));
      },
      error: errors.get(name),
    };
  }

  function sourceField(name: keyof SourceInput): {
    value: string;
    onChange: (value: string) => void;
    error: string | undefined;
  } {
    return {
      value: source[name],
      onChange: (value) => {
        setSource((current) => ({ ...current, [name]: value }));
      },
      error: errors.get(`source.${name}`),
    };
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    setSubmitting(true);
    setFormError(null);
    setErrors(new Map());
    try {
      await onSubmit(values, source);
    } catch (cause: unknown) {
      setErrors(fieldErrorsOf(cause));
      setFormError(actionErrorMessage(cause, 'A empresa está arquivada e não aceita alterações.'));
      setSubmitting(false);
    }
  }

  return (
    <form
      noValidate
      onSubmit={(event) => {
        void handleSubmit(event);
      }}
      className="space-y-4 rounded-2xl border border-stone-200/70 glass p-4 shadow-sm"
    >
      <fieldset className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
        <legend className="sr-only">Dados da empresa</legend>
        <FormField label="Nome" required maxLength={200} {...field('name')} />
        <FormField label="Nome comercial" maxLength={200} {...field('tradeName')} />
        <FormField
          label="Segmento"
          maxLength={80}
          hint="Escolha um segmento já usado quando servir: grafias diferentes viram segmentos diferentes no filtro."
          suggestions={segments.kind === 'ready' ? segments.data.segments.map((item) => item.name) : []}
          {...field('segment')}
        />
        <div className="grid grid-cols-[1fr_6rem] gap-3">
          <FormField label="Cidade" required maxLength={120} {...field('city')} />
          <FormField label="UF" required maxLength={2} {...field('state')} />
        </div>
        <FormField label="Site" type="url" maxLength={500} {...field('websiteUrl')} />
        <FormField label="Instagram" type="url" maxLength={500} {...field('instagramUrl')} />
        <FormField label="Telefone comercial" type="tel" hint="Com DDD." {...field('phone')} />
        <FormField label="E-mail comercial" type="email" maxLength={254} {...field('businessEmail')} />
        <FormField label="Endereço comercial" maxLength={300} {...field('address')} />
        <FormField label="Observações" multiline maxLength={2000} {...field('notes')} />
      </fieldset>

      {withSource && (
        <fieldset className="grid gap-3 border-t border-stone-200 pt-3 md:grid-cols-3">
          <legend className="text-sm font-medium text-stone-800">Origem (opcional)</legend>
          <p className="text-xs text-stone-500 md:col-span-3">
            De onde veio esta empresa. Sem preencher, fica registrado como cadastro manual de hoje.
          </p>
          <FormField label="Fonte" maxLength={120} {...sourceField('name')} />
          <FormField label="Referência" maxLength={300} {...sourceField('reference')} />
          <FormField label="URL da fonte" type="url" maxLength={1000} {...sourceField('url')} />
        </fieldset>
      )}

      <div aria-live="polite">
        <DuplicateWarning values={values} {...(companyId === undefined ? {} : { excludeCompanyId: companyId })} />
      </div>

      <div aria-live="polite">
        {formError !== null && (
          <p role="alert" className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-800">
            {formError}
          </p>
        )}
      </div>

      <div className="flex justify-end gap-2">
        <button type="button" className={buttonSecondary} onClick={onCancel} disabled={submitting}>
          Cancelar
        </button>
        <button type="submit" className={buttonPrimary} disabled={submitting}>
          {submitting ? 'Salvando…' : submitLabel}
        </button>
      </div>
    </form>
  );
}
