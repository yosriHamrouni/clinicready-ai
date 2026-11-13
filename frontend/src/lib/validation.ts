/** Mirrors the backend's format check. A well-formed number is not proof that it is reachable. */
export function isWellFormedPhone(value: string): boolean {
  if (!/^[+]?[0-9 ().-]{8,25}$/.test(value)) return false;
  const digits = value.replace(/\D/g, '').length;
  return digits >= 8 && digits <= 15;
}

export function isWellFormedEmail(value: string): boolean {
  return /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(value);
}
