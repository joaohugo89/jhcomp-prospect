package com.jhcomp.prospect.company.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhcomp.prospect.company.domain.DuplicateSignal;
import com.jhcomp.prospect.company.domain.SourceType;
import com.jhcomp.prospect.company.infrastructure.persistence.CompanyEntity;
import com.jhcomp.prospect.company.infrastructure.persistence.CompanyEntity.CompanyData;
import com.jhcomp.prospect.company.infrastructure.persistence.CompanyRepository;
import com.jhcomp.prospect.company.infrastructure.persistence.CompanySpecifications;
import com.jhcomp.prospect.company.infrastructure.persistence.LeadSourceEntity;
import com.jhcomp.prospect.company.infrastructure.persistence.LeadSourceEntity.SourceData;
import com.jhcomp.prospect.company.infrastructure.persistence.LeadSourceRepository;
import com.jhcomp.prospect.shared.error.ConflictException;
import com.jhcomp.prospect.shared.error.InvalidRequestException;
import com.jhcomp.prospect.shared.error.ResourceNotFoundException;
import com.jhcomp.prospect.shared.security.AuthenticatedUser;
import com.jhcomp.prospect.shared.normalization.ContactNormalizer;
import com.jhcomp.prospect.shared.security.OrganizationContext;
import com.jhcomp.prospect.shared.text.Texts;

/**
 * Casos de uso de empresa (RF-010, RF-014, RF-020, RF-022).
 *
 * <p>A organizacao vem sempre do {@link OrganizationContext}; nenhum metodo
 * publico a recebe como parametro (RNF-001).
 */
@Service
public class CompanyService {

    /** Espaco repetido no meio do segmento vira um so: "Barbearia  Centro" e "Barbearia Centro". */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** Mais recentes primeiro; desempate por id para a paginacao ser estavel. */
    public static final Sort DEFAULT_SORT =
            Sort.by(Sort.Direction.DESC, CompanyEntity::getCreatedAt).and(Sort.by(CompanyEntity::getId));

    /** Teto de um lote, o mesmo da fila de leads. */
    public static final int MAX_BULK = 100;

    /** Id que nenhuma empresa tem: o "excluir a propria" quando ainda nao ha propria. */
    private static final UUID NO_COMPANY = new UUID(0L, 0L);

    /** Origem atribuida quando o operador cadastra sem informar uma. */
    static final String DEFAULT_SOURCE_NAME = "Cadastro manual";

    private final CompanyRepository companyRepository;
    private final LeadSourceRepository sourceRepository;
    private final OrganizationContext organizationContext;
    private final Clock clock;

    CompanyService(
            CompanyRepository companyRepository,
            LeadSourceRepository sourceRepository,
            OrganizationContext organizationContext,
            Clock clock) {
        this.companyRepository = companyRepository;
        this.sourceRepository = sourceRepository;
        this.organizationContext = organizationContext;
        this.clock = clock;
    }

    /**
     * Cadastra a empresa e registra de onde ela veio (RF-020).
     *
     * <p>Empresa e origem sao gravadas na mesma transacao: empresa sem
     * proveniencia violaria o RF-022.
     */
    @Transactional
    public CompanyEntity create(CompanyData data, SourceData source) {
        AuthenticatedUser user = organizationContext.requireCurrentUser();
        Instant now = clock.instant();

        CompanyEntity company = companyRepository.save(new CompanyEntity(
                UUID.randomUUID(), user.organizationId(), user.userId(), withCanonicalSegment(user.organizationId(), data), now));
        saveSource(company, null, source, user, now);
        return company;
    }

