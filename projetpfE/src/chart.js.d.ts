/** Déclarations locales pour la résolution TypeScript (package chart.js requis à l'exécution). */
declare module 'chart.js' {
  export type ChartConfiguration<TType extends string = string> = {
    type: TType;
    data?: unknown;
    options?: unknown;
  };

  export const registerables: readonly unknown[];

  export class Chart {
    constructor(item: HTMLCanvasElement, config: unknown);
    destroy(): void;
    static register(...plugins: unknown[]): void;
  }
}
