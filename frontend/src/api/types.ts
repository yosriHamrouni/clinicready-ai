export type Role = 'RECEPTIONIST' | 'PRACTITIONER' | 'CLINIC_MANAGER';

export interface CurrentUser {
  id: string;
  email: string;
  fullName: string;
  clinicId: string;
  roles: Role[];
}

export interface LoginRequest {
  email: string;
  password: string;
}

/**
 * ASSUMPTION (to confirm with backend): the session is carried by an httpOnly cookie,
 * so the response only needs `user`. `accessToken` is optional: if the backend returns a
 * JWT instead, the client keeps it in memory and sends it as a Bearer header.
 */
export interface LoginResponse {
  accessToken?: string;
  user: CurrentUser;
}

/** Error body shape we expect from the backend (all fields optional on parse). */
export interface ApiErrorBody {
  code?: string;
  message?: string;
  details?: unknown;
}
