# 07 — Security, privacy and LGPD

> This document specifies technical controls. It does not replace legal
> advice: the legal basis and the processing procedures must be validated
> against the real operation. LGPD is the Brazilian General Data Protection
> Law (Lei Geral de Proteção de Dados), which governs this product's market.

## 1. Principles

Specific and legitimate purpose · adequacy and necessity · transparency · data
quality and currency · security and prevention · non-discrimination ·
accountability.

Commercial data about a business can be personal data whenever it identifies a
natural person — a sole trader, a partner, an individual contact. **Do not
assume that publicly available data carries no legal obligation.**

## 2. Data inventory

For each category, document: the data collected, the purpose, the origin, the
data subject or category of subject, the legal basis assessed, who has
internal access, which provider is involved, the retention period, and the
correction or deletion procedure.

Collect only what is needed to evaluate and run the commercial opportunity.

## 3. Legal basis and prospecting

Do not assume that legitimate interest authorises any prospecting.

Where that basis is relied on, document the legitimate purpose, the necessity
of the data, the data subject's reasonable expectation, the impact on rights
and freedoms, the mitigating measures, and the transparency and objection
mechanism.

If another basis applies, record the corresponding justification. Consent must
not be manufactured, and must never be presumed from the mere availability of
a contact.

Messages must be relevant, clearly attributed, and must honour do-not-contact
requests. Channel-specific rules apply on top of this.

> **Operational gap worth stating.** The technical controls below are
> implemented; what lives outside the software is not. Before any real
> outreach there must be a published contact address for data subjects and
> someone who answers it within the legal deadline. This is cheap now and
> expensive later.

## 4. Authentication security

- Passwords hashed with a delegating encoder that writes BCrypt prefixed
  `{bcrypt}`, which allows a later migration to Argon2id without invalidating
  existing passwords.
- Never store a password in clear text.
- Rate limiting against automated attempts, by e-mail **and** by IP, because
  the caller chooses the e-mail.
- Generic responses on login failure, and the hash comparison runs even for an
  unknown user so response time does not reveal which addresses exist.
- Session revocation and expiry; changing or resetting a password ends the
  user's other sessions through `credentials_changed_at` (D28).
- Secure cookies: `HttpOnly`, `SameSite=Lax`, `Secure` in production.
- MFA is a possible later addition.

The rate limiter uses **atomic reservation** (`tryAcquire`, D20). A
check-then-record pair must never be reintroduced: between the check and the
accounting, concurrent requests slip through. Eviction by cap **never removes
a blocked entry** — without that rule, flooding the limiter erases the
attacker's own block and hands them unlimited attempts.

Any test for a limit must be validated in reverse: remove the protection and
confirm the test fails. Three versions of the limiter test passed without the
protection — one never reached the cap, one exhausted the IP cap first, and
one used a frozen clock, which gives every entry the same instant and makes
"oldest" meaningless.

## 5. Authorisation and isolation

- The organisation context derives from the authenticated identity.
- The organisation is checked on every read and write of a private resource.
- Ownership is validated across related entities (company, lead, deal,
  analysis, message).
- `organization_id` from the frontend is never trusted.
- Cross-organisation access is tested automatically, and such a test is
  mandatory for every new private entity or endpoint.
- Roles carry the least privilege needed.
- Administrative actions are audited.
- A resource from another organisation answers **404, never 403**: saying it
  exists but belongs to another tenant is already a leak.
- Membership is revalidated against the database on every authenticated
  request (D21), so revoking access takes effect on the next request.

## 6. Data protection

- HTTPS in production.
- Encryption at rest where the infrastructure supports it.
- Secrets in a secret manager or protected variables.
- Never commit `.env`, tokens or keys.
- Never log message content or personal data without need.
- Restrict access to exports.
- Protect imported files and limit their size and type.
- **SSRF protection in any URL fetcher:** block private, loopback and metadata
  addresses, and apply a timeout, a redirect limit and a response size limit.

Two details that cost real time:

- URL validation parses with `java.net.URI`, not a regular expression. The
  first regex-based version accepted `javascript:1,alert(1)`.
- Closing an HttpClient 5 response drains the rest of the body. A server that
  sends more than the limit and keeps dripping bytes never hits the read
  timeout and holds the caller forever; `SafeHttpFetcher` cancels the request
  before closing it.

## 7. AI and providers

- Send the provider only the data that is necessary.
- Review the provider's retention and data-use terms.
- Never send credentials or sensitive data.
- Treat the model's response as untrusted: validate the schema, cap the size
  and the cost, and never execute returned code.
- Record provider, model and prompt version where needed.
- **The model has no tool for sending or publishing.** This is enforced by the
  absence of such a tool in the AI layer, with a structural test, not by a
  prompt instruction.
- Keep system instructions separate from external data to reduce prompt
  injection.
- Content from external pages is untrusted data, never an instruction to the
  system.

## 8. External sources

Use official APIs or sources that permit access. Check terms, licences,
attribution and storage rules. Respect limits and quotas. Never work around a
login, paywall, CAPTCHA, block or technical control. Do not scrape interfaces
where that is prohibited. Record the source and the collection date. Do not
reuse data for an incompatible purpose.

## 9. Retention and data subject rights

A documented policy is needed for review and refresh, retention, deletion or
anonymisation, access and correction, objection and opt-out, handling
requests, and the minimum preservation required to honour contact blocks.

Specific periods must be set after legal and operational analysis. Data is not
kept indefinitely out of convenience.

## 10. Do-not-contact list

The list stores the minimum identifier needed, restricts access, records the
reason and the date, is consulted before any approach, prevents a new import
from reactivating a blocked contact, and has an audit trail.

The identifier is stored normalised by the same component that normalises
company contacts, so a request made in one phone format blocks every format of
that number.

## 11. Auditing and incidents

Audited: logins and relevant security events, permission creation and change,
exports, changes to blocks, approval and recording of contacts, and
administrative operations.

Incident plan: identify and contain; preserve evidence; assess scope and
impact; fix the cause; evaluate notification obligations; document and revise
the controls.

One implementation note: `audit_event` has a foreign key to `organization`, so
`SELECT ... FOR UPDATE` on the organisation row combined with auditing in a
`REQUIRES_NEW` transaction deadlocks. Serialise with `pg_advisory_xact_lock`.

## 12. Launch checklist

- [x] Multi-tenant isolation tested.
- [x] Secrets out of the repository.
- [x] URL fetcher protected against SSRF.
- [x] Do-not-contact process tested.
- [x] Backups and restore tested (`scripts/verify-backup.sh --compare`).
- [x] Logs reviewed for exposure.
- [ ] Data inventory and purposes documented.
- [ ] Legal basis assessed for each flow.
- [ ] Privacy notice published.
- [ ] Retention policy defined.
- [ ] Data subject contact channel published and staffed.
- [ ] Providers and their terms reviewed.
- [ ] Incident procedure documented.

The unchecked items are organisational rather than technical, and all of them
must be closed before real prospecting begins — not before the software is
considered finished.
