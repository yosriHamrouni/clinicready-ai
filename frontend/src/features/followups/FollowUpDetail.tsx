import { useId, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { followUpsApi } from '../../api/endpoints/followUps';
import type { FollowUpTask } from '../../api/types';
import { useAuth } from '../../auth/useAuth';
import { Button } from '../../components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '../../components/ui/card';
import { Label } from '../../components/ui/label';
import { strings } from '../../lib/strings';
import { frenchReason } from '../../lib/reasons';
import { FollowUpStatusBadge } from './badges';
import { requirementLabel } from './labels';

const t = strings.followUps;
const TEXTAREA =
  'w-full rounded-md border border-input bg-card px-3 py-2 text-base text-foreground disabled:cursor-not-allowed disabled:bg-muted md:text-sm';

/** One task: the finding, the message draft, the decisions so far, and the actions the user may take. */
export function FollowUpDetail({ taskId }: { taskId: string }) {
  const { hasRole } = useAuth();
  const canDecide = hasRole('RECEPTIONIST') || hasRole('CLINIC_MANAGER');
  const queryClient = useQueryClient();

  const task = useQuery({
    queryKey: ['follow-up', taskId],
    queryFn: () => followUpsApi.get(taskId),
    retry: (count, error) => !(error instanceof ApiError && error.status === 404) && count < 2,
  });
  const draft = useQuery({
    queryKey: ['follow-up-draft', taskId],
    queryFn: () => followUpsApi.draft(taskId),
  });

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['follow-up', taskId] });
    void queryClient.invalidateQueries({ queryKey: ['follow-up-draft', taskId] });
    void queryClient.invalidateQueries({ queryKey: ['follow-ups'] });
    void queryClient.invalidateQueries({ queryKey: ['readiness'] });
  };

  if (task.isPending) return <p className="text-sm text-muted-foreground">{strings.a11y.loading}</p>;
  if (task.isError) {
    return (
      <p role="alert" className="text-sm text-destructive">
        {t.detailLoadError}
      </p>
    );
  }
  const k = task.data;

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader className="flex-row flex-wrap items-center justify-between gap-2 space-y-0">
          <CardTitle>{requirementLabel(k.requirementCode)}</CardTitle>
          <FollowUpStatusBadge status={k.status} />
        </CardHeader>
        <CardContent className="space-y-3 text-sm">
          <div>
            <h3 className="font-semibold">{t.reason}</h3>
            <p>{frenchReason(k.reason)}</p>
          </div>
          <div>
            <h3 className="font-semibold">{t.suggestedAction}</h3>
            <p>{frenchReason(k.suggestedAction)}</p>
          </div>
          <div className="flex flex-wrap gap-x-4 gap-y-1">
            {k.runId && <span className="text-muted-foreground">{t.proposedByAgent}</span>}
            <Link to={`/appointments/${k.appointmentId}`} className="text-primary hover:underline">
              {t.openAppointment}
            </Link>
          </div>
        </CardContent>
      </Card>

      <DraftCard
        task={k}
        body={draft.data?.body}
        edited={!!draft.data?.editedBy}
        language={draft.data?.language}
        canEdit={canDecide && k.status === 'AWAITING_APPROVAL'}
        onSaved={refresh}
      />

      <Decisions task={k} canDecide={canDecide} onChanged={refresh} />
    </div>
  );
}

