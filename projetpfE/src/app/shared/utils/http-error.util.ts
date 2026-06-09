export type OtpVerifyErrorKind = 'expired' | 'incorrect' | 'technical' | 'generic';

export const OTP_EXPIRED_MESSAGE =
  'Le code a expiré. Cliquez sur « Renvoyer » pour obtenir un nouveau code.';

export const OTP_INCORRECT_MESSAGE =
  "Le code saisi est incorrect. Corrigez-le avant l'expiration du délai.";

const OTP_ERROR_MESSAGES: Record<string, string> = {
  'OTP invalide': OTP_INCORRECT_MESSAGE,
  'OTP expiré': OTP_EXPIRED_MESSAGE,
  'OTP déjà utilisé': 'Ce code a déjà été utilisé. Renvoyez un nouveau code.',
  'Trop de tentatives OTP': 'Trop de tentatives. Renvoyez un nouveau code.',
  'Aucun OTP actif pour ce lien': 'Aucun code actif. Cliquez sur « Renvoyer » pour en recevoir un nouveau.',
  'Code OTP obligatoire': 'Veuillez saisir le code à 6 chiffres.',
  'Le code OTP doit contenir exactement 6 chiffres': 'Le code doit contenir exactement 6 chiffres.',
  'Token invalide': 'Lien invalide ou expiré. Utilisez le lien reçu par e-mail.',
  'Lien expiré': 'Ce lien a expiré. Contactez votre conseiller pour un nouveau lien.',
  'Lien déjà utilisé': 'Ce lien a déjà été utilisé.',
  'Identité client invalide': 'Les informations saisies ne correspondent pas au dossier.',
  'Erreur technique, veuillez réessayer.': 'Une erreur technique est survenue. Veuillez réessayer dans quelques instants.',
  'Erreur technique lors de la vérification. Veuillez réessayer.':
    'Une erreur technique est survenue. Veuillez réessayer dans quelques instants.',
  'Internal Server Error': 'Une erreur technique est survenue. Veuillez réessayer dans quelques instants.',
};

function looksLikeJson(value: string): boolean {
  const trimmed = value.trim();
  return trimmed.startsWith('{') && trimmed.endsWith('}');
}

function humanizeKnownMessage(message: string): string {
  const trimmed = message.trim();
  if (!trimmed) {
    return trimmed;
  }
  return OTP_ERROR_MESSAGES[trimmed] ?? trimmed;
}

function isSpringGenericError(value: string): boolean {
  const v = value.toLowerCase();
  return (
    v === 'internal server error' ||
    v === 'bad request' ||
    v === 'unauthorized' ||
    v === 'forbidden' ||
    v === 'not found'
  );
}

function readRawMessageFromObject(body: Record<string, unknown>): string | null {
  const message = body['message'];
  if (typeof message === 'string' && message.trim() && !looksLikeJson(message)) {
    return message.trim();
  }

  const errorField = body['error'];
  if (typeof errorField === 'string' && errorField.trim() && !isSpringGenericError(errorField)) {
    return errorField.trim();
  }

  return null;
}

function isTechnicalMessage(raw: string): boolean {
  const lower = raw.toLowerCase();
  return (
    lower.includes('technique') ||
    lower.includes('internal server') ||
    lower.includes('accès refusé') ||
    lower.includes('introuvable')
  );
}

export function extractRawHttpErrorMessage(err: unknown): string | null {
  const httpErr = err as { error?: unknown; message?: string };
  const body = httpErr?.error;

  if (typeof body === 'string') {
    if (looksLikeJson(body)) {
      try {
        return readRawMessageFromObject(JSON.parse(body) as Record<string, unknown>);
      } catch {
        return null;
      }
    }
    return body.trim() || null;
  }

  if (body && typeof body === 'object') {
    return readRawMessageFromObject(body as Record<string, unknown>);
  }

  const direct = httpErr?.message;
  if (
    typeof direct === 'string' &&
    direct.trim() &&
    !looksLikeJson(direct) &&
    !direct.startsWith('Http failure response')
  ) {
    return direct.trim();
  }

  return null;
}

const EXPIRED_OTP_RAW = new Set([
  'OTP expiré',
  'OTP déjà utilisé',
  'Trop de tentatives OTP',
  'Aucun OTP actif pour ce lien',
]);

export function classifyOtpVerifyError(err: unknown): {
  kind: OtpVerifyErrorKind;
  message: string;
} {
  const status = (err as { status?: number })?.status;
  const raw = extractRawHttpErrorMessage(err);

  if (raw && EXPIRED_OTP_RAW.has(raw)) {
    return { kind: 'expired', message: OTP_EXPIRED_MESSAGE };
  }
  if (raw === 'OTP invalide') {
    return { kind: 'incorrect', message: OTP_INCORRECT_MESSAGE };
  }

  if (raw) {
    const lower = raw.toLowerCase();
    if (
      raw === 'Token invalide' ||
      raw === 'Lien expiré' ||
      raw === 'Lien déjà utilisé'
    ) {
      return { kind: 'generic', message: humanizeKnownMessage(raw) };
    }
    if (lower.includes('expir') || lower.includes('tentative') || lower.includes('aucun otp actif')) {
      return { kind: 'expired', message: OTP_EXPIRED_MESSAGE };
    }
    if (lower.includes('otp invalide') || lower.includes('incorrect')) {
      return { kind: 'incorrect', message: OTP_INCORRECT_MESSAGE };
    }
    if (lower.includes('6 chiffres') || lower.includes('code otp obligatoire')) {
      return { kind: 'incorrect', message: humanizeKnownMessage(raw) };
    }
    if (!isTechnicalMessage(raw)) {
      return { kind: 'generic', message: humanizeKnownMessage(raw) };
    }
  }

  if (status === 409) {
    return { kind: 'expired', message: OTP_EXPIRED_MESSAGE };
  }
  if (status === 400) {
    return { kind: 'incorrect', message: OTP_INCORRECT_MESSAGE };
  }

  return {
    kind: 'technical',
    message: 'Une erreur technique est survenue. Veuillez réessayer dans quelques instants.',
  };
}

function readMessageFromObject(body: Record<string, unknown>): string | null {
  const raw = readRawMessageFromObject(body);
  return raw ? humanizeKnownMessage(raw) : null;
}

export function extractHttpErrorMessage(err: unknown, fallback: string): string {
  const httpErr = err as {
    error?: unknown;
    message?: string;
    status?: number;
  };

  const body = httpErr?.error;
  if (typeof body === 'string') {
    if (looksLikeJson(body)) {
      try {
        const parsed = JSON.parse(body) as Record<string, unknown>;
        return readMessageFromObject(parsed) ?? fallback;
      } catch {
        return fallback;
      }
    }
    return humanizeKnownMessage(body);
  }

  if (body && typeof body === 'object') {
    const fromObject = readMessageFromObject(body as Record<string, unknown>);
    if (fromObject) {
      return fromObject;
    }
  }

  const direct = httpErr?.message;
  if (
    typeof direct === 'string' &&
    direct.trim() &&
    !looksLikeJson(direct) &&
    !direct.startsWith('Http failure response')
  ) {
    return humanizeKnownMessage(direct);
  }

  if (httpErr?.status === 0) {
    return 'Impossible de contacter le serveur. Vérifiez votre connexion.';
  }

  return fallback;
}
