import { useState } from 'react';

import { buttonPrimary, buttonSecondary } from '../../components/styles';
import type { MemberWithPassword } from '../../types/member';

/**
 * A freshly generated temporary password (D28). It appears exactly once: the
 * server does not keep it in clear text and there is no way to read it
 * again.
 */
export function TemporaryPasswordNotice({
  issued,
  onDismiss,
}: {
  readonly issued: MemberWithPassword;
  readonly onDismiss: () => void;
}): React.JSX.Element {
  const [copied, setCopied] = useState(false);
  const { member, temporaryPassword } = issued;

  async function copy(): Promise<void> {
    try {
      await navigator.clipboard.writeText(temporaryPassword);
      setCopied(true);
    } catch {
      // No clipboard permission: the password stays visible to be copied by hand.
      setCopied(false);
    }
  }

  return (
    <section
      role="status"
      aria-labelledby="senha-temporaria"
      className="rounded-2xl border border-amber-300 bg-amber-50 p-4 text-sm text-amber-950"
    >
      <h2 id="senha-temporaria" className="text-base font-semibold">
        {`Senha temporária de ${member.displayName}`}
      </h2>
      <p className="mt-2">
        {`Entregue a senha abaixo para ${member.email} por um canal seguro, como pessoalmente ou por mensagem direta. Ela não será mostrada de novo. No primeiro acesso, será preciso trocá-la.`}
      </p>
      <p className="mt-3 select-all rounded-md glass px-3 py-2 font-mono text-base tracking-wide text-stone-900">
        {temporaryPassword}
      </p>
      <div className="mt-3 flex flex-wrap gap-2">
        <button
          type="button"
          className={buttonSecondary}
          onClick={() => {
            void copy();
          }}
        >
          {copied ? 'Copiada' : 'Copiar senha'}
        </button>
        <button type="button" className={buttonPrimary} onClick={onDismiss}>
          Já entreguei a senha
        </button>
      </div>
    </section>
  );
}
