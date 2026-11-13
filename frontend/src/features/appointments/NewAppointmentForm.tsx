import { useId, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { appointmentsApi } from '../../api/endpoints/appointments';
import { patientsApi } from '../../api/endpoints/patients';
import { requirementsApi } from '../../api/endpoints/requirements';
import type { NewAppointmentInput } from '../../api/types';
import { Button } from '../../components/ui/button';
import { Card, CardContent } from '../../components/ui/card';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { toDateTimeInput } from '../../lib/dates';
import { strings } from '../../lib/strings';
import { selectClass } from './styles';

const t = strings.appointments;

export function NewAppointmentForm({
  defaultDate,
  defaultPatientId = '',
  onDone,
  onCancel,
}: {
  defaultDate: Date;
  defaultPatientId?: string;
  onDone: () => void;
  onCancel: () => void;
}) {
  const id = useId();
  const queryClient = useQueryClient();
  const patients = useQuery({ queryKey: ['patients', ''], queryFn: () => patientsApi.list() });
  const practitioners = useQuery({
    queryKey: ['practitioners'],
    queryFn: appointmentsApi.practitioners,
  });
  const types = useQuery({
    queryKey: ['appointment-types'],
    queryFn: requirementsApi.appointmentTypes,
  });
  const nineAm = new Date(defaultDate);
  nineAm.setHours(9, 0, 0, 0);
  const [form, setForm] = useState({
    patientId: defaultPatientId,
    practitionerId: '',
    appointmentTypeId: '',
    startsAt: toDateTimeInput(nineAm),
  });

  const create = useMutation({
    mutationFn: (body: NewAppointmentInput) => appointmentsApi.create(body),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['appointments'] });
      onDone();
    },
  });

  const complete = form.patientId && form.practitionerId && form.appointmentTypeId && form.startsAt;
  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!complete) return;
    create.mutate({ ...form, startsAt: new Date(form.startsAt).toISOString() });
  };

  const select = (
    key: 'patientId' | 'practitionerId' | 'appointmentTypeId',
    label: string,
    options: { id: string; label: string }[] | undefined,
  ) => (
    <div className="space-y-2">
      <Label htmlFor={`${id}-${key}`}>{label}</Label>
      <select
        id={`${id}-${key}`}
        className={selectClass}
        required
        value={form[key]}
        onChange={(e) => setForm({ ...form, [key]: e.target.value })}
      >
        <option value="">{t.choose}</option>
        {options?.map((o) => (
          <option key={o.id} value={o.id}>
            {o.label}
          </option>
        ))}
      </select>
    </div>
  );

  return (
    <Card>
      <CardContent className="pt-4 md:pt-6">
        <form aria-label={t.addTitle} onSubmit={onSubmit} className="space-y-4">
          <h2 className="text-base">{t.addTitle}</h2>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {select(
              'patientId',
              t.patient,
              patients.data?.map((p) => ({ id: p.id, label: `${p.fullName} (${p.patientRef})` })),
            )}
            {select(
              'practitionerId',
              t.practitioner,
              practitioners.data?.map((p) => ({ id: p.id, label: p.fullName })),
            )}
            {select(
              'appointmentTypeId',
              t.type,
              types.data?.map((ty) => ({ id: ty.id, label: ty.label })),
            )}
            <div className="space-y-2">
              <Label htmlFor={`${id}-startsAt`}>{t.startsAt}</Label>
              <Input
                id={`${id}-startsAt`}
                type="datetime-local"
                required
                value={form.startsAt}
                onChange={(e) => setForm({ ...form, startsAt: e.target.value })}
              />
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <Button type="submit" disabled={!complete || create.isPending}>
              {create.isPending ? t.creating : t.create}
            </Button>
            <Button type="button" variant="outline" onClick={onCancel}>
              {t.cancel}
            </Button>
            {create.error && (
              <span role="alert" className="text-sm text-destructive">
                {t.saveError}
              </span>
            )}
          </div>
        </form>
      </CardContent>
    </Card>
  );
}
