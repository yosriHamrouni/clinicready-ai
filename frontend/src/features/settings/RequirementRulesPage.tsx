import { useEffect, useId, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CalendarX, Lock, Plus, Trash2 } from 'lucide-react';
import { ApiError } from '../../api/client';
import { requirementsApi } from '../../api/endpoints/requirements';
import {
  DOCUMENT_CATEGORIES,
  type DocumentCategory,
  type RequirementRule,
  type RequirementRuleInput,
} from '../../api/types';
import { useAuth } from '../../auth/useAuth';
import { EmptyState } from '../../components/EmptyState';
import { LoadingSkeleton } from '../../components/LoadingSkeleton';
import { PageHeader } from '../../components/PageHeader';
import { Button } from '../../components/ui/button';
import { Card, CardContent } from '../../components/ui/card';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { strings } from '../../lib/strings';

const t = strings.requirements;
const MAX_VALIDITY_DAYS = 3650;
const selectClass =
  'flex h-11 w-full rounded-md border border-input bg-card px-3 text-base text-foreground md:text-sm';

/** Form state: validity is kept as typed text until submit. */
interface RuleDraft {
  documentCategory: DocumentCategory;
  mandatory: boolean;
  validityDays: string;
  needsVerification: boolean;
}

function toDraft(rule: RequirementRuleInput): RuleDraft {
  return {
    documentCategory: rule.documentCategory,
    mandatory: rule.mandatory,
    validityDays: rule.validityDays == null ? '' : String(rule.validityDays),
    needsVerification: rule.needsVerification,
  };
}

/** null when the validity text is not empty and not an integer in 1..3650. */
function toInput(draft: RuleDraft): RequirementRuleInput | null {
  const text = draft.validityDays.trim();
  let validityDays: number | null = null;
  if (text !== '') {
    validityDays = Number(text);
    if (!Number.isInteger(validityDays) || validityDays < 1 || validityDays > MAX_VALIDITY_DAYS) {
      return null;
    }
  }
  return { ...draft, validityDays };
}

function errorMessage(error: unknown): string {
  return error instanceof ApiError && error.code === 'RULE_CONFLICT' ? t.conflict : t.saveError;
}

const rulesKey = (typeId: string) => ['requirements', typeId] as const;

export function RequirementRulesPage() {
  const { hasRole } = useAuth();
  const isManager = hasRole('CLINIC_MANAGER');

  return (
    <div className="space-y-6">
      <PageHeader title={t.title} description={t.description} />
      {isManager ? (
        <RulesEditor />
      ) : (
        <EmptyState icon={Lock} title={t.forbiddenTitle} description={t.forbiddenDescription} />
      )}
    </div>
  );
}

function RulesEditor() {
  const selectId = useId();
  const types = useQuery({
    queryKey: ['appointment-types'],
    queryFn: requirementsApi.appointmentTypes,
  });
  const [typeId, setTypeId] = useState<string>('');

  useEffect(() => {
    if (!typeId && types.data?.length) setTypeId(types.data[0]!.id);
  }, [types.data, typeId]);

  if (types.isPending) return <LoadingSkeleton />;
  if (types.isError)
    return (
      <p role="alert" className="text-sm text-destructive">
        {t.loadError}
      </p>
    );
  if (types.data.length === 0) {
    return (
      <EmptyState icon={CalendarX} title={t.noTypesTitle} description={t.noTypesDescription} />
    );
  }

  return (
    <div className="space-y-6">
      <div className="max-w-sm space-y-2">
        <Label htmlFor={selectId}>{t.appointmentType}</Label>
        <select
          id={selectId}
          className={selectClass}
          value={typeId}
          onChange={(e) => setTypeId(e.target.value)}
        >
          {types.data.map((type) => (
            <option key={type.id} value={type.id}>
              {type.label}
            </option>
          ))}
        </select>
      </div>
      {typeId && <RulesForType key={typeId} typeId={typeId} />}
    </div>
  );
}

function RulesForType({ typeId }: { typeId: string }) {
  const rules = useQuery({
    queryKey: rulesKey(typeId),
    queryFn: () => requirementsApi.list(typeId),
  });

  if (rules.isPending) return <LoadingSkeleton />;
  if (rules.isError)
    return (
      <p role="alert" className="text-sm text-destructive">
        {t.loadError}
      </p>
    );

  const used = new Set(rules.data.map((r) => r.documentCategory));
  return (
    <div className="space-y-4">
      {rules.data.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t.noRules}</p>
      ) : (
        <ul className="space-y-3">
          {rules.data.map((rule) => (
            <li key={rule.id}>
              <RuleRow rule={rule} usedCategories={used} />
            </li>
          ))}
        </ul>
      )}
      <AddRule typeId={typeId} usedCategories={used} />
    </div>
  );
}

