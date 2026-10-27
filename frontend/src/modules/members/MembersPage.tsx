import { useState } from 'react';

import { formatDateTime } from '../../app/format';
import { useLoad } from '../../app/useLoad';
import { DataTable, type Column } from '../../components/DataTable';
import { PageHeader } from '../../components/PageHeader';
import { Pagination } from '../../components/Pagination';
import { LoadStateView } from '../../components/States';
import { fetchMembers } from '../../services/memberService';
import type { Member } from '../../types/member';

const COLUMNS: readonly Column<Member>[] = [
  { header: 'Nome', render: (member) => member.displayName },
  { header: 'E-mail', render: (member) => <span className="break-all">{member.email}</span> },
  { header: 'Papel', render: (member) => (member.role === 'ADMIN' ? 'Administrador' : 'Operador') },
  { header: 'Desde', render: (member) => formatDateTime(member.createdAt), hideOnSmall: true },
];

/**
 * Membros da organização (RF-003). Só administradores chegam aqui pelo menu;
 * quem digitar o endereço sem permissão vê o estado de acesso negado.
 */
export function MembersPage(): React.JSX.Element {
  const [page, setPage] = useState(0);
  const [state, reload] = useLoad(String(page), (signal) => fetchMembers(page, signal));

  return (
    <>
      <PageHeader title="Membros" description="Pessoas com acesso ativo a esta organização." />
      {state.kind === 'ready' ? (
        <>
          <DataTable caption="Membros" columns={COLUMNS} rows={state.data.content} rowKey={(row) => row.memberId} />
          <Pagination page={state.data} onChange={setPage} />
        </>
      ) : (
        <LoadStateView state={state} onRetry={reload} />
      )}
    </>
  );
}
