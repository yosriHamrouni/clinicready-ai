import { frenchReason } from '../lib/reasons';

describe('frenchReason', () => {
  it('translates a missing document with the sources checked and category labels', () => {
    expect(
      frenchReason(
        'No ID_DOCUMENT or INSURANCE_CARD document found in the sources checked: STAFF_UPLOAD, EMAIL, SCAN, PATIENT_SUBMISSION',
      ),
    ).toBe(
      "Aucun document « Pièce d'identité » ou « Carte d'assurance » trouvé dans les sources vérifiées : " +
        'téléversement par le personnel, e-mail, scan, envoi par le patient',
    );
  });

  it('translates found, uncertain, rejected and expired documents', () => {
    expect(frenchReason('CONSENT_FORM document found')).toBe(
      'Document « Formulaire de consentement » trouvé',
    );
    expect(
      frenchReason(
        'REFERRAL_LETTER document found but text extraction failed; AI extraction flagged it (LOW_CONFIDENCE)',
      ),
    ).toBe(
      "Document « Lettre d'orientation » trouvé, mais : la lecture du texte a échoué ; " +
        "l'extraction par l'IA l'a signalé (confiance faible)",
    );
    expect(
      frenchReason('PRESCRIPTION document expired before the appointment date 2026-11-24'),
    ).toBe('Document « Ordonnance » expiré avant la date du rendez-vous (24/11/2026)');
    expect(
      frenchReason('Every LAB_RESULT document was rejected by staff (confirmed non-compliance)'),
    ).toBe(
      "Tous les documents « Résultat d'analyse » ont été rejetés par le personnel (non-conformité confirmée)",
    );
  });

  it('translates suggested actions and fixed sentences', () => {
    expect(frenchReason('Obtain and upload a CONSENT_FORM document')).toBe(
      'Obtenir et téléverser un document « Formulaire de consentement »',
    );
    expect(frenchReason('Enter the advance amount')).toBe("Saisir le montant de l'avance");
    expect(frenchReason('Stopped: the run asked for more than 15 tool calls')).toBe(
      "Arrêtée : l'analyse a demandé plus de 15 appels d'outils",
    );
  });

  it('leaves text it does not know unchanged, so nothing is hidden', () => {
    expect(frenchReason('Something the backend added later')).toBe('Something the backend added later');
    expect(frenchReason('Déjà en français')).toBe('Déjà en français');
  });
});
