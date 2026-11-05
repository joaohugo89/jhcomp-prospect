import { ExternalLink, Globe, Mail, Phone } from 'lucide-react';

import { safeHref } from '../../app/format';
import { useLoad } from '../../app/useLoad';
import { getCompany } from '../../services/companyService';

/** Contato comercial da empresa. Abrir telefone ou e-mail não é envio (regra 5). */
export function CompanyContact({ companyId }: { readonly companyId: string }): React.JSX.Element | null {
  const [company] = useLoad(companyId, (signal) => getCompany(companyId, signal));
  if (company.kind !== 'ready') {
    return null;
  }
  const { phone, businessEmail, websiteUrl } = company.data;
  const site = safeHref(websiteUrl);
  if (phone === undefined && businessEmail === undefined && site === undefined) {
    return <p className="mt-5 text-xs text-stone-500">Empresa sem telefone, e-mail ou site cadastrado.</p>;
  }
  return (
    <section aria-labelledby="previa-contato" className="mt-5">
      <h3 id="previa-contato" className="text-[11px] font-semibold tracking-wider text-stone-500 font-mono uppercase">
        Contato da empresa
      </h3>
      <ul className="mt-2 space-y-1.5 text-sm">
        {phone !== undefined && (
          <ContactLine
            icon={<Phone className="size-3.5" />}
            href={`tel:${phone.replace(/[^\d+]/g, '')}`}
            text={phone}
          />
        )}
        {businessEmail !== undefined && (
          <ContactLine icon={<Mail className="size-3.5" />} href={`mailto:${businessEmail}`} text={businessEmail} />
        )}
        {site !== undefined && websiteUrl !== undefined && (
          <ContactLine icon={<Globe className="size-3.5" />} href={site} text={websiteUrl} external />
        )}
      </ul>
    </section>
  );
}

function ContactLine({
  icon,
  href,
  text,
  external = false,
}: {
  readonly icon: React.ReactNode;
  readonly href: string;
  readonly text: string;
  readonly external?: boolean;
}): React.JSX.Element {
  return (
    <li className="flex min-w-0 items-center gap-2 text-stone-700">
      <span aria-hidden="true" className="text-stone-400">
        {icon}
      </span>
      <a
        href={href}
        className="truncate hover:text-brand-700 hover:underline focus-visible:outline-2 focus-visible:outline-brand-500"
        {...(external ? { target: '_blank', rel: 'noopener noreferrer' } : {})}
      >
        {text}
      </a>
      {external && <ExternalLink aria-hidden="true" className="size-3 shrink-0 text-stone-400" />}
    </li>
  );
}
