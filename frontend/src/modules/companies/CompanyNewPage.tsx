import { navigate } from '../../app/router';
import { PageHeader } from '../../components/PageHeader';
import { createCompany } from '../../services/companyService';
import { CompanyForm, EMPTY_COMPANY } from './CompanyForm';

/** Cadastro de empresa (RF-010, RF-020). */
export function CompanyNewPage(): React.JSX.Element {
  return (
    <>
      <PageHeader title="Nova empresa" description="Campos com * são obrigatórios." />
      <CompanyForm
        initial={EMPTY_COMPANY}
        withSource
        submitLabel="Cadastrar"
        onSubmit={async (input, source) => {
          const company = await createCompany(input, source);
          // Abre o detalhe, onde aparecem as possíveis duplicatas (RF-014).
          navigate(`/companies/${company.id}`);
        }}
        onCancel={() => {
          navigate('/companies');
        }}
      />
    </>
  );
}
