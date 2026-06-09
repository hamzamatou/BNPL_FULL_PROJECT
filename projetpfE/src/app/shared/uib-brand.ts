/** Chemins logo UIB servis sous /assets/… */
export const UIB_LOGO_CANDIDATES = [
  '/assets/uib-logo.png',
  '/assets/images/uib-logo.png',
  '/assets/uib-logo.jpg',
  '/assets/images/uib-logo.jpg',
  '/assets/images/logo.png',
];

export function resolveUibLogo(): Promise<string> {
  return (async () => {
    for (const src of UIB_LOGO_CANDIDATES) {
      if (await uibLogoExists(src)) return src;
    }
    return UIB_LOGO_CANDIDATES[0];
  })();
}

function uibLogoExists(src: string): Promise<boolean> {
  return new Promise((resolve) => {
    const img = new Image();
    img.onload = () => resolve(true);
    img.onerror = () => resolve(false);
    img.src = src;
  });
}
