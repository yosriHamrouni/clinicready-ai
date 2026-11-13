import { useId, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CircleAlert, CircleCheck, CircleDashed, FileText } from 'lucide-react';
import { documentsApi } from '../../api/endpoints/documents';
import {
  METADATA_FIELDS,
  type DocumentMetadata,
  type ExtractionStatus,
  type MetadataFieldKey,
  type MetadataFields,
  type PatientDocument,
} from '../../api/types';
import { LoadingSkeleton } from '../../components/LoadingSkeleton';
import { Badge } from '../../components/ui/badge';
import { Button } from '../../components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '../../components/ui/card';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { formatDateTime } from '../../lib/dates';
import { strings } from '../../lib/strings';
import { cn } from '../../lib/utils';

const t = strings.documents;
const DATE_FIELDS: ReadonlySet<MetadataFieldKey> = new Set(['documentDate', 'expiryDate']);
const MAX_LENGTH = 200;

const STATUS_TONE: Record<PatientDocument['status'], string> = {
  UPLOADED: 'border-slate-200 bg-slate-100 text-slate-700',
  NEEDS_REVIEW: 'border-amber-200 bg-amber-50 text-amber-900',
  VERIFIED: 'border-green-200 bg-green-50 text-green-800',
  REJECTED: 'border-red-200 bg-red-50 text-red-800',
};

// Icon + text: extraction state is never conveyed by colour alone.
const EXTRACTION_ICON: Record<ExtractionStatus, typeof CircleCheck> = {
  PENDING: CircleDashed,
  EXTRACTED: CircleCheck,
  EXTRACTION_FAILED: CircleAlert,
};

/** The patient's uploaded documents with their status, a file preview and correctable fields. */
export function PatientDocuments({ patientId }: { patientId: string }) {
  const documents = useQuery({
    queryKey: ['documents', { patientId }],
    queryFn: () => documentsApi.listForPatient(patientId),
  });
  const [selectedId, setSelectedId] = useState<string | null>(null);

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t.title}</CardTitle>
      </CardHeader>
      <CardContent>
        {documents.isPending ? (
          <LoadingSkeleton />
        ) : documents.isError ? (
          <p role="alert" className="text-sm text-destructive">
            {t.loadError}
          </p>
        ) : documents.data.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t.empty}</p>
        ) : (
          <DocumentsWorkspace
            documents={documents.data}
            selectedId={selectedId ?? documents.data[0]!.id}
            onSelect={setSelectedId}
          />
        )}
      </CardContent>
    </Card>
  );
}

function DocumentsWorkspace({
  documents,
  selectedId,
  onSelect,
}: {
  documents: PatientDocument[];
  selectedId: string;
  onSelect: (id: string) => void;
}) {
  const selected = documents.find((d) => d.id === selectedId) ?? documents[0]!;
  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]">
      <ul aria-label={t.list} className="space-y-2">
        {documents.map((d) => (
          <li key={d.id}>
            <button
              type="button"
              aria-pressed={d.id === selected.id}
              onClick={() => onSelect(d.id)}
              className={cn(
                'flex w-full flex-col gap-2 rounded-md border p-3 text-left text-sm hover:bg-muted',
                d.id === selected.id ? 'border-primary bg-primary-soft' : 'border-border bg-card',
              )}
            >
              <span className="flex items-center gap-2 font-medium">
                <FileText className="h-4 w-4 shrink-0" aria-hidden="true" />
                {strings.documentCategories[d.category]} · {t.version(d.version)}
              </span>
              <span className="break-all text-muted-foreground">{d.originalFilename}</span>
              <span className="text-xs text-muted-foreground">
                {t.uploadedOn(formatDateTime(d.uploadedAt))}
              </span>
              <span className="flex flex-wrap gap-2">
                <Badge className={STATUS_TONE[d.status]}>{t.statuses[d.status]}</Badge>
                <ExtractionBadge status={d.extractionStatus} />
              </span>
            </button>
          </li>
        ))}
      </ul>
      {/* Re-mount when another document is chosen so the form draft starts clean. */}
      <DocumentDetail key={selected.id} document={selected} />
    </div>
  );
}

function ExtractionBadge({ status }: { status: ExtractionStatus }) {
  const Icon = EXTRACTION_ICON[status];
  return (
    <Badge variant="outline">
      <Icon className="h-3.5 w-3.5" aria-hidden="true" />
      {t.extraction[status]}
    </Badge>
  );
}

