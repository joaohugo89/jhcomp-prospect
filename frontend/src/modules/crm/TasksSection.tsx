import { useState } from 'react';

import { actionErrorMessage, fieldErrorsOf } from '../../app/errors';
import { formatDateTime, toInstant } from '../../app/format';
import { useLoad } from '../../app/useLoad';
import { FormField } from '../../components/FormField';
import { LoadStateView } from '../../components/States';
import { buttonSecondary, card, input } from '../../components/styles';
import { createTask, listTasks, updateTask } from '../../services/crmService';
import type { Task } from '../../types/crm';

/** Tarefas do lead (RF-042): abertas primeiro, como o servidor ordena. */
export function TasksSection({
  leadId,
  onChanged,
}: {
  readonly leadId: string;
  /** Tarefa criada ou concluída: quem mostra a próxima tarefa (resumo do lead) relê. */
  readonly onChanged?: () => void;
}): React.JSX.Element {
  const [state, reload] = useLoad(leadId, (signal) => listTasks(leadId, signal));
  const [error, setError] = useState<string | null>(null);
  const [pendingId, setPendingId] = useState<string | null>(null);
  // Referência fixa para "atrasada": o relógio não é lido durante a renderização.
  const [now] = useState(() => Date.now());

  async function toggle(task: Task): Promise<void> {
    setPendingId(task.id);
    setError(null);
    try {
      await updateTask(task, task.status === 'OPEN' ? 'DONE' : 'OPEN');
      reload();
      onChanged?.();
    } catch (cause: unknown) {
      setError(actionErrorMessage(cause));
    } finally {
      setPendingId(null);
    }
  }

  return (
    <section aria-labelledby="tarefas" className={`${card} @container p-5`}>
      <h2 id="tarefas" className="text-sm font-semibold text-slate-900">
        Tarefas
      </h2>
      <NewTaskForm
        leadId={leadId}
        onCreated={() => {
          reload();
          onChanged?.();
        }}
      />
      {state.kind !== 'ready' ? (
        <LoadStateView state={state} onRetry={reload} />
      ) : state.data.content.length === 0 ? (
        <p className="mt-3 text-sm text-slate-600">Nenhuma tarefa para este lead.</p>
      ) : (
        <ul className="mt-3 divide-y divide-slate-100">
          {state.data.content.map((task) => (
            <li key={task.id} className="flex items-start gap-2 py-2 text-sm">
              <input
                type="checkbox"
                className="mt-1"
                checked={task.status === 'DONE'}
                disabled={pendingId === task.id}
                aria-label={`Concluída: ${task.title}`}
                onChange={() => {
                  void toggle(task);
                }}
              />
              <div className="min-w-0">
                <p className={task.status === 'DONE' ? 'text-slate-500 line-through' : 'text-slate-900'}>
                  {task.title}
                </p>
                <TaskDue task={task} now={now} />
              </div>
            </li>
          ))}
        </ul>
      )}
      {error !== null && (
        <p role="alert" className="mt-2 text-sm text-red-700">
          {error}
        </p>
      )}
    </section>
  );
}

function TaskDue({ task, now }: { readonly task: Task; readonly now: number }): React.JSX.Element | null {
  if (task.status === 'DONE') {
    return task.completedAt === undefined ? null : (
      <p className="text-xs text-slate-500">{`Concluída em ${formatDateTime(task.completedAt)}`}</p>
    );
  }
  if (task.dueAt === undefined) {
    return null;
  }
  // Texto, e não só cor, indica o atraso (doc 08 §12).
  const overdue = new Date(task.dueAt).getTime() < now;
  return (
    <p className={`text-xs ${overdue ? 'font-medium text-red-700' : 'text-slate-500'}`}>
      {`${overdue ? 'Atrasada · ' : ''}Prazo ${formatDateTime(task.dueAt)}`}
    </p>
  );
}

function NewTaskForm({
  leadId,
  onCreated,
}: {
  readonly leadId: string;
  readonly onCreated: () => void;
}): React.JSX.Element {
  const [title, setTitle] = useState('');
  const [dueAt, setDueAt] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<ReadonlyMap<string, string>>(new Map());

  async function submit(): Promise<void> {
    setBusy(true);
    setError(null);
    setFieldErrors(new Map());
    try {
      await createTask(leadId, title.trim(), toInstant(dueAt));
      setTitle('');
      setDueAt('');
      onCreated();
    } catch (cause: unknown) {
      setFieldErrors(fieldErrorsOf(cause));
      setError(actionErrorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form
      // Responde à largura do cartão (lateral estreita do lead), não à da tela.
      className="mt-3 grid gap-3 @md:grid-cols-[2fr_1fr_auto] @md:items-end"
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      <FormField
        label="Nova tarefa"
        value={title}
        onChange={setTitle}
        required
        maxLength={200}
        error={fieldErrors.get('title')}
      />
      <label className="text-sm font-medium text-slate-700">
        Prazo (opcional)
        <input
          type="datetime-local"
          className={input}
          value={dueAt}
          onChange={(event) => {
            setDueAt(event.target.value);
          }}
        />
      </label>
      <button type="submit" className={buttonSecondary} disabled={busy || title.trim() === ''}>
        {busy ? 'Adicionando…' : 'Adicionar'}
      </button>
      {error !== null && (
        <p role="alert" className="text-sm text-red-700 sm:col-span-3">
          {error}
        </p>
      )}
    </form>
  );
}
