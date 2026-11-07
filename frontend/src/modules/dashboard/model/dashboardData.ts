import { SOURCE_TYPE_LABEL, STATUS_LABEL } from '../../../app/labels';
import { SOURCE_TYPES } from '../../../types/company';
import type {
  Activity,
  AttentionSection,
  DashboardAttention,
  DashboardConversion,
  DashboardFunnel,
  DashboardSummary,
  DashboardTimeline,
  Granularity,
  InteractionChannel,
  InteractionType,
} from '../../../types/crm';
import { LEAD_STATUSES, type LeadStatus } from '../../../types/lead';

/**
 * View model for the dashboard.
 *
 * Presentation components receive only these types, never the API response:
 * the API contract can evolve without touching the layout, and the same layout
 * runs on `mockDashboardData` in tests and in the preview screen. All of the
 * conversion lives in this file.
 *
 * Type names end in `View` wherever `types/crm.ts` already uses the obvious
 * name for the API shape — `AttentionItem`, `FunnelStage` and `ConversionRow`
 * are the wire types, and these are what the screen consumes.
 */

/** Calendar dates (YYYY-MM-DD), inclusive on both ends. */
export interface SelectedPeriod {
  readonly from: string;
  readonly to: string;
}

/** Events inside the selected period. */
export interface PeriodMetrics {
  readonly companiesAdded: number;
  readonly contactsRegistered: number;
  readonly dealsWon: number;
  /** In Brazilian reais. */
  readonly wonValue: number;
}

/** The situation right now, independent of the period. */
export interface CurrentMetrics {
  readonly activeLeads: number;
  readonly openProposals: number;
  readonly openTasks: number;
  readonly overdueTasks: number;
}

export interface LeadStatusCount {
  readonly status: LeadStatus;
  readonly label: string;
  readonly count: number;
}

/** What happened: a stage change (no `from` means the lead was opened) or an interaction. */
export type Transition =
  | { readonly kind: 'status'; readonly from?: LeadStatus; readonly to?: LeadStatus }
  | { readonly kind: 'interaction'; readonly interaction?: InteractionType; readonly channel?: InteractionChannel };

export interface RecentActivity {
  readonly id: string;
  readonly leadId: string;
  readonly company: string;
  readonly transition: Transition;
  /** ISO-8601 instant in UTC. */
  readonly at: string;
  readonly user?: string;
}

/** Everything the dashboard summary shows. Activity is paginated and fetched separately. */
export interface DashboardData {
  readonly period: SelectedPeriod;
  readonly periodMetrics: PeriodMetrics;
  readonly currentMetrics: CurrentMetrics;
  /** Every stage, in funnel order, including the empty ones. */
  readonly leadsByStatus: readonly LeadStatusCount[];
  /** Active companies with no segment: conversion by segment is incomplete without them. */
  readonly companiesWithoutSegment: number;
}

export function toDashboardData(response: DashboardSummary): DashboardData {
  const { summary } = response;
  return {
    period: { from: response.from, to: response.to },
    periodMetrics: {
      companiesAdded: summary.companiesAdded,
      contactsRegistered: summary.contactsRegistered,
      dealsWon: summary.dealsWon,
      wonValue: summary.wonValue,
    },
    currentMetrics: {
      activeLeads: summary.activeLeads,
      openProposals: summary.openProposals,
      openTasks: summary.openTasks,
      overdueTasks: summary.overdueTasks,
    },
    leadsByStatus: LEAD_STATUSES.map((status) => ({
      status,
      label: STATUS_LABEL[status],
      count: summary.leadsByStatus[status] ?? 0,
    })),
    companiesWithoutSegment: summary.companiesWithoutSegment,
  };
}

export function toActivity(activity: Activity): RecentActivity {
  const { interactionType, channel, fromStatus, toStatus, actorName } = activity;
  const transition: Transition =
    activity.type === 'INTERACTION'
      ? {
          kind: 'interaction',
          ...(interactionType === undefined ? {} : { interaction: interactionType }),
          ...(channel === undefined ? {} : { channel }),
        }
      : {
          kind: 'status',
          ...(fromStatus === undefined ? {} : { from: fromStatus }),
          ...(toStatus === undefined ? {} : { to: toStatus }),
        };
  return {
    // The API gives the event no id; type, lead and instant identify it in the list.
    id: `${activity.type}-${activity.leadId}-${activity.occurredAt}`,
    leadId: activity.leadId,
    company: activity.companyName,
    transition,
    at: activity.occurredAt,
    // Optional property set only when there is a value (exactOptionalPropertyTypes).
    ...(actorName === undefined ? {} : { user: actorName }),
  };
}

/** One interval of the series; `start` is its first day inside the period. */
export interface SeriesPoint extends PeriodMetrics {
  readonly start: string;
}

export interface TimeSeries {
  readonly granularity: Granularity;
  readonly points: readonly SeriesPoint[];
}

export function toTimeSeries(response: DashboardTimeline): TimeSeries {
  return {
    granularity: response.granularity,
    points: response.points.map((point) => ({
      start: point.start,
      companiesAdded: point.companiesAdded,
      contactsRegistered: point.contactsRegistered,
      dealsWon: point.dealsWon,
      wonValue: point.wonValue,
    })),
  };
}

