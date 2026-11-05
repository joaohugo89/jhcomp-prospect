import { Trash2 } from 'lucide-react';
import { useState } from 'react';

import { actionErrorMessage } from '../../app/errors';
import { formatDateTime } from '../../app/format';
import { navigate } from '../../app/router';
import { useLoad } from '../../app/useLoad';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { buttonSecondary, input } from '../../components/styles';
import { archiveCompany, deleteCompany, getRemovalEligibility } from '../../services/companyService';
import type { Company, RemovalEligibility } from '../../types/company';

/** Mesma comparação do servidor: sem caixa e sem espaço nas pontas. */
function sameName(typed: string, name: string): boolean {
  return typed.trim().toLowerCase() === name.trim().toLowerCase();
}

function blockedReason(eligibility: RemovalEligibility, company: Company): string {
  if (eligibility.blocker === 'HAS_ACTIVITY') {
    return `${company.name} já tem histórico comercial (lead que avançou de etapa, interação, tarefa, negócio, mensagem ou análise). Excluir apagaria esse histórico; por isso só é possível arquivar. Arquivada, ela sai da lista padrão e não aceita alterações, mas o histórico fica.`;
  }
  return 'Só quem cadastrou a empresa pode excluí-la, e apenas nos 7 dias seguintes ao cadastro. Depois disso, peça a um administrador ou arquive.';
}

/**
 * Excluir de verdade a empresa cadastrada por engano (D32). O botão está
 * sempre à vista: quando o servidor aceitaria (sem atividade comercial; quem
 * cadastrou em até 7 dias, ou o administrador), confirma digitando o nome;
 * quando não, explica o motivo e oferece arquivar.
 */
export function DeleteCompanyAction({
  company,
  onArchived,
}: {
  readonly company: Company;
  readonly onArchived: () => void;
}): React.JSX.Element | null {
  const [eligibility] = useLoad(`removal|${company.id}|${company.updatedAt}`, (signal) =>
    getRemovalEligibility(company.id, signal),
  );
  const [open, setOpen] = useState(false);
  const [typed, setTyped] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (eligibility.kind !== 'ready') {
    return null;
  }
  const allowed = eligibility.data.allowed;
  const until = eligibility.data.allowedUntil;

  async function run(action: () => Promise<void>, fallback: string): Promise<void> {
    setBusy(true);
    setError(null);
    try {
      await action();
    } catch (cause: unknown) {
      setError(actionErrorMessage(cause, fallback));
      setOpen(false);
      setBusy(false);
    }
  }

  const close = (): void => {
    setOpen(false);
  };

  return (
    <>
      <button
        type="button"
        className={`${buttonSecondary} gap-1.5 text-red-700`}
        onClick={() => {
          setTyped('');
          setOpen(true);
        }}
      >
        <Trash2 aria-hidden="true" className="size-4" />
        Excluir
      </button>
      {error !== null && (
        <p role="alert" className="w-full text-sm text-red-700">
          {error}
        </p>
      )}
      {open && allowed && (
        <ConfirmDialog
          title="Excluir empresa definitivamente?"
          description={`${company.name} e o lead aberto com ela serão apagados, sem como desfazer. Use para o que foi cadastrado por engano; bloqueios de contato continuam valendo.${
            until === undefined ? '' : ` Você pode excluir até ${formatDateTime(until)}.`
          }`}
          confirmLabel="Excluir"
          destructive
          busy={busy}
          confirmDisabled={!sameName(typed, company.name)}
          onConfirm={() => {
            void run(async () => {
              await deleteCompany(company.id, typed);
              navigate('/companies');
            }, 'A empresa recebeu atividade e não pode mais ser excluída. Arquive-a.');
          }}
          onCancel={close}
        >
          <label className="mt-4 block text-sm font-medium text-stone-700">
            {`Digite "${company.name}" para confirmar`}
            <input
              className={input}
              value={typed}
              autoComplete="off"
              onChange={(event) => {
                setTyped(event.target.value);
              }}
            />
          </label>
        </ConfirmDialog>
      )}
      {open && !allowed && (
        <ConfirmDialog
          title="Esta empresa não pode ser excluída"
          description={blockedReason(eligibility.data, company)}
          {...(company.archived
            ? {}
            : {
                confirmLabel: 'Arquivar em vez disso',
                onConfirm: () => {
                  void run(async () => {
                    await archiveCompany(company.id);
                    setOpen(false);
                    setBusy(false);
                    onArchived();
                  }, 'Não foi possível arquivar agora.');
                },
              })}
          cancelLabel="Fechar"
          busy={busy}
          onCancel={close}
        />
      )}
    </>
  );
}