function DraftCard(props: {
  task: FollowUpTask;
  body: string | undefined;
  edited: boolean;
  language: string | undefined;
  canEdit: boolean;
  onSaved: () => void;
}) {
  const id = useId();
  const [text, setText] = useState<string | null>(null);
  const value = text ?? props.body ?? '';
  const save = useMutation({
    mutationFn: () => followUpsApi.saveDraft(props.task.id, value),
    onSuccess: () => {
      setText(null);
      props.onSaved();
    },
  });
  const locked = save.error instanceof ApiError && save.error.code === 'DRAFT_LOCKED';

  return (
    <Card>
      <CardHeader className="flex-row flex-wrap items-center justify-between gap-2 space-y-0">
        <CardTitle>{t.draftTitle}</CardTitle>
        {props.language && (
          <span className="text-xs text-muted-foreground">{t.draftLanguage(props.language)}</span>
        )}
      </CardHeader>
      <CardContent className="space-y-3">
        <Label htmlFor={id}>{t.draftLabel}</Label>
        <textarea
          id={id}
          rows={6}
          dir={props.language === 'AR' ? 'rtl' : 'ltr'}
          className={TEXTAREA}
          value={value}
          readOnly={!props.canEdit}
          disabled={!props.canEdit}
          onChange={(e) => setText(e.target.value)}
        />
        {props.edited && <p className="text-xs text-muted-foreground">{t.draftEdited}</p>}
        {props.canEdit ? (
          <div className="flex flex-wrap items-center gap-3">
            <Button
              variant="outline"
              disabled={save.isPending || text === null || value.trim() === ''}
              onClick={() => save.mutate()}
            >
              {save.isPending ? t.saving : t.saveDraft}
            </Button>
            {save.isSuccess && text === null && (
              <span role="status" className="text-sm text-green-800">
                {t.draftSaved}
              </span>
            )}
            {save.isError && (
              <span role="alert" className="text-sm text-destructive">
                {locked ? t.draftLockedError : t.actionError}
              </span>
            )}
            <p className="w-full text-xs text-muted-foreground">{t.draftHint}</p>
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}

function Decisions({
  task,
  canDecide,
  onChanged,
}: {
  task: FollowUpTask;
  canDecide: boolean;
  onChanged: () => void;
}) {
  const commentId = useId();
  const [comment, setComment] = useState('');
  const run = useMutation({
    mutationFn: (action: () => Promise<FollowUpTask>) => action(),
    onSuccess: () => {
      setComment('');
      onChanged();
    },
  });
  const decide = (action: () => Promise<FollowUpTask>) => run.mutate(action);
  const trimmed = comment.trim();

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t.approvals}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {task.approvals.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t.noDecisions}</p>
        ) : (
          <ul className="space-y-2 text-sm">
            {task.approvals.map((a) => (
              <li key={a.id} className="rounded-md border border-border px-3 py-2">
                <span className="font-medium">{t.decisions[a.decision]}</span>
                <span className="text-muted-foreground">
                  {' · '}
                  {new Date(a.decidedAt).toLocaleString('fr-FR', {
                    dateStyle: 'short',
                    timeStyle: 'short',
                  })}
                </span>
                {a.comment && <p className="mt-1">{a.comment}</p>}
              </li>
            ))}
          </ul>
        )}

        {!canDecide && <p className="text-sm text-muted-foreground">{t.readOnly}</p>}

        {canDecide && task.status === 'AWAITING_APPROVAL' && (
          <div className="space-y-3">
            <Label htmlFor={commentId}>{t.commentLabel}</Label>
            <textarea
              id={commentId}
              rows={2}
              className={TEXTAREA}
              value={comment}
              onChange={(e) => setComment(e.target.value)}
            />
            <div className="flex flex-wrap items-center gap-3">
              <Button
                disabled={run.isPending}
                onClick={() => decide(() => followUpsApi.approve(task.id, trimmed || undefined))}
              >
                {t.approve}
              </Button>
              <Button
                variant="outline"
                disabled={run.isPending || trimmed === ''}
                onClick={() => decide(() => followUpsApi.reject(task.id, trimmed))}
              >
                {t.reject}
              </Button>
              {trimmed === '' && (
                <span className="text-xs text-muted-foreground">{t.commentRequired}</span>
              )}
            </div>
          </div>
        )}

        {canDecide && task.status === 'APPROVED' && (
          <div className="space-y-2">
            <p className="text-sm text-muted-foreground">{t.noAutoSend}</p>
            <Button disabled={run.isPending} onClick={() => decide(() => followUpsApi.markDelivered(task.id))}>
              {t.markDelivered}
            </Button>
          </div>
        )}

        {canDecide && task.status === 'DELIVERED' && (
          <Button disabled={run.isPending} onClick={() => decide(() => followUpsApi.resolve(task.id))}>
            {t.resolve}
          </Button>
        )}

        {run.isError && (
          <p role="alert" className="text-sm text-destructive">
            {t.actionError}
          </p>
        )}
      </CardContent>
    </Card>
  );
}