function RuleRow({
  rule,
  usedCategories,
}: {
  rule: RequirementRule;
  usedCategories: Set<DocumentCategory>;
}) {
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState<RuleDraft>(() => toDraft(rule));
  const [saved, setSaved] = useState(false);
  const input = toInput(draft);
  const dirty = JSON.stringify(input) !== JSON.stringify(toInput(toDraft(rule)));
  const refresh = () =>
    queryClient.invalidateQueries({ queryKey: rulesKey(rule.appointmentTypeId) });

  const save = useMutation({
    mutationFn: (body: RequirementRuleInput) => requirementsApi.update(rule.id, body),
    onSuccess: async () => {
      setSaved(true);
      await refresh();
    },
  });
  const remove = useMutation({
    mutationFn: () => requirementsApi.remove(rule.id),
    onSuccess: refresh,
  });

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (input && dirty) save.mutate(input);
  };
  const categories = DOCUMENT_CATEGORIES.filter(
    (c) => c === rule.documentCategory || !usedCategories.has(c),
  );
  const error = save.error ?? remove.error;

  return (
    <Card>
      <CardContent className="pt-4 md:pt-6">
        <form
          onSubmit={onSubmit}
          aria-label={t.ruleFor(strings.documentCategories[rule.documentCategory])}
          className="space-y-4"
        >
          <RuleFields
            draft={draft}
            categories={categories}
            onChange={(next) => {
              setSaved(false);
              setDraft(next);
            }}
          />
          <div className="flex flex-wrap items-center gap-3">
            <Button type="submit" disabled={!input || !dirty || save.isPending}>
              {save.isPending ? t.saving : t.save}
            </Button>
            <Button
              type="button"
              variant="outline"
              onClick={() => remove.mutate()}
              disabled={remove.isPending}
            >
              <Trash2 className="h-4 w-4" aria-hidden="true" />
              {t.remove}
            </Button>
            {saved && !dirty && (
              <span role="status" className="text-sm text-muted-foreground">
                {t.saved}
              </span>
            )}
            {error && (
              <span role="alert" className="text-sm text-destructive">
                {errorMessage(error)}
              </span>
            )}
          </div>
        </form>
      </CardContent>
    </Card>
  );
}

function AddRule({
  typeId,
  usedCategories,
}: {
  typeId: string;
  usedCategories: Set<DocumentCategory>;
}) {
  const queryClient = useQueryClient();
  const available = DOCUMENT_CATEGORIES.filter((c) => !usedCategories.has(c));
  const empty = (): RuleDraft => ({
    documentCategory: available[0] ?? 'OTHER',
    mandatory: true,
    validityDays: '',
    needsVerification: false,
  });
  const [draft, setDraft] = useState<RuleDraft>(empty);
  const input = toInput(draft);

  // Keep the selected category valid once a rule for it exists.
  const firstAvailable = available[0];
  useEffect(() => {
    if (firstAvailable && usedCategories.has(draft.documentCategory)) {
      setDraft((d) => ({ ...d, documentCategory: firstAvailable }));
    }
  }, [firstAvailable, usedCategories, draft.documentCategory]);

  const create = useMutation({
    mutationFn: (body: RequirementRuleInput) => requirementsApi.create(typeId, body),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: rulesKey(typeId) });
      setDraft((d) => ({ ...d, validityDays: '', mandatory: true, needsVerification: false }));
    },
  });

  if (available.length === 0) {
    return <p className="text-sm text-muted-foreground">{t.allCategoriesUsed}</p>;
  }

  return (
    <Card>
      <CardContent className="pt-4 md:pt-6">
        <form
          aria-label={t.addTitle}
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            if (input) create.mutate(input);
          }}
        >
          <h2 className="text-base">{t.addTitle}</h2>
          <RuleFields draft={draft} categories={available} onChange={setDraft} />
          <div className="flex flex-wrap items-center gap-3">
            <Button type="submit" disabled={!input || create.isPending}>
              <Plus className="h-4 w-4" aria-hidden="true" />
              {t.add}
            </Button>
            {create.error && (
              <span role="alert" className="text-sm text-destructive">
                {errorMessage(create.error)}
              </span>
            )}
          </div>
        </form>
      </CardContent>
    </Card>
  );
}

function RuleFields({
  draft,
  categories,
  onChange,
}: {
  draft: RuleDraft;
  categories: DocumentCategory[];
  onChange: (draft: RuleDraft) => void;
}) {
  const id = useId();
  const validityInvalid = toInput(draft) === null;

  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4 lg:items-end">
      <div className="space-y-2">
        <Label htmlFor={`${id}-category`}>{t.category}</Label>
        <select
          id={`${id}-category`}
          className={selectClass}
          value={draft.documentCategory}
          onChange={(e) =>
            onChange({ ...draft, documentCategory: e.target.value as DocumentCategory })
          }
        >
          {categories.map((c) => (
            <option key={c} value={c}>
              {strings.documentCategories[c]}
            </option>
          ))}
        </select>
      </div>
      <div className="space-y-2">
        <Label htmlFor={`${id}-validity`}>{t.validityDays}</Label>
        <Input
          id={`${id}-validity`}
          type="number"
          inputMode="numeric"
          min={1}
          max={MAX_VALIDITY_DAYS}
          value={draft.validityDays}
          aria-invalid={validityInvalid}
          aria-describedby={`${id}-validity-hint`}
          onChange={(e) => onChange({ ...draft, validityDays: e.target.value })}
        />
        <p
          id={`${id}-validity-hint`}
          className={validityInvalid ? 'text-xs text-destructive' : 'text-xs text-muted-foreground'}
        >
          {validityInvalid ? t.validityInvalid : t.validityHint}
        </p>
      </div>
      <Checkbox
        id={`${id}-mandatory`}
        label={t.mandatory}
        checked={draft.mandatory}
        onChange={(mandatory) => onChange({ ...draft, mandatory })}
      />
      <Checkbox
        id={`${id}-verification`}
        label={t.needsVerification}
        checked={draft.needsVerification}
        onChange={(needsVerification) => onChange({ ...draft, needsVerification })}
      />
    </div>
  );
}

function Checkbox({
  id,
  label,
  checked,
  onChange,
}: {
  id: string;
  label: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
}) {
  return (
    <div className="flex h-11 items-center gap-2">
      <input
        id={id}
        type="checkbox"
        className="h-5 w-5 rounded border-input accent-primary"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
      />
      <Label htmlFor={id}>{label}</Label>
    </div>
  );
}
