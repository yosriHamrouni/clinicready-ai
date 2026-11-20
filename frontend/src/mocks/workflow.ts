import { http, HttpResponse } from 'msw';
import type { FollowUpTask, Readiness } from '../api/types';

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
    runId: 'run-001',
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

let readiness = initialReadiness();
let tasks = initialTasks();

export function resetMockWorkflow(): void {
  readiness = initialReadiness();
  tasks = initialTasks();
}

const notFound = () =>
  HttpResponse.json({ code: 'NOT_FOUND', message: 'Not found' }, { status: 404 });

export const workflowHandlers = [
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
