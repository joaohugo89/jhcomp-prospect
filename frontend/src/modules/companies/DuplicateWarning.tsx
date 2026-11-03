import { Copy } from 'lucide-react';
import { useEffect, useState } from 'react';

import { DUPLICATE_SIGNAL_LABEL } from '../../app/labels';
import { useLoad } from '../../app/useLoad';
import { link } from '../../components/styles';
import { checkDuplicates } from '../../services/companyService';
import type { CompanyInput, DuplicateCandidate, DuplicateProbe } from '../../types/company';

/** Espera a pessoa parar de digitar antes de perguntar ao servidor. */
const DEBOUNCE_MS = 400;

/** Só pergunta quando há sinal possível: telefone, site, ou nome com cidade e UF. */
function probeOf(values: CompanyInput, excludeCompanyId: string | undefined): DuplicateProbe | null {
  const phoneDigits = values.phone.replace(/\D/g, '');
  const hasName = values.name.trim().length >= 3 && values.city.trim() !== '' && values.state.trim().length === 2;
  if (phoneDigits.length < 8 && values.websiteUrl.trim() === '' && !hasName) {
    return null;
  }
  return {
    name: values.name.trim(),
    city: values.city.trim(),
    state: values.state.trim(),
    websiteUrl: values.websiteUrl.trim(),
    phone: values.phone.trim(),
    ...(excludeCompanyId === undefined ? {} : { excludeCompanyId }),
  };
}

function useDebounced<T>(value: T, delay: number): T {
  const [settled, setSettled] = useState(value);
  useEffect(() => {
    const timer = window.setTimeout(() => {
      setSettled(value);
    }, delay);
    return () => {
      window.clearTimeout(timer);
    };
  }, [value, delay]);
  return settled;
}

/**
 * Aviso de possível duplicata enquanto o cadastro é digitado (RF-014). Só
 * sugere: não impede salvar, porque mesmo telefone pode ser outra filial.
 */
export function DuplicateWarning({
  values,
  excludeCompanyId,
}: {
  readonly values: CompanyInput;
  /** A própria empresa, na edição. */
  readonly excludeCompanyId?: string;
}): React.JSX.Element | null {
  const probe = probeOf(values, excludeCompanyId);
  const current = probe === null ? '' : JSON.stringify(probe);
  // Quando a chave assenta, o formulário parou de mudar: o probe desta renderização é o dela.
  const settled = useDebounced(current, DEBOUNCE_MS);
  const [state] = useLoad(`duplicatas|${settled}`, (signal): Promise<readonly DuplicateCandidate[]> =>
    probe === null || settled !== current ? Promise.resolve([]) : checkDuplicates(probe, signal),
  );

  if (state.kind !== 'ready' || state.data.length === 0) {
    return null;
  }
  return (
    <div role="status" className="rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-950 ring-1 ring-amber-200">
      <p className="flex items-center gap-1.5 font-semibold">
        <Copy aria-hidden="true" className="size-4" />
        {state.data.length === 1 ? 'Possível duplicata' : 'Possíveis duplicatas'}
      </p>
      <ul className="mt-1.5 space-y-1">
        {state.data.map((candidate) => (
          <li key={candidate.companyId}>
            <a href={`/companies/${candidate.companyId}`} target="_blank" rel="noopener noreferrer" className={link}>
              {candidate.name}
            </a>
            {` · ${candidate.city}/${candidate.state}${candidate.archived ? ' · arquivada' : ''} · ${candidate.signals
              .map((signal) => DUPLICATE_SIGNAL_LABEL[signal].toLowerCase())
              .join(', ')}`}
          </li>
        ))}
      </ul>
      <p className="mt-1.5 text-xs text-amber-900">
        Confira antes de salvar. Se for a mesma empresa, abra a existente em vez de cadastrar de novo.
      </p>
    </div>
  );
}