    @Transactional(readOnly = true)
    public CompanyEntity find(UUID companyId) {
        return companyRepository
                .findByIdAndOrganizationId(companyId, organizationContext.requireOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Company"));
    }

    @Transactional(readOnly = true)
    public Page<CompanyEntity> list(CompanySpecifications.Criteria criteria, Pageable pageable) {
        return companyRepository.findAll(
                CompanySpecifications.matching(organizationContext.requireOrganizationId(), criteria), pageable);
    }

    /** Atualizacao parcial: campo nulo mantem o valor, texto vazio limpa. */
    @Transactional
    public CompanyEntity update(UUID companyId, CompanyPatch patch) {
        CompanyEntity company = requireActive(companyId);
        company.update(withCanonicalSegment(company.getOrganizationId(), patch.applyTo(company)), clock.instant());
        return company;
    }

    /** Arquivar nao e excluir (RF-010). Repetir a operacao nao tem efeito. */
    @Transactional
    public CompanyEntity archive(UUID companyId) {
        CompanyEntity company = find(companyId);
        company.archive(clock.instant());
        return company;
    }

    @Transactional(readOnly = true)
    public Page<LeadSourceEntity> sources(UUID companyId, Pageable pageable) {
        CompanyEntity company = find(companyId);
        return sourceRepository.findByOrganizationIdAndCompanyIdOrderByCollectedAtDescIdAsc(
                company.getOrganizationId(), company.getId(), pageable);
    }

    /**
     * Empresas da mesma organizacao que podem ser a mesma (RF-014).
     *
     * <p>So sugere: nada e mesclado nem descartado aqui. Nome parecido nao
     * entra como sinal; so nome normalizado identico na mesma cidade e estado,
     * e mesmo esse e o sinal mais fraco (doc 06 §4).
     */
    @Transactional(readOnly = true)
    public List<DuplicateCandidate> duplicates(UUID companyId) {
        CompanyEntity company = find(companyId);
        return findDuplicates(company.getOrganizationId(), company.getNormalizedDomain(), company.getNormalizedPhone(),
                company.getNormalizedName(), company.getCity(), company.getState(), company.getId());
    }

    /**
     * Possiveis duplicatas de uma empresa ainda sendo digitada, com os mesmos
     * sinais de {@link #duplicates}. {@code excludedId} e a propria empresa, na
     * edicao. Sem nome, cidade e estado so os sinais de contato valem.
     */
    @Transactional(readOnly = true)
    public List<DuplicateCandidate> probeDuplicates(DuplicateProbe probe) {
        UUID organizationId = organizationContext.requireOrganizationId();
        String city = Texts.blankToNull(probe.city());
        String state = Texts.blankToNull(probe.state());
        return findDuplicates(
                organizationId,
                ContactNormalizer.domain(probe.websiteUrl()).orElse(null),
                ContactNormalizer.phone(probe.phone()).orElse(null),
                city == null || state == null ? null : ContactNormalizer.name(probe.name()).orElse(null),
                city == null ? null : city.strip(),
                state == null ? null : state.strip().toUpperCase(Locale.ROOT),
                probe.excludedId() == null ? NO_COMPANY : probe.excludedId());
    }

    /** O que o formulario ja tem para comparar. Nulos nao contam como sinal. */
    public record DuplicateProbe(
            String name, String city, String state, String websiteUrl, String phone, UUID excludedId) {}

    private List<DuplicateCandidate> findDuplicates(
            UUID organizationId,
            String normalizedDomain,
            String normalizedPhone,
            String normalizedName,
            String city,
            String state,
            UUID excludedId) {
        Map<UUID, DuplicateCandidate> candidates = new LinkedHashMap<>();
        if (normalizedDomain != null) {
            collect(candidates, DuplicateSignal.DOMAIN, companyRepository
                    .findTop10ByOrganizationIdAndNormalizedDomainAndIdNot(organizationId, normalizedDomain, excludedId));
        }
        if (normalizedPhone != null) {
            collect(candidates, DuplicateSignal.PHONE, companyRepository
                    .findTop10ByOrganizationIdAndNormalizedPhoneAndIdNot(organizationId, normalizedPhone, excludedId));
        }
        if (normalizedName != null && city != null && state != null) {
            collect(candidates, DuplicateSignal.NAME_CITY_STATE, companyRepository
                    .findTop10ByOrganizationIdAndNormalizedNameAndCityIgnoreCaseAndStateAndIdNot(
                            organizationId, normalizedName, city, state, excludedId));
        }
        return List.copyOf(candidates.values());
    }

    /**
     * Mesmo segmento, ou mesma cidade e estado, em varias empresas de uma vez
     * (qualidade de dado em lote). Tudo numa transacao; empresa arquivada nao
     * muda e e contada a parte. Id de outra organizacao responde 404 para o
     * lote inteiro, como se nao existisse.
     *
     * @return quantas mudaram e quantas foram puladas por estarem arquivadas
     */
    @Transactional
    public BulkResult bulkUpdate(List<UUID> companyIds, String segment, String city, String state) {
        List<UUID> distinct = companyIds.stream().distinct().toList();
        if (distinct.isEmpty() || distinct.size() > MAX_BULK) {
            throw new InvalidRequestException("companyIds must have between 1 and " + MAX_BULK + " items");
        }
        if (segment == null && city == null) {
            throw new InvalidRequestException("segment or city is required");
        }
        if ((city == null) != (state == null)) {
            throw new InvalidRequestException("city and state go together");
        }
        List<CompanyEntity> companies = distinct.stream().map(this::find).toList();
        int updated = 0;
        for (CompanyEntity company : companies) {
            if (company.isArchived()) {
                continue;
            }
            CompanyPatch patch = new CompanyPatch(
                    null, null, segment, city, state, null, null, null, null, null, null);
            update(company.getId(), patch);
            updated++;
        }
        return new BulkResult(updated, companies.size() - updated);
    }

    public record BulkResult(int updated, int skippedArchived) {}

    /**
     * Empresa da organizacao ativa que aceita alteracao.
     *
     * <p>Usado tambem pelo modulo de lead, que nao pode abrir oportunidade
     * para empresa arquivada.
     */
    @Transactional(readOnly = true)
    public CompanyEntity requireActive(UUID companyId) {
        CompanyEntity company = find(companyId);
        if (company.isArchived()) {
            throw new ConflictException("Company is archived");
        }
        return company;
    }

    /**
     * Empresas da organizacao ativa com algum dos valores normalizados
     * informados, inclusive arquivadas: uma empresa arquivada continua sendo a
     * mesma empresa.
     *
     * <p>Usado pela importacao para classificar o arquivo inteiro com tres
     * consultas, em vez de tres por linha.
     */
    @Transactional(readOnly = true)
    public List<CompanyEntity> findByNormalizedValues(
            Collection<String> domains, Collection<String> phones, Collection<String> names) {
        UUID organizationId = organizationContext.requireOrganizationId();
        Map<UUID, CompanyEntity> found = new LinkedHashMap<>();
        if (!domains.isEmpty()) {
            addAll(found, companyRepository.findByOrganizationIdAndNormalizedDomainIn(organizationId, domains));
        }
        if (!phones.isEmpty()) {
            addAll(found, companyRepository.findByOrganizationIdAndNormalizedPhoneIn(organizationId, phones));
        }
        if (!names.isEmpty()) {
            addAll(found, companyRepository.findByOrganizationIdAndNormalizedNameIn(organizationId, names));
        }
        return List.copyOf(found.values());
    }

    /**
     * Completa uma empresa existente com dados importados (D23).
     *
     * <p>So preenche campos vazios; registra a origem mesmo sem preencher nada,
     * porque a linha importada tambem confirma a empresa (RF-022). Empresa
     * arquivada nao e alterada.
     *
     * @return campos preenchidos, ou vazio se a empresa estiver arquivada
     */
    @Transactional
    public Optional<List<String>> completeFromImport(UUID companyId, CompanyData data, SourceData source) {
        AuthenticatedUser user = organizationContext.requireCurrentUser();
        CompanyEntity company = find(companyId);
        if (company.isArchived()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        List<String> filled = company.fillBlanks(withCanonicalSegment(company.getOrganizationId(), data), now);
        saveSource(company, null, source, user, now);
        return Optional.of(filled);
    }

    /**
     * Segmento com a grafia que ja existe na organizacao (D31): "barbearia "
     * vira "Barbearia" se esta ja e usada. Sem espaco nas pontas nem repetido;
     * em branco e o mesmo que sem segmento. Um segmento novo fica como foi
     * digitado.
     */
    private CompanyData withCanonicalSegment(UUID organizationId, CompanyData data) {
        String cleaned = Texts.blankToNull(data.segment());
        if (cleaned != null) {
            cleaned = WHITESPACE.matcher(cleaned.strip()).replaceAll(" ");
        }
        String segment = cleaned == null
                ? null
                : companyRepository.findExistingSegment(organizationId, cleaned).orElse(cleaned);
        if (Objects.equals(segment, data.segment())) {
            return data;
        }
        return new CompanyData(
                data.name(),
                data.tradeName(),
                segment,
                data.city(),
                data.state(),
                data.websiteUrl(),
                data.instagramUrl(),
                data.phone(),
                data.businessEmail(),
                data.address(),
                data.notes());
    }

    /** Registra uma origem ligada a uma oportunidade (RF-011: lead tem origem). */
    @Transactional
    public void recordLeadSource(CompanyEntity company, UUID leadId, SourceData source) {
        saveSource(company, leadId, source, organizationContext.requireCurrentUser(), clock.instant());
    }

    private void saveSource(
            CompanyEntity company, UUID leadId, SourceData source, AuthenticatedUser user, Instant now) {
        sourceRepository.save(new LeadSourceEntity(
                UUID.randomUUID(),
                company.getOrganizationId(),
                company.getId(),
                leadId,
                withDefaults(source, now),
                user.userId(),
                now));
    }

    private SourceData withDefaults(SourceData source, Instant now) {
        if (source == null) {
            return new SourceData(SourceType.MANUAL, DEFAULT_SOURCE_NAME, null, null, now, null);
        }
        return source.collectedAt() == null
                ? new SourceData(source.type(), source.name(), source.reference(), source.url(), now, source.note())
                : source;
    }

    private static void addAll(Map<UUID, CompanyEntity> found, List<CompanyEntity> companies) {
        companies.forEach(company -> found.putIfAbsent(company.getId(), company));
    }

    private static void collect(
            Map<UUID, DuplicateCandidate> candidates, DuplicateSignal signal, List<CompanyEntity> matches) {
        for (CompanyEntity match : matches) {
            candidates.computeIfAbsent(
                            match.getId(), unusedId -> new DuplicateCandidate(match, EnumSet.noneOf(DuplicateSignal.class)))
                    .signals()
                    .add(signal);
        }
    }

    /** Empresa possivelmente igual e os sinais que a aproximaram. */
    public record DuplicateCandidate(CompanyEntity company, Set<DuplicateSignal> signals) {}

    /**
     * Alteracao parcial de empresa.
     *
     * <p>Nulo mantem o valor atual; texto vazio limpa um campo opcional. A
     * validacao de entrada impede limpar os obrigatorios.
     */
    public record CompanyPatch(
            String name,
            String tradeName,
            String segment,
            String city,
            String state,
            String websiteUrl,
            String instagramUrl,
            String phone,
            String businessEmail,
            String address,
            String notes) {

        CompanyData applyTo(CompanyEntity current) {
            return new CompanyData(
                    pick(name, current, CompanyEntity::getName),
                    pick(tradeName, current, CompanyEntity::getTradeName),
                    pick(segment, current, CompanyEntity::getSegment),
                    pick(city, current, CompanyEntity::getCity),
                    pick(state, current, CompanyEntity::getState),
                    pick(websiteUrl, current, CompanyEntity::getWebsiteUrl),
                    pick(instagramUrl, current, CompanyEntity::getInstagramUrl),
                    pick(phone, current, CompanyEntity::getPhone),
                    pick(businessEmail, current, CompanyEntity::getBusinessEmail),
                    pick(address, current, CompanyEntity::getAddress),
                    pick(notes, current, CompanyEntity::getNotes));
        }

        private static String pick(String patched, CompanyEntity current, Function<CompanyEntity, String> getter) {
            return patched != null ? patched : getter.apply(current);
        }
    }
}
