# 01 — Product overview

## 1. Identification

- **Name:** JH.COMP Prospect
- **Owner:** JH.COMP
- **Initial market:** small businesses and service providers in Poços de
  Caldas and the surrounding region, Minas Gerais, Brazil.
- **Model:** internal tool.
- **Possible future:** multi-organisation SaaS. The data model is already
  isolated per organisation, which keeps that option open without committing
  to it.

## 2. Problem

JH.COMP is an early-stage company and needs a predictable commercial process.
Prospecting done ad hoc makes it hard to identify suitable companies, record
what was found, prioritise opportunities, personalise outreach and learn which
services are actually in demand.

This product centralises that process and removes repetitive work **without**
replacing human judgement in commercial communication.

## 3. Value proposition

Turn legitimate, verifiable business information into organised opportunities
for services such as:

- institutional websites and landing pages;
- catalogues and online stores;
- systems and automation;
- WhatsApp integrations;
- computer support and maintenance, where relevant.

The product must never assume a company needs a service merely because some
piece of information was not found. Every opportunity carries evidence, a
confidence level and the possibility of review.

## 4. Objectives

### MVP objectives

1. Centralise companies and leads.
2. Support manual entry and CSV import.
3. Support search by region and segment using permitted sources.
4. Prevent duplicate records.
5. Record the origin and collection date of every piece of information.
6. Identify digital opportunities backed by evidence.
7. Prioritise leads with an explainable score.
8. Manage the commercial process in a pipeline.
9. Draft personalised messages for human review.
10. Record contacts, replies and do-not-contact requests.

> Objective 11 of the original plan — "produce a basic website preview from
> controlled templates" — was removed from the MVP by decision D13. The
> Website Studio is not implemented and has no entities, endpoints or screens.

### Later objectives

- More data connectors.
- Assisted publication of contracted projects.
- Official integrations with communication channels.
- Team management and finer-grained permissions.
- Plans, billing and SaaS operation.

## 5. Non-objectives

Within the MVP, JH.COMP Prospect is **not**:

- a mass-sending tool;
- a system for collecting data by unauthorised scraping;
- a platform for indiscriminate enrichment of personal data;
- a replacement for a full corporate CRM;
- a site builder that publishes automatically on behalf of third parties;
- a system that makes commercial decisions without supervision.

## 6. Users and roles

### Organisation administrator

- Manages members and settings.
- Accesses the organisation's data.
- Manages integrations and parameters.
- Reads the audit trail.

### Sales operator

- Researches and registers companies.
- Qualifies leads.
- Manages the pipeline and tasks.
- Prepares messages and records interactions.

JH.COMP can start with a single administrator. The authorisation model is
nevertheless built for several members per organisation, because retrofitting
tenancy later is far more expensive than carrying it from the start.

## 7. Initial segments

Candidate segments, subject to validation by research and real conversations:

- barbershops and salons;
- restaurants and snack bars;
- clinics and pet shops;
- garages and automotive services;
- service providers;
- small retailers.

Segment choice must follow observable signals, capacity to deliver and the
response to outreach — not assumptions about an industry.

## 8. Main journey

1. The operator picks a city and a segment.
2. Companies are registered by hand, imported from CSV, or fetched from an
   authorised source.
3. The system normalises the records and looks for duplicates.
4. The operator reviews the data and its origin.
5. The system or the operator starts a digital presence analysis.
6. The analysis records findings, evidence, confidence and date.
7. The system computes an explainable score.
8. The operator decides whether the lead is a fit.
9. The system drafts a contextual message.
10. The operator reviews it and sends it outside the system, or through an
    official integration once one exists.
11. The interaction and its outcome are recorded.
12. The lead advances in the pipeline.
13. If there is interest, a proposal follows.

## 9. Metrics

### Operational

- Companies added per period.
- Share of duplicate records.
- Share of leads with sufficient data.
- Average analysis time.
- Average analysis cost per lead.
- Share of findings backed by evidence.

### Commercial

- Qualified leads.
- Approved and performed outreach.
- Replies received.
- Commercial conversations started.
- Proposals sent.
- Deals won and lost.
- Revenue attributed to won deals.
- Conversion by segment and by origin.

Metrics must not reward indiscriminate contact volume. Quality, relevance and
respect for the recipient's preferences are requirements of the process, not
trade-offs against it.

## 10. MVP success criteria

The MVP counts as useful when an operator can, in a single flow:

- import or register companies;
- find and review duplicates;
- check the origin and freshness of the data;
- record an analysis with evidence;
- prioritise a lead;
- move the lead through the pipeline;
- generate and review a message;
- record an interaction or a do-not-contact request.

The first operational goal is to run that flow with roughly 30 real companies
from the region, obtained from permitted sources. That number is a validation
target, not a conversion promise.
