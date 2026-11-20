import { http, HttpResponse } from 'msw';
import type { AgentRun, AuditEvent, FollowUpTask, Readiness } from '../api/types';

// Synthetic readiness results and follow-up tasks for the mock appointments.
const NOTICE =
  'Administrative completeness check only. It is not a medical decision and does not guarantee coverage or payment.';

const initialReadiness = (): Map<string, Readiness> =>
  new Map([
    [
      'appt-001',
      {
        appointmentId: 'appt-001',
        status: 'INCOMPLETE',
        notice: NOTICE,
        evaluatedAt: '2026-11-10T10:00:00Z',
        incompleteReasons: ['No INSURANCE_CARD document in the record'],
        reviewReasons: ['ID_DOCUMENT text extraction failed'],
        checks: [
          {
            ruleId: 'rule-001',
            requirement: 'DOCUMENT',
            documentCategories: ['ID_DOCUMENT'],
            mandatory: true,
            applicable: true,
            status: 'NEEDS_REVIEW',
            reason: 'ID_DOCUMENT text extraction failed',
            remainingAction: 'Check the scan and correct its metadata',
            requiresHumanReview: true,
            documents: [
              {
                documentId: 'doc-002',
                category: 'ID_DOCUMENT',
                version: 1,
                status: 'UPLOADED',
                uploadedAt: '2026-11-09T14:00:00Z',
              },
            ],
            confirmations: [],
          },
          {
            ruleId: 'rule-002',
            requirement: 'DOCUMENT',
            documentCategories: ['INSURANCE_CARD'],
            mandatory: true,
            applicable: true,
            status: 'MISSING',
            reason: 'No INSURANCE_CARD document in the record',
            remainingAction: 'Ask the patient for an INSURANCE_CARD',
            requiresHumanReview: false,
            documents: [],
            confirmations: [],
          },
        ],
      },
    ],
  ]);

const initialTasks = (): FollowUpTask[] => [
  {
    id: 'task-001',
    appointmentId: 'appt-001',
    patientId: 'patient-001',
    requirementCode: 'INSURANCE_CARD',
    status: 'AWAITING_APPROVAL',
    checkStatus: 'MISSING',
    reason: 'No INSURANCE_CARD document in the record',
    suggestedAction: 'Ask the patient for an INSURANCE_CARD',
    evidenceDocumentIds: [],
    language: 'FR',
    dueAt: null,
    createdBy: null,
    runId: 'run-1',
    approvalId: null,
    deliveredAt: null,
    deliveredBy: null,
    deliveredByRun: null,
    resolvedAt: null,
    createdAt: '2026-11-10T10:01:00Z',
    updatedAt: '2026-11-10T10:01:00Z',
    approvals: [],
  },
];

/** A finished run: every request succeeds at once with the same report (mock). */
function mockRun(id: string, request: string): AgentRun {
  const at = new Date().toISOString();
  return {
    id,
    request,
    status: 'SUCCEEDED',
    statusReason: null,
    report: {
      summary: '1 appointment checked: 1 incomplete.',
      counts: { ready: 0, needsReview: 0, incomplete: 1 },
      appointments: [
        {
          appointmentId: 'appt-001',
          status: 'INCOMPLETE',
          findings: [
            {
              requirementCode: 'INSURANCE_CARD',
              status: 'MISSING',
              action: 'Ask the patient for an INSURANCE_CARD',
            },
            { requirementCode: 'ID_DOCUMENT', status: 'NEEDS_REVIEW', action: null },
          ],
        },
      ],
    },
    toolCalls: 3,
    promptTokens: 1200,
    completionTokens: 300,
    createdAt: at,
    startedAt: at,
    finishedAt: at,
    actions: [
      {
        seq: 1,
        tool: 'findAppointments',
        arguments: {},
        resultStatus: 'OK',
        latencyMs: 42,
        tokens: 0,
        at,
      },
      {
        seq: 2,
        tool: 'getReadiness',
        arguments: { appointmentId: 'appt-001' },
        resultStatus: 'OK',
        latencyMs: 18,
        tokens: 0,
        at,
      },
      {
        seq: 3,
        tool: 'createFollowUpDraft',
        arguments: { appointmentId: 'appt-001' },
        resultStatus: 'OK',
        latencyMs: 25,
        tokens: 0,
        at,
      },
    ],
  };
}

