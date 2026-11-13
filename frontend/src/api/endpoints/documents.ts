import { apiUrl, get, patch } from '../client';
import type { DocumentMetadata, MetadataPatch, PatientDocument } from '../types';

const path = (id: string) => `/documents/${encodeURIComponent(id)}`;

export const documentsApi = {
  listForPatient: (patientId: string) =>
    get<PatientDocument[]>(`/patients/${encodeURIComponent(patientId)}/documents`),
  metadata: (id: string) => get<DocumentMetadata>(`${path(id)}/metadata`),
  updateMetadata: (id: string, body: MetadataPatch) =>
    patch<DocumentMetadata, MetadataPatch>(`${path(id)}/metadata`, body),
  /** URL of the file bytes, for an <img> or <iframe>. */
  contentUrl: (id: string) => apiUrl(`${path(id)}/content`),
};
