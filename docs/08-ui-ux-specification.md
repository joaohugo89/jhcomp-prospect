# 08 — UI/UX specification

> **The interface itself is in Portuguese.** It is an internal tool for a
> Brazilian operator, so every label, message and empty state the user reads
> is written in Portuguese. This document, like the rest of the
> documentation, is in English.

## 1. Purpose of the interface

The operator must be able to run the commercial flow quickly, with little
friction and enough context to decide.

Priorities: clarity · controlled information density · efficient filters ·
explicit loading and error states · accessibility · responsiveness ·
confirmation on destructive actions.

## 2. Visual direction

The identity follows the JH.COMP brand without competing with the data: the
same language as the company's sites (joaohugo.com and its subdomains).

Oxblood `#44171b` and cream `#f7f3ee`, red `#e53935` as the accent, Fraunces
for headings, Inter for body text, JetBrains Mono for short uppercase labels,
and a glassmorphism finish — translucent surfaces with `backdrop-filter` and a
hairline of cream at 12% opacity.

Two modes share one set of tokens:

- **light** — cream background, oxblood navigation and buttons;
- **dark** — deep oxblood background, translucent cream surfaces.

The default follows the device preference; a button at the top of the sidebar
switches between the two and the choice is kept in `localStorage`. The login
screen is oxblood in both modes.

There is no blue in the palette: the families used for information and for
funnel stages are sand, terracotta and plum. A blue sitting next to the
oxblood read as an accident rather than a decision.

Definitions are in `frontend/src/index.css`; the theme switch is in
`frontend/src/app/theme.ts`.

## 3. Navigation

```text
JH.COMP Prospect
├── Dashboard
├── Pipeline
├── Leads
├── Empresas            (companies)
├── Importações         (imports)
├── Não contato         (do not contact)
└── Membros             (members, admin only)
```

The sidebar groups these under *Trabalho*, *Base* and *Administração*.

There is no "Outreach" entry: composing a message belongs to the lead it
concerns, and a separate screen would have invited working through messages
instead of through leads. There is no "Website Studio" entry either — the
feature was removed by D13.

Anything not implemented is hidden rather than shown as disabled. A menu entry
that leads nowhere teaches the operator to distrust the menu.

## 4. Dashboard

Shows active leads, leads per stage, companies added in the period, contacts
recorded, open proposals, deals won, recent activity and pending tasks, plus
an attention panel of what needs action now (D30) and a conversion funnel.

Filters: period, segment, city, origin, owner.

Charts are not drawn on insufficient data: below the minimum sample of 10 the
rate is withheld rather than shown as a misleading percentage (D29). The empty
state points at registering or importing the first companies instead of
rendering an empty chart.

## 5. Company search

Not implemented — no business source provider exists (RF-021). When one is
added: the criteria are city, state, segment, source and limit; results show
name, segment, location, website, business phone, source, collection date and
a possible-duplicate indicator; the actions are open, select, import, ignore
and review a duplicate.

A field the source did not provide is never displayed as if it were confirmed.

## 6. Company and lead lists

Both lists are work queues rather than tables to browse: the row answers "what
do I do with this one", not merely "what is it".

Columns: company, status, owner, next task, time in stage, value, score.
Features: text search, combinable filters, sorting, pagination, bulk selection
for safe actions only, and saved views — private, or shared when an admin
publishes them (D31).

**Bulk actions can never send messages.** They are limited to owner and
priority for leads, and segment or city for companies.

## 7. Lead detail

Header: company name, segment, location, status, priority, score, main
actions.

Sections: overview, digital presence, history, messages, negotiation. There is
no website project section (D13).

The analysis section shows, for each finding: the finding, its verification
state, the evidence, the date, the confidence, the points awarded out of the
maximum, and the human review. A finding changed by a reviewer still shows
what the automated analysis originally concluded.

## 8. Kanban pipeline

Columns per stage, compact cards carrying the company name, the potential
service, the score, the last interaction and the next task. Two densities,
compact and detailed.

Cards move by drag-and-drop **and** by an accessible control, because
drag-and-drop alone excludes keyboard users. Every change is persisted and
reports success or failure.

Columns are `Lead.status`, one to one (D2). Won and Lost show only what closed
in the last 30 days; older ones stay in the lead list.

## 9. Outreach

The composer shows the channel, the recipient, the context used, the draft, an
explicit list of unverified statements, a regenerate button, manual editing,
approve and discard.

Before approving: the do-not-contact block is checked, the sender identity is
shown, the origin and context of the message are shown, and both recipient and
content can be reviewed. Approving a message with unverified claims requires
ticking that they were reviewed.

**There is no bulk send button**, and no send button at all: the operator
sends outside the system and comes back to record it.

## 10. Website Studio

Removed by decision D13. No screen, no route, no menu entry.

## 11. Interface states

Every screen covers: loading · empty · success · error · no permission ·
session expired · integration unavailable · partial result · stale data.

Error messages are useful and never reveal internal detail.

## 12. Accessibility and responsiveness

Keyboard navigation · visible focus · labels on fields · adequate contrast ·
validation messages associated with their field · never colour alone to convey
status · tables usable on small screens · forms in a logical order ·
confirmation on destructive actions.

## 13. Reusable components

`AppShell`, `PageHeader`, `SidePanel`, `StatTile`, `Badges` (status, priority,
score, message status), `FormField`, `SegmentSelect`, `Pagination`,
`ConfirmDialog`, and the shared state views (loading, empty, error) in
`States`. Control classes — buttons, inputs, cards, chips, focus ring — are
centralised in `components/styles.ts` so focus and disabled states are
identical everywhere.

## 14. Product principle

The interface prioritises the next useful action: review a lead, validate a
finding, answer a company, prepare a proposal. It is not optimised for the
volume of messages sent, and no screen reports that number as an achievement.
