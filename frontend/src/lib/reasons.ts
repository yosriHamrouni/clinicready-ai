import { strings } from './strings';

/**
 * The API explains readiness in English (the engine's reasons, suggested actions, run stop reasons).
 * The interface is French, so these helpers translate the known sentences and return anything else
 * unchanged: a new backend sentence shows up in English rather than disappearing.
 *
 * Document categories stay codes in the API ("ID_DOCUMENT or INSURANCE_CARD"); here they become labels.
 */

const CATS = '([A-Z_]+(?: or [A-Z_]+)*)';

const SOURCES: Record<string, string> = {
  STAFF_UPLOAD: 'téléversement par le personnel',
  EMAIL: 'e-mail',
  SCAN: 'scan',
  PATIENT_SUBMISSION: 'envoi par le patient',
};

const REVIEW_REASONS: Record<string, string> = {
  EXTRACTION_FAILED: 'lecture impossible',
  LOW_CONFIDENCE: 'confiance faible',
  CATEGORY_UNKNOWN: 'catégorie inconnue',
};

const DOUBTS: Record<string, string> = {
  'text extraction failed': 'la lecture du texte a échoué',
  'text extraction still pending': 'la lecture du texte est encore en cours',
  'flagged for review by staff': 'signalé pour vérification par le personnel',
  'conflicting dates on the document': 'dates contradictoires sur le document',
  'awaiting staff verification': 'en attente de vérification par le personnel',
};

const FIXED: Record<string, string> = {
  'Expected examination list not entered yet': "La liste des examens attendus n'est pas encore saisie",
  'No examination expected': 'Aucun examen attendu',
  'Expected examination list entered': 'Liste des examens attendus saisie',
  'Advance amount not entered yet': "Le montant de l'avance n'est pas encore saisi",
  'No advance required (amount 0)': 'Aucune avance requise (montant 0)',
  'Advance payment confirmed by staff': "Avance confirmée par le personnel",
  'Advance payment confirmation pending': "Confirmation de l'avance en attente",
  'Enter the expected examination list': 'Saisir la liste des examens attendus',
  'Enter the advance amount': "Saisir le montant de l'avance",
  'Confirm the advance payment once received': "Confirmer l'avance une fois reçue",
  'Administrative completeness check only. It is not a medical decision and does not guarantee coverage or payment.':
    "Vérification administrative uniquement. Ce n'est pas une décision médicale et ne garantit ni la prise en charge ni le paiement.",
  'No model is configured': "Aucun modèle n'est configuré",
  'The final report is not valid JSON matching the report schema':
    "Le rapport final n'est pas valide : il ne respecte pas le format attendu",
  'Interrupted by a server restart': "Interrompue par un redémarrage du serveur",
  'Too many runs in progress; try again shortly':
    'Trop d’analyses en cours : réessayez dans un instant',
};

const label = (code: string) =>
  strings.documentCategories[code as keyof typeof strings.documentCategories] ?? code;

/** "ID_DOCUMENT or INSURANCE_CARD" becomes « Pièce d'identité » ou « Carte d'assurance ». */
const categories = (joined: string) =>
  joined
    .split(' or ')
    .map((c) => `« ${label(c)} »`)
    .join(' ou ');

const frenchDate = (iso: string) => iso.split('-').reverse().join('/');

function doubt(text: string): string {
  const flagged = /^AI extraction flagged it \((.+)\)$/.exec(text);
  if (flagged) {
    return `l'extraction par l'IA l'a signalé (${REVIEW_REASONS[flagged[1]] ?? flagged[1]})`;
  }
  return DOUBTS[text] ?? text;
}

type Rule = [RegExp, (m: RegExpExecArray) => string];

const RULES: Rule[] = [
  [
    new RegExp(`^No ${CATS} document found in the sources checked: (.+)$`),
    (m) =>
      `Aucun document ${categories(m[1])} trouvé dans les sources vérifiées : ${m[2]
        .split(', ')
        .map((s) => SOURCES[s] ?? s)
        .join(', ')}`,
  ],
  [
    new RegExp(`^Every ${CATS} document was rejected by staff \\(confirmed non-compliance\\)$`),
    (m) =>
      `Tous les documents ${categories(m[1])} ont été rejetés par le personnel (non-conformité confirmée)`,
  ],
  [new RegExp(`^${CATS} document found$`), (m) => `Document ${categories(m[1])} trouvé`],
  [
    new RegExp(`^${CATS} document found but (.+)$`),
    (m) => `Document ${categories(m[1])} trouvé, mais : ${m[2].split('; ').map(doubt).join(' ; ')}`,
  ],
  [
    new RegExp(`^${CATS} document expired before the appointment date (\\d{4}-\\d{2}-\\d{2})$`),
    (m) => `Document ${categories(m[1])} expiré avant la date du rendez-vous (${frenchDate(m[2])})`,
  ],
  [
    new RegExp(`^Obtain and upload a ${CATS} document$`),
    (m) => `Obtenir et téléverser un document ${categories(m[1])}`,
  ],
  [new RegExp(`^Obtain a valid ${CATS} document$`), (m) => `Obtenir un document ${categories(m[1])} valide`],
  [
    new RegExp(`^Review the ${CATS} document, then verify or correct it$`),
    (m) => `Vérifier le document ${categories(m[1])}, puis le valider ou le corriger`,
  ],
  [
    /^Stopped: the run asked for more than (\d+) tool calls$/,
    (m) => `Arrêtée : l'analyse a demandé plus de ${m[1]} appels d'outils`,
  ],
  [/^Stopped: the run took longer than .+$/, () => "Arrêtée : l'analyse a dépassé le délai autorisé"],
  [
    /^Stopped: tool (\w+) failed.*$/,
    (m) => `Arrêtée : l'outil ${m[1]} a échoué et l'analyse n'a pas continué`,
  ],
  [
    /^The model call failed \(.+\); nothing was changed after it$/,
    () => "L'appel au modèle a échoué ; rien n'a été modifié après cet échec",
  ],
];

/** French text for a reason or action sentence from the API; the original when it is not a known one. */
export function frenchReason(text: string): string {
  if (FIXED[text]) return FIXED[text];
  for (const [pattern, build] of RULES) {
    const match = pattern.exec(text);
    if (match) return build(match);
  }
  return text;
}