let readiness = initialReadiness();
let tasks = initialTasks();
let runs = new Map<string, AgentRun>();

// Synthetic audit events, newest first.
const MOCK_AUDIT_EVENTS: AuditEvent[] = [
  {
    id: 'audit-003',
    action: 'AGENT_RUN_FINISHED',
    entityType: 'AGENT_RUN',
    entityId: 'run-1',
    actorUserId: 'user-001',
    actorName: 'Sarra Test',
    runId: 'run-1',
    changeSummary: { status: 'SUCCEEDED', toolCalls: 2 },
    occurredAt: '2026-11-20T09:00:05Z',
  },
  {
    id: 'audit-002',
    action: 'AGENT_TOOL_CALLED',
    entityType: 'AGENT_RUN',
    entityId: 'run-1',
    actorUserId: 'user-001',
    actorName: 'Sarra Test',
    runId: 'run-1',
    changeSummary: { tool: 'checkReadiness', seq: 1, resultStatus: 'OK' },
    occurredAt: '2026-11-20T09:00:03Z',
  },
  {
    id: 'audit-001',
    action: 'PATIENT_CREATED',
    entityType: 'PATIENT',
    entityId: 'patient-001',
    actorUserId: null,
    actorName: null,
    runId: null,
    changeSummary: { patientRef: 'P-0001' },
    occurredAt: '2026-11-19T15:30:00Z',
  },
];

export function resetMockWorkflow(): void {
  readiness = initialReadiness();
  tasks = initialTasks();
  runs = new Map();
}

const notFound = () =>
  HttpResponse.json({ code: 'NOT_FOUND', message: 'Not found' }, { status: 404 });

export const workflowHandlers = [
  http.post('*/agent-runs', async ({ request }) => {
    const body = (await request.json()) as { request?: string };
    const text = body.request?.trim() ?? '';
    if (!text || text.length > 500) {
      return HttpResponse.json(
        { code: 'VALIDATION_ERROR', message: 'The request must be 1 to 500 characters' },
        { status: 400 },
      );
    }
    const id = `run-${runs.size + 1}`;
    runs.set(id, mockRun(id, text));
    return HttpResponse.json({ runId: id, status: 'QUEUED' }, { status: 202 });
  }),

  http.get('*/agent-runs/:id', ({ params }) => {
    const run = runs.get(String(params.id));
    return run ? HttpResponse.json(run) : notFound();
  }),

  http.get('*/appointments/:id/readiness', ({ params }) => {
    const found = readiness.get(String(params.id));
    return found ? HttpResponse.json(found) : notFound();
  }),

  http.post('*/appointments/:id/readiness-checks', ({ params }) => {
    const id = String(params.id);
    const result: Readiness = readiness.get(id) ?? {
      appointmentId: id,
      status: 'READY',
      notice: NOTICE,
      evaluatedAt: new Date().toISOString(),
      incompleteReasons: [],
      reviewReasons: [],
      checks: [],
    };
    const next = { ...result, evaluatedAt: new Date().toISOString() };
    readiness.set(id, next);
    return HttpResponse.json(next);
  }),

  http.get('*/audit-events', ({ request }) => {
    const params = new URL(request.url).searchParams;
    const runId = params.get('runId');
    const items = MOCK_AUDIT_EVENTS.filter((e) => !runId || e.runId === runId);
    return HttpResponse.json({ items, page: 0, size: 25, total: items.length });
  }),

  http.get('*/follow-up-tasks', ({ request }) => {
    const params = new URL(request.url).searchParams;
    const statuses = params.get('status')?.split(',') ?? [];
    const appointmentId = params.get('appointmentId');
    return HttpResponse.json(
      tasks
        .filter((t) => !appointmentId || t.appointmentId === appointmentId)
        .filter((t) => statuses.length === 0 || statuses.includes(t.status))
        .map((t) => ({ ...t, approvals: [] })),
    );
  }),

  http.get('*/follow-up-tasks/:id', ({ params }) => {
    const task = tasks.find((t) => t.id === params.id);
    return task ? HttpResponse.json(task) : notFound();
  }),
];
