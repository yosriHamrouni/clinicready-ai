import { get, post } from '../client';
import type { CurrentUser, LoginRequest, LoginResponse } from '../types';

export const authApi = {
  login: (body: LoginRequest) => post<LoginResponse, LoginRequest>('/auth/login', body),
  me: () => get<CurrentUser>('/auth/me'),
  logout: () => post<void>('/auth/logout'),
};
