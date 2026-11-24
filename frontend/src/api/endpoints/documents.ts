import { apiUrl, get, patch, post } from '../client';
import type {
  DocumentCategory,
  DocumentMetadata,
  DocumentSource,
  MetadataPatch,
  PatientDocument,
} from '../types';

const path = (id: string) => `/documents/${encodeURIComponent(id)}`;

export const documentsApi = {
  listForPatient: (patientId: string) =>
    get<PatientDocument[]>(`/patients/${encodeURIComponent(patientId)}/documents`),
  upload: (patientId: string, file: File, category: DocumentCategory, source: DocumentSource) => {
    const form = new FormData();
    form.append('file', file);
    form.append('category', category);
    form.append('source', source);
    // Uploads can be slow on clinic connections.
    return post<PatientDocument, FormData>(
      `/patients/${encodeURIComponent(patientId)}/documents`,
      form,
      {
        timeoutMs: 60_000,
      },
    );
  },
  verify: (id: string) => post<PatientDocument>(`${path(id)}/verify`),
  reject: (id: string, reason: string) =>
    post<PatientDocument, { reason: string }>(`${path(id)}/reject`, { reason }),
  metadata: (id: string) => get<DocumentMetadata>(`${path(id)}/metadata`),
  updateMetadata: (id: string, body: MetadataPatch) =>
    patch<DocumentMetadata, MetadataPatch>(`${path(id)}/metadata`, body),
  /** URL of the file bytes, for an <img> or <iframe>. */
  contentUrl: (id: string) => apiUrl(`${path(id)}/content`),
};
