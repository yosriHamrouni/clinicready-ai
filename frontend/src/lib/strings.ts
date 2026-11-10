import type { Role } from '../api/types';

/** All UI strings (French), centralized for review and future i18n. */
export const strings = {
  app: { name: 'ClinicReady', tagline: 'Préparez chaque dossier patient avant le rendez-vous' },
  a11y: {
    skipToContent: 'Aller au contenu principal',
    mainNav: 'Navigation principale',
    openMenu: 'Ouvrir le menu',
    closeMenu: 'Fermer le menu',
    collapseSidebar: 'Réduire le menu latéral',
    expandSidebar: 'Développer le menu latéral',
    userMenu: 'Menu utilisateur',
    loading: 'Chargement…',
  },
  nav: {
    dashboard: 'Tableau de bord',
    patients: 'Patients',
    agent: 'Espace agent',
    followUps: 'Suivis',
  },
  header: { search: 'Rechercher', searchSoon: 'Bientôt disponible' },
  user: {
    clinic: 'Clinique',
    logout: 'Se déconnecter',
    roles: {
      RECEPTIONIST: 'Réceptionniste',
      PRACTITIONER: 'Praticien',
      CLINIC_MANAGER: 'Responsable de clinique',
    } satisfies Record<Role, string>,
  },
  status: {
    PRESENT: 'Présent',
    MISSING: 'Manquant',
    NEEDS_REVIEW: 'À vérifier',
    EXPIRED: 'Expiré',
    NOT_APPLICABLE: 'Non applicable',
  },
  dashboard: {
    title: 'Tableau de bord',
    description: "Vue d'ensemble de la préparation des dossiers avant rendez-vous.",
    cta: 'Préparer les rendez-vous',
    kpis: {
      upcoming: 'Rendez-vous à venir',
      ready: 'Dossiers prêts',
      incomplete: 'Dossiers incomplets',
      pendingFollowUps: 'Suivis en attente',
    },
    kpiPlaceholder: 'Bientôt disponible',
    demoTitle: 'Aperçu des statuts (développement)',
  },
  patients: {
    title: 'Patients',
    description: 'Dossiers patients, rendez-vous et documents.',
    emptyTitle: 'Aucun patient pour le moment',
    emptyDescription:
      'Le module Patients permettra de consulter les dossiers et leur état de préparation.',
  },
  agent: {
    title: 'Espace agent',
    description: 'Assistant administratif : analyses et actions proposées.',
    emptyTitle: 'Aucune analyse en cours',
    emptyDescription: "L'assistant proposera ici des actions que vous pourrez valider ou refuser.",
  },
  followUps: {
    title: 'Suivis',
    description: 'Relances, brouillons de messages et approbations.',
    emptyTitle: 'Aucun suivi en attente',
    emptyDescription: 'Les relances à envoyer aux patients apparaîtront ici pour validation.',
  },
  notFound: {
    title: 'Page introuvable',
    description: "La page que vous cherchez n'existe pas ou a été déplacée.",
    back: 'Retour au tableau de bord',
  },
  login: {
    heading: 'Connexion',
    subheading: 'Accédez à votre espace clinique',
    email: 'E-mail',
    password: 'Mot de passe',
    submit: 'Se connecter',
    pending: 'Connexion…',
    showPassword: 'Afficher le mot de passe',
    hidePassword: 'Masquer le mot de passe',
    capsLock: 'Verr. Maj activé',
    invalidCredentials: 'Identifiants invalides',
    serviceError: 'Service momentanément indisponible. Veuillez réessayer.',
    emailRequired: "L'e-mail est requis",
    emailInvalid: "Format d'e-mail invalide",
    passwordRequired: 'Le mot de passe est requis',
    footer: 'Données de démonstration synthétiques uniquement.',
    values: [
      { title: 'Dossiers complets', text: 'Repérez les documents manquants avant le jour J.' },
      { title: 'Relances guidées', text: 'Des brouillons à valider, jamais envoyés sans vous.' },
      { title: 'Données protégées', text: 'Accès par rôle, traçabilité de chaque action.' },
    ],
  },
} as const;