function DocumentDetail({ document: d }: { document: PatientDocument }) {
  const metadata = useQuery({
    queryKey: ['document-metadata', d.id],
    queryFn: () => documentsApi.metadata(d.id),
  });

  return (
    <div className="space-y-6">
      <Preview document={d} />
      <section aria-labelledby={`meta-${d.id}`} className="space-y-3">
        <h3 id={`meta-${d.id}`} className="text-base font-semibold">
          {t.metadataTitle}
        </h3>
        <p className="text-sm text-muted-foreground">{t.metadataHint}</p>
        {metadata.isPending ? (
          <LoadingSkeleton />
        ) : metadata.isError ? (
          <p role="alert" className="text-sm text-destructive">
            {t.metadataLoadError}
          </p>
        ) : (
          <MetadataForm documentId={d.id} saved={metadata.data} />
        )}
      </section>
    </div>
  );
}

function Preview({ document: d }: { document: PatientDocument }) {
  const url = documentsApi.contentUrl(d.id);
  const title = `${t.preview} : ${d.originalFilename}`;
  return (
    <div className="space-y-2">
      <div className="overflow-hidden rounded-md border border-border bg-muted">
        {d.contentType.startsWith('image/') ? (
          <img src={url} alt={title} className="mx-auto max-h-[28rem] object-contain" />
        ) : d.contentType === 'application/pdf' ? (
          <iframe title={title} src={url} className="h-[28rem] w-full" />
        ) : (
          <p className="p-4 text-sm text-muted-foreground">{t.previewFallback}</p>
        )}
      </div>
      <a
        href={url}
        target="_blank"
        rel="noreferrer"
        className="text-sm text-primary hover:underline"
      >
        {t.openFile}
      </a>
    </div>
  );
}

type Draft = Record<MetadataFieldKey, string>;

const toDraft = (fields: MetadataFields): Draft =>
  Object.fromEntries(METADATA_FIELDS.map((k) => [k, fields[k] ?? ''])) as Draft;

function MetadataForm({ documentId, saved }: { documentId: string; saved: DocumentMetadata }) {
  const id = useId();
  const queryClient = useQueryClient();
  const original = toDraft(saved.fields);
  const [draft, setDraft] = useState<Draft>(original);
  const [justSaved, setJustSaved] = useState(false);

  // Only the fields that differ are sent; an empty value clears the field.
  const changed = METADATA_FIELDS.filter((k) => draft[k].trim() !== original[k]);
  const save = useMutation({
    mutationFn: () =>
      documentsApi.updateMetadata(documentId, {
        fields: Object.fromEntries(changed.map((k) => [k, draft[k].trim() || null])),
      }),
    onSuccess: (updated) => {
      setJustSaved(true);
      queryClient.setQueryData(['document-metadata', documentId], updated);
    },
  });

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (changed.length > 0) save.mutate();
  };

  return (
    <form aria-label={t.metadataTitle} onSubmit={onSubmit} className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2">
        {METADATA_FIELDS.map((key) => (
          <div key={key} className="space-y-2">
            <Label htmlFor={`${id}-${key}`}>{t.fields[key]}</Label>
            <Input
              id={`${id}-${key}`}
              type={DATE_FIELDS.has(key) ? 'date' : 'text'}
              value={draft[key]}
              maxLength={MAX_LENGTH}
              onChange={(e) => {
                setJustSaved(false);
                setDraft((d) => ({ ...d, [key]: e.target.value }));
              }}
            />
          </div>
        ))}
      </div>
      <p className="text-xs text-muted-foreground">
        {saved.updatedAt
          ? t.lastEdit(saved.updatedByName, formatDateTime(saved.updatedAt))
          : t.neverEdited}
      </p>
      <div className="flex flex-wrap items-center gap-3">
        <Button type="submit" disabled={changed.length === 0 || save.isPending}>
          {save.isPending ? t.saving : t.save}
        </Button>
        {justSaved && changed.length === 0 && (
          <span role="status" className="text-sm text-muted-foreground">
            {t.saved}
          </span>
        )}
        {save.isError && (
          <span role="alert" className="text-sm text-destructive">
            {t.saveError}
          </span>
        )}
      </div>
    </form>
  );
}
