import { useState } from 'react';

import { actionErrorMessage } from '../../app/errors';
import { useLoad } from '../../app/useLoad';
import { input } from '../../components/styles';
import { assignLead } from '../../services/leadService';
import { HttpError } from '../../services/httpClient';
import { getTeam } from '../../services/teamService';
import type { Lead } from '../../types/lead';

/**
 * The lead's owner. Any member can assign or clear it; the server requires an
 * active member of the same organisation. It is what the dashboard and the
 * queue use for "mine".
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
  // An owner who has left the organisation stays visible until replaced.
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
      // A 404 here almost always means a member stopped being active since the list loaded.
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
