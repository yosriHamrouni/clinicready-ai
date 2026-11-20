import { strings } from '../../lib/strings';

export function requirementLabel(code: string): string {
  return strings.documentCategories[code as keyof typeof strings.documentCategories] ?? code;
}
