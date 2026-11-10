/**
 * Single place that sets document language and direction.
 * Switch to 'ar' / 'rtl' here when Arabic is added; all layout uses logical utilities.
 */
export const LOCALE = { lang: 'fr', dir: 'ltr' } as const;

export function applyDocumentDirection(): void {
  document.documentElement.lang = LOCALE.lang;
  document.documentElement.dir = LOCALE.dir;
}
