import { Link } from '../../../app/router';
import { link } from '../../../components/styles';
import type { LeadStatus } from '../../../types/lead';
import type { StatusLead } from '../model/dashboardData';
import { DashboardSection } from './DashboardSection';

/**
 * Cor por etapa. As etapas em aberto seguem uma rampa de azul, do claro ao
 * escuro na ordem do funil: são ordenadas, não categorias soltas. Encerradas e
 * bloqueadas saem da rampa. A identidade vem sempre do nome ao lado.
 */
const STAGE_COLOR: Readonly<Record<LeadStatus, string>> = {
  NEW: 'bg-sky-300',
  QUALIFIED: 'bg-sky-400',
  CONTACT_APPROVED: 'bg-sky-500',
  CONTACTED: 'bg-brand-500',
  IN_CONVERSATION: 'bg-brand-600',
  PROPOSAL_SENT: 'bg-brand-700',
  WON: 'bg-emerald-600',
  LOST: 'bg-slate-400',
  DO_NOT_CONTACT: 'bg-red-500',
};

/**
 * Funil: leads por etapa, situação de agora. Barra segmentada com o todo,
 * cartões das etapas com lead (contagem e participação) e as vazias em pílulas.
 */
export function StatusProgressList({
  itens,
  filtrado,
}: {
  readonly itens: readonly StatusLead[];
  /** Com segmento, cidade ou origem escolhidos, "vazio" não quer dizer "sem leads". */
  readonly filtrado: boolean;
}): React.JSX.Element {
  const total = itens.reduce((soma, item) => soma + item.quantidade, 0);
  const comLead = itens.filter((item) => item.quantidade > 0);
  const vazias = itens.filter((item) => item.quantidade === 0);

  return (
    <DashboardSection
      id="por-status"
      title="Funil · status dos leads"
      description="Situação de agora, em todas as etapas"
      actions={
        <span className="rounded-lg bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-700 tabular-nums">
          {`${total} ${total === 1 ? 'lead' : 'leads'}`}
        </span>
      }
    >
      {total === 0 ? (
        <p className="mt-4 text-sm text-slate-600">
          {filtrado ? 'Nenhum lead com o segmento, a cidade ou a origem escolhidos.' : 'Nenhum lead ainda. '}
          {!filtrado && (
            <Link to="/companies" className={link}>
              Cadastre ou importe empresas
            </Link>
          )}
        </p>
      ) : (
        <>
          {/* Barra segmentada fina: partes com 2px de folga, sem texto; o detalhe está nos cartões. */}
          <div aria-hidden="true" className="mt-5 flex h-2 gap-0.5 overflow-hidden rounded-full">
            {comLead.map((item) => (
              <div
                key={item.status}
                className={`h-full first:rounded-l-full last:rounded-r-full ${STAGE_COLOR[item.status]}`}
                style={{ width: `${(item.quantidade / total) * 100}%` }}
              />
            ))}
          </div>

          <dl className="mt-4 grid grid-cols-2 gap-2">
            {comLead.map((item) => (
              <div
                key={item.status}
                className="flex h-11 items-center justify-between gap-2 rounded-xl bg-slate-50 px-3 ring-1 ring-slate-200/70"
              >
                <dt className="flex min-w-0 items-center gap-2 text-xs font-semibold text-slate-700">
                  <span aria-hidden="true" className={`size-2 shrink-0 rounded-full ${STAGE_COLOR[item.status]}`} />
                  <span className="truncate">{item.nome}</span>
                </dt>
                <dd className="shrink-0 text-xs font-bold text-slate-900 tabular-nums">
                  {item.quantidade}
                  <span className="ml-1 font-normal text-slate-400">{`(${Math.round((item.quantidade / total) * 100)}%)`}</span>
                </dd>
              </div>
            ))}
          </dl>

          {vazias.length > 0 && (
            <div className="mt-4 border-t border-slate-100 pt-3">
              <p className="sr-only">Etapas sem lead</p>
              <ul className="flex flex-wrap gap-1.5">
                {vazias.map((item) => (
                  <li
                    key={item.status}
                    className="rounded-md bg-slate-50 px-2 py-0.5 text-[11px] text-slate-500 ring-1 ring-slate-200/70"
                  >
                    {`${item.nome}: 0`}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </>
      )}
    </DashboardSection>
  );
}
