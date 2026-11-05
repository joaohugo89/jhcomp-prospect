import { useState } from 'react';

import { actionErrorMessage } from '../../app/errors';
import { useLoad } from '../../app/useLoad';
import { input } from '../../components/styles';
import { assignLead } from '../../services/leadService';
import { HttpError } from '../../services/httpClient';
import { getTeam } from '../../services/teamService';
import type { Lead } from '../../types/lead';

/**
 * Responsável pelo lead. Qualquer membro atribui ou remove; o servidor exige
 * membro ativo da mesma organização. É o que o dashboard e a fila usam em "Meus".
 */
export function AssigneeField({
  lead,
  onChanged,
}: {
  readonly lead: Lead;
  readonly onChanged: (lead: Lead) => void;
}): React.JSX.Element {
  const [team] = useLoad('team', (signal) => getTeam(signal));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const members = team.kind === 'ready' ? team.data.content : [];
  // Responsável que deixou a organização continua visível até ser trocado.
  const unknown = lead.assignedUserId !== undefined && !members.some((member) => member.userId === lead.assignedUserId);

  async function change(value: string): Promise<void> {
    const next = value === '' ? null : value;
    if (next === (lead.assignedUserId ?? null)) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      onChanged(await assignLead(lead.id, next));
    } catch (cause: unknown) {
      // 404 aqui é, quase sempre, membro que deixou de estar ativo desde que a lista carregou.
      setError(
        cause instanceof HttpError && cause.type === 'RESOURCE_NOT_FOUND'
          ? 'A pessoa escolhida não é mais membro ativo. Recarregue a página.'
          : actionErrorMessage(cause),
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <label className="block text-sm font-medium text-stone-700">
        Responsável
        <select
          className={input}
          value={lead.assignedUserId ?? ''}
          disabled={busy || team.kind === 'loading'}
          onChange={(event) => {
            void change(event.target.value);
          }}
        >
          <option value="">Sem responsável</option>
          {unknown && lead.assignedUserId !== undefined && <option value={lead.assignedUserId}>Membro inativo</option>}
          {members.map((member) => (
            <option key={member.userId} value={member.userId}>
              {member.displayName}
            </option>
          ))}
        </select>
      </label>
      {team.kind === 'error' && (
        <p role="alert" className="mt-2 text-sm text-red-700">
          Não foi possível carregar a equipe.
        </p>
      )}
      {error !== null && (
        <p role="alert" className="mt-2 text-sm text-red-700">
          {error}
        </p>
      )}
    </div>
  );
}