/** Leads opened in the period for one group (origin or segment) and what came of them (RF-071). */
export interface ConversionGroup {
  /** Absent when the segment is unset or the origin was never recorded. */
  readonly key?: string;
  readonly label: string;
  readonly leads: number;
  readonly contacted: number;
  readonly won: number;
  /** Below the minimum sample the rate is not presented at all (D29). */
  readonly sufficientSample: boolean;
}

export interface ConversionView {
  readonly minimumSample: number;
  readonly bySource: readonly ConversionGroup[];
  readonly bySegment: readonly ConversionGroup[];
  /** Every lead opened in the period, summed across origins. */
  readonly total: Omit<ConversionGroup, 'key' | 'label'>;
}

export function toConversion(response: DashboardConversion): ConversionView {
  const { minimumSample, bySource, bySegment } = response.conversion;
  const group = (row: (typeof bySource)[number], label: string): ConversionGroup => ({
    ...(row.key === undefined ? {} : { key: row.key }),
    label,
    leads: row.leads,
    contacted: row.contacted,
    won: row.won,
    sufficientSample: row.sufficientSample,
  });
  const leads = bySource.reduce((sum, row) => sum + row.leads, 0);
  return {
    minimumSample,
    bySource: bySource.map((row) => group(row, sourceLabel(row.key))),
    bySegment: bySegment.map((row) => group(row, row.key ?? 'Sem segmento')),
    total: {
      leads,
      contacted: bySource.reduce((sum, row) => sum + row.contacted, 0),
      won: bySource.reduce((sum, row) => sum + row.won, 0),
      sufficientSample: leads >= minimumSample,
    },
  };
}

function sourceLabel(key: string | undefined): string {
  const type = SOURCE_TYPES.find((candidate) => candidate === key);
  return type === undefined ? 'Sem origem registrada' : SOURCE_TYPE_LABEL[type];
}

/**
 * Change against the previous period of the same length. With no base — zero
 * before — there is no percentage: "+∞%" would be misleading.
 */
export type Variation =
  { readonly kind: 'percent'; readonly value: number } | { readonly kind: 'noBase' } | { readonly kind: 'noData' };

export function variation(current: number, previous: number | undefined): Variation {
  if (previous === undefined || (previous === 0 && current === 0)) {
    return { kind: 'noData' };
  }
  if (previous === 0) {
    return { kind: 'noBase' };
  }
  return { kind: 'percent', value: ((current - previous) / previous) * 100 };
}

/** The period immediately before, with the same number of days. */
export function previousPeriod(period: SelectedPeriod): SelectedPeriod {
  const start = Date.parse(`${period.from}T00:00:00Z`);
  const end = Date.parse(`${period.to}T00:00:00Z`);
  const day = 86_400_000;
  const newEnd = start - day;
  return { from: isoDate(newEnd - (end - start)), to: isoDate(newEnd) };
}

function isoDate(epoch: number): string {
  return new Date(epoch).toISOString().slice(0, 10);
}

export interface AttentionRow {
  readonly leadId: string;
  readonly company: string;
  readonly status: LeadStatus;
  readonly at: string;
  readonly detail?: string;
  readonly overdue: boolean;
}

export interface AttentionSectionView {
  readonly total: number;
  readonly items: readonly AttentionRow[];
}

/** What needs action now (doc 08 §4). */
export interface AttentionView {
  readonly tasks: AttentionSectionView;
  readonly staleLeads: AttentionSectionView;
  readonly quietProposals: AttentionSectionView;
  readonly withoutTask: AttentionSectionView;
}

export function toAttention(response: DashboardAttention): AttentionView {
  return {
    tasks: toSection(response.tasks),
    staleLeads: toSection(response.staleLeads),
    quietProposals: toSection(response.quietProposals),
    withoutTask: toSection(response.withoutTask),
  };
}

function toSection(section: AttentionSection): AttentionSectionView {
  return {
    total: section.total,
    items: section.items.map((item) => ({
      leadId: item.leadId,
      company: item.companyName,
      status: item.status,
      at: item.at,
      ...(item.detail === undefined ? {} : { detail: item.detail }),
      overdue: item.overdue,
    })),
  };
}

export interface FunnelStageView {
  readonly status: LeadStatus;
  readonly label: string;
  /** Leads in the cohort that reached this stage or went beyond it. */
  readonly reached: number;
  /** Of those that reached the previous stage, how many reached this one; absent without a sample. */
  readonly passthrough?: number;
  /** Average days for those who have already left the stage; absent without a sample. */
  readonly avgDays?: number;
  readonly exits: number;
}

export interface FunnelView {
  readonly minimumSample: number;
  readonly cohort: number;
  readonly stages: readonly FunnelStageView[];
}

/**
 * Conversion funnel. The step from one stage to the next only gets a
 * percentage when the previous stage has the minimum sample (D29): "1 of 1"
 * would read as 100%.
 */
export function toFunnel(response: DashboardFunnel): FunnelView {
  const { minimumSample, cohort, stages } = response.funnel;
  return {
    minimumSample,
    cohort,
    stages: stages.map((stage, index) => {
      const previous = index === 0 ? cohort : (stages[index - 1]?.reached ?? 0);
      return {
        status: stage.status,
        label: STATUS_LABEL[stage.status],
        reached: stage.reached,
        ...(index > 0 && previous >= minimumSample ? { passthrough: stage.reached / previous } : {}),
        ...(stage.avgDays === undefined ? {} : { avgDays: stage.avgDays }),
        exits: stage.exits,
      };
    }),
  };
}
