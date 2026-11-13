import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, Plus } from 'lucide-react';
import { ApiError } from '../../api/client';
import { appointmentsApi } from '../../api/endpoints/appointments';
import { patientsApi } from '../../api/endpoints/patients';
import type { Patient } from '../../api/types';
import { useAuth } from '../../auth/useAuth';
import { LoadingSkeleton } from '../../components/LoadingSkeleton';
import { PageHeader } from '../../components/PageHeader';
import { Button } from '../../components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '../../components/ui/card';
import { formatDateTime, startOfDay } from '../../lib/dates';
import { strings } from '../../lib/strings';
import { AppointmentStatusBadge } from '../appointments/badges';
import { NewAppointmentForm } from '../appointments/NewAppointmentForm';

const t = strings.patients;

export function PatientDetailPage() {
  const { id = '' } = useParams();
  const patient = useQuery({
    queryKey: ['patient', id],
    queryFn: () => patientsApi.get(id),
    retry: (count, error) => !(error instanceof ApiError && error.status === 404) && count < 2,
  });

  return (
    <div className="space-y-6">
      <Link
        to="/patients"
        className="inline-flex items-center gap-1 text-sm text-primary hover:underline"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden="true" />
        {t.back}
      </Link>
      {patient.isPending ? (
        <LoadingSkeleton />
      ) : patient.isError ? (
        <p role="alert" className="text-sm text-destructive">
          {patient.error instanceof ApiError && patient.error.status === 404
            ? t.notFound
            : t.loadError}
        </p>
      ) : (
        <PatientDetail patient={patient.data} />
      )}
    </div>
  );
}

function PatientDetail({ patient }: { patient: Patient }) {
  const { hasRole } = useAuth();
  const canWrite = hasRole('RECEPTIONIST') || hasRole('CLINIC_MANAGER');
  const [adding, setAdding] = useState(false);
  const appointments = useQuery({
    queryKey: ['appointments', { patientId: patient.id }],
    queryFn: () => appointmentsApi.list({ patientId: patient.id }),
  });

  return (
    <>
      <PageHeader
        title={patient.fullName}
        description={patient.patientRef}
        actions={
          canWrite && !adding ? (
            <Button className="w-full sm:w-auto" onClick={() => setAdding(true)}>
              <Plus className="h-4 w-4" aria-hidden="true" />
              {strings.appointments.add}
            </Button>
          ) : undefined
        }
      />
      <dl className="grid gap-4 text-sm sm:grid-cols-2">
        <div>
          <dt className="text-muted-foreground">{t.phone}</dt>
          <dd>{patient.phone ?? '—'}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">{t.language}</dt>
          <dd>{strings.languages[patient.preferredLanguage]}</dd>
        </div>
      </dl>
      {adding && (
        <NewAppointmentForm
          defaultDate={startOfDay(new Date())}
          defaultPatientId={patient.id}
          onDone={() => setAdding(false)}
          onCancel={() => setAdding(false)}
        />
      )}
      <Card>
        <CardHeader>
          <CardTitle>{t.appointments}</CardTitle>
        </CardHeader>
        <CardContent>
          {appointments.isPending ? (
            <LoadingSkeleton />
          ) : appointments.isError ? (
            <p role="alert" className="text-sm text-destructive">
              {strings.appointments.loadError}
            </p>
          ) : appointments.data.length === 0 ? (
            <p className="text-sm text-muted-foreground">{t.noAppointments}</p>
          ) : (
            <ul className="divide-y divide-border">
              {appointments.data.map((a) => (
                <li key={a.id} className="flex flex-wrap items-center justify-between gap-2 py-3">
                  <Link
                    to={`/appointments/${a.id}`}
                    className="text-sm text-primary hover:underline"
                  >
                    {formatDateTime(a.startsAt)} · {a.appointmentType.label} ·{' '}
                    {a.practitioner.fullName}
                  </Link>
                  <AppointmentStatusBadge status={a.status} />
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </>
  );
}
