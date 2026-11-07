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
 * Company use cases (RF-010, RF-014, RF-020, RF-022).
 *
 * <p>The organisation always comes from {@link OrganizationContext}; no public
 * method takes it as a parameter (RNF-001).
 */
@Service
public class CompanyService {

    /** Repeated spaces inside a segment collapse to one: "Barbearia  Centro" is "Barbearia Centro". */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** Newest first, with the id as tiebreak so pagination is stable. */
    public static final Sort DEFAULT_SORT =
            Sort.by(Sort.Direction.DESC, CompanyEntity::getCreatedAt).and(Sort.by(CompanyEntity::getId));

    /** The cap on one batch, the same as the lead queue's. */
    public static final int MAX_BULK = 100;

    /** An id no company has: the "exclude self" when there is no self yet. */
    private static final UUID NO_COMPANY = new UUID(0L, 0L);

    /** The origin recorded when the operator creates a company without giving one. */
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
     * Registers the company and records where it came from (RF-020).
     *
     * <p>Company and origin are written in the same transaction: a company
     * without provenance would violate RF-022.
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

    /** Partial update: a null field keeps its value, an empty string clears it. */
    @Transactional
    public CompanyEntity update(UUID companyId, CompanyPatch patch) {
        CompanyEntity company = requireActive(companyId);
        company.update(withCanonicalSegment(company.getOrganizationId(), patch.applyTo(company)), clock.instant());
        return company;
    }

    /** Archiving is not deleting (RF-010). Repeating it has no effect. */
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
     * Companies in the same organisation that may be the same one (RF-014).
     *
     * <p>It only suggests: nothing is merged or discarded here. A similar name
     * is not a signal; only an identical normalised name in the same city and
     * state, and even that is the weakest signal (doc 06 §4).
     */
    @Transactional(readOnly = true)
    public List<DuplicateCandidate> duplicates(UUID companyId) {
        CompanyEntity company = find(companyId);
        return findDuplicates(company.getOrganizationId(), company.getNormalizedDomain(), company.getNormalizedPhone(),
                company.getNormalizedName(), company.getCity(), company.getState(), company.getId());
    }

    /**
     * Possible duplicates of a company still being typed, using the same
     * signals as {@link #duplicates}. {@code excludedId} is the company itself
     * when editing. Without name, city and state, only the contact signals
     * apply.
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

    /** What the form already has to compare with. Nulls do not count as a signal. */
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
     * The same segment, or the same city and state, across several companies
     * at once (bulk data quality). All in one transaction; an archived company
     * is untouched and counted separately. An id from another organisation
     * answers 404 for the whole batch, as if it did not exist.
     *
     * @return how many changed, and how many were skipped as archived
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
     * A company of the active organisation that accepts changes.
     *
     * <p>Also used by the lead module, which cannot open an opportunity for an
     * archived company.
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
     * Companies of the active organisation matching any of the normalised
     * values given, archived ones included: an archived company is still the
     * same company.
     *
     * <p>Used by the import to classify the whole file with three queries
     * instead of three per row.
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
     * Completes an existing company with imported data (D23).
     *
     * <p>It only fills empty fields, and records the origin even when it fills
     * nothing, because the imported row also confirms the company (RF-022). An
     * archived company is not changed.
     *
     * @return the fields filled, or empty when the company is archived
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
     * A segment in the spelling that already exists in the organisation (D31):
     * "barbearia " becomes "Barbearia" when that form is already in use.
     * Surrounding and repeated spaces are removed; blank is the same as no
     * segment. A new segment is kept exactly as typed.
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

    /** Records an origin tied to an opportunity (RF-011: a lead has an origin). */
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

    /** A possibly identical company, and the signals that brought it up. */
    public record DuplicateCandidate(CompanyEntity company, Set<DuplicateSignal> signals) {}

    /**
     * A partial change to a company.
     *
     * <p>Null keeps the current value; an empty string clears an optional
     * field. Input validation prevents clearing the required ones.
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
