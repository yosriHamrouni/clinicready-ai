import { useEffect, useId, useState, type FormEvent } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { Plus, Search, Users } from 'lucide-react';
import { ApiError } from '../../api/client';
import { patientsApi } from '../../api/endpoints/patients';
import type { Language, NewPatientInput } from '../../api/types';
import { useAuth } from '../../auth/useAuth';
import { EmptyState } from '../../components/EmptyState';
import { LoadingSkeleton } from '../../components/LoadingSkeleton';
import { PageHeader } from '../../components/PageHeader';
import { Button } from '../../components/ui/button';
import { Card, CardContent } from '../../components/ui/card';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { strings } from '../../lib/strings';
import { isWellFormedPhone } from '../../lib/validation';
import { selectClass } from '../appointments/styles';

const t = strings.patients;
/** Same cap as the backend's PatientService.SEARCH_LIMIT. */
const SEARCH_LIMIT = 200;

function useDebounced<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);
  return debounced;
}

export function PatientsPage() {
  const { hasRole } = useAuth();
  const canWrite = hasRole('RECEPTIONIST') || hasRole('CLINIC_MANAGER');
  const searchId = useId();
  const [search, setSearch] = useState('');
  const [adding, setAdding] = useState(false);
  const q = useDebounced(search.trim(), 250);
  // Keep the previous results on screen while the next search loads.
  const patients = useQuery({
    queryKey: ['patients', q],
    queryFn: () => patientsApi.list(q),
    placeholderData: keepPreviousData,
  });

  return (
    <div className="space-y-6">
      <PageHeader
        title={t.title}
        description={t.description}
        actions={
          canWrite && !adding ? (
            <Button className="w-full sm:w-auto" onClick={() => setAdding(true)}>
              <Plus className="h-4 w-4" aria-hidden="true" />
              {t.add}
            </Button>
          ) : undefined
        }
      />

      {adding && <NewPatientForm onCancel={() => setAdding(false)} />}

      <div className="max-w-md space-y-2">
        <Label htmlFor={searchId}>{t.search}</Label>
        <div className="relative">
          <Search
            className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground"
            aria-hidden="true"
          />
          <Input
            id={searchId}
            type="search"
            className="pl-9"
            placeholder={t.searchPlaceholder}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
      </div>

      {patients.isPending ? (
        <LoadingSkeleton />
      ) : patients.isError ? (
        <p role="alert" className="text-sm text-destructive">
          {t.loadError}
        </p>
      ) : patients.data.length === 0 ? (
        q ? (
          <p className="text-sm text-muted-foreground">{t.noMatch}</p>
        ) : (
          <EmptyState icon={Users} title={t.emptyTitle} description={t.emptyDescription} />
        )
      ) : (
        <>
          <div className="overflow-x-auto rounded-lg border border-border bg-card">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-border text-xs text-muted-foreground">
                <tr>
                  <th className="px-4 py-3 font-medium">{t.ref}</th>
                  <th className="px-4 py-3 font-medium">{t.fullName}</th>
                  <th className="px-4 py-3 font-medium">{t.phone}</th>
                  <th className="px-4 py-3 font-medium">{t.language}</th>
                </tr>
              </thead>
              <tbody>
                {patients.data.map((p) => (
                  <tr key={p.id} className="border-b border-border last:border-0">
                    <td className="whitespace-nowrap px-4 py-3">
                      <Link
                        className="font-semibold text-primary hover:underline"
                        to={`/patients/${p.id}`}
                      >
                        {p.patientRef}
                      </Link>
                    </td>
                    <td className="px-4 py-3">{p.fullName}</td>
                    <td className="whitespace-nowrap px-4 py-3">{p.phone ?? '—'}</td>
                    <td className="px-4 py-3">{strings.languages[p.preferredLanguage]}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {patients.data.length >= SEARCH_LIMIT && (
            <p className="text-xs text-muted-foreground">{t.limitNotice(SEARCH_LIMIT)}</p>
          )}
        </>
      )}
    </div>
  );
}

function NewPatientForm({ onCancel }: { onCancel: () => void }) {
  const id = useId();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [form, setForm] = useState({
    fullName: '',
    phone: '',
    patientRef: '',
    preferredLanguage: 'FR' as Language,
  });
  const phoneInvalid = form.phone.trim() !== '' && !isWellFormedPhone(form.phone.trim());

  const create = useMutation({
    mutationFn: (body: NewPatientInput) => patientsApi.create(body),
    onSuccess: async (patient) => {
      await queryClient.invalidateQueries({ queryKey: ['patients'] });
      navigate(`/patients/${patient.id}`);
    },
  });

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!form.fullName.trim() || phoneInvalid) return;
    create.mutate({
      fullName: form.fullName.trim(),
      phone: form.phone.trim() || undefined,
      patientRef: form.patientRef.trim() || undefined,
      preferredLanguage: form.preferredLanguage,
    });
  };

  return (
    <Card>
      <CardContent className="pt-4 md:pt-6">
        <form aria-label={t.addTitle} onSubmit={onSubmit} className="space-y-4">
          <h2 className="text-base">{t.addTitle}</h2>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor={`${id}-name`}>{t.fullName}</Label>
              <Input
                id={`${id}-name`}
                required
                maxLength={200}
                value={form.fullName}
                onChange={(e) => setForm({ ...form, fullName: e.target.value })}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor={`${id}-phone`}>{t.phone}</Label>
              <Input
                id={`${id}-phone`}
                type="tel"
                value={form.phone}
                aria-invalid={phoneInvalid}
                aria-describedby={`${id}-phone-hint`}
                onChange={(e) => setForm({ ...form, phone: e.target.value })}
              />
              <p
                id={`${id}-phone-hint`}
                className={
                  phoneInvalid ? 'text-xs text-destructive' : 'text-xs text-muted-foreground'
                }
              >
                {phoneInvalid ? t.phoneInvalid : t.phoneHint}
              </p>
            </div>
            <div className="space-y-2">
              <Label htmlFor={`${id}-ref`}>{t.ref}</Label>
              <Input
                id={`${id}-ref`}
                maxLength={20}
                value={form.patientRef}
                aria-describedby={`${id}-ref-hint`}
                onChange={(e) => setForm({ ...form, patientRef: e.target.value })}
              />
              <p id={`${id}-ref-hint`} className="text-xs text-muted-foreground">
                {t.refHint}
              </p>
            </div>
            <div className="space-y-2">
              <Label htmlFor={`${id}-language`}>{t.language}</Label>
              <select
                id={`${id}-language`}
                className={selectClass}
                value={form.preferredLanguage}
                onChange={(e) =>
                  setForm({ ...form, preferredLanguage: e.target.value as Language })
                }
              >
                {(['FR', 'AR'] as const).map((l) => (
                  <option key={l} value={l}>
                    {strings.languages[l]}
                  </option>
                ))}
              </select>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <Button
              type="submit"
              disabled={!form.fullName.trim() || phoneInvalid || create.isPending}
            >
              {create.isPending ? t.creating : t.create}
            </Button>
            <Button type="button" variant="outline" onClick={onCancel}>
              {t.cancel}
            </Button>
            {create.error && (
              <span role="alert" className="text-sm text-destructive">
                {create.error instanceof ApiError && create.error.code === 'PATIENT_REF_CONFLICT'
                  ? t.refConflict
                  : t.saveError}
              </span>
            )}
          </div>
        </form>
      </CardContent>
    </Card>
  );
}
