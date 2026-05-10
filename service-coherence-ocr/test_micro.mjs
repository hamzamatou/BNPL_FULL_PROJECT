#!/usr/bin/env node

/**
 * Test terminal (Node 18+) : GET /health puis POST /coherence/check (multipart).
 */

import fs from "node:fs";
import path from "node:path";
import readline from "node:readline/promises";
import { stdin as input, stdout as output } from "node:process";
import { fileURLToPath } from "node:url";

// ✅🔥 AJOUT IMPORTANT (désactive timeout interne fetch)
import { Agent, setGlobalDispatcher } from "undici";

setGlobalDispatcher(new Agent({
  headersTimeout: 0, // ⛔ pas de timeout headers
  bodyTimeout: 0     // ⛔ pas de timeout body
}));

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const SCENARIOS = ["ok", "missing-doc", "no-loyer-no-devis"];

// ================= ARGUMENTS =================
function parseArgs(argv) {
  const out = {
    baseUrl: "http://localhost:8090",
    scenario: null,
    docsDir: path.join(__dirname, "test-docs"),
    timeoutSec: 600,
    save: "",
    interactiveExplicit: false,
    debug: false,
    nom: null,
    prenom: null,
    cin: null,
    revenu: null,
    montant: null,
    loyer: null,
  };

  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === "--base-url" || a === "-b") out.baseUrl = String(argv[++i] ?? "").replace(/\/$/, "");
    else if (a === "--scenario" || a === "-s") out.scenario = argv[++i] ?? out.scenario;
    else if (a === "--docs-dir" || a === "-d") out.docsDir = path.resolve(argv[++i] ?? out.docsDir);
    else if (a === "--timeout" || a === "-t") out.timeoutSec = Number(argv[++i] ?? out.timeoutSec) || 600;
    else if (a === "--save") out.save = argv[++i] ?? "";
    else if (a === "--interactive" || a === "-i") out.interactiveExplicit = true;
    else if (a === "--debug" || a === "-D") out.debug = true;
    else if (a === "--nom") out.nom = argv[++i] ?? null;
    else if (a === "--prenom") out.prenom = argv[++i] ?? null;
    else if (a === "--cin") out.cin = argv[++i] ?? null;
    else if (a === "--revenu") out.revenu = argv[++i] ?? null;
    else if (a === "--montant") out.montant = argv[++i] ?? null;
    else if (a === "--loyer") out.loyer = argv[++i] ?? null;
    else if (SCENARIOS.includes(a)) out.scenario = a;
  }

  const interactive = out.interactiveExplicit || out.scenario === null;
  return { ...out, interactive };
}

// ================= INPUT =================
async function promptLine(rl, question, def = "") {
  const raw = (await rl.question(`${question} [${def}] `)).trim();
  return raw === "" ? def : raw;
}

async function readInteractivePayload(rl) {
  const nom = await promptLine(rl, "Nom ?", "ALI");
  const prenom = await promptLine(rl, "Prenom ?", "SAMI");
  const cin = await promptLine(rl, "CIN ?", "12345678");
  const revenu = await promptLine(rl, "Revenu ?", "2500");
  const montant = await promptLine(rl, "Montant (devis) ?", "12000");
  const loyer = await promptLine(rl, "Loyer (0 = non) ?", "600");

  const montantNum = Number(montant);
  const loyerNum = Number(loyer);

  const aUnLoyer = Number.isFinite(loyerNum) ? loyerNum > 0 : true;
  const includeLoyer = aUnLoyer;

  // L'API exige `devis` seulement si `montant > 10000`
  const includeDevis = Number.isFinite(montantNum) ? montantNum > 10000 : true;

  const declared = {
    nom,
    prenom,
    cin,
    revenu_mensuel: revenu,
    montant: Number.isFinite(montantNum) ? montantNum : 0,
    aUnLoyer,
    loyer_mensuel: aUnLoyer ? String(loyerNum) : "0",
  };

  return {
    declaredJson: JSON.stringify(declared),
    includeLoyer,
    includeDevis,
    skipFicheM2: false
  };
}

// ================= FILE =================
function assertFile(p) {
  if (!fs.existsSync(p)) throw new Error(`Fichier introuvable: ${p}`);
}

function appendFile(form, field, filePath) {
  assertFile(filePath);
  const buf = fs.readFileSync(filePath);
  form.append(field, new Blob([buf]), path.basename(filePath));
}

function buildFormData(docsDir, payload) {
  const form = new FormData();
  form.append("declared_data", payload.declaredJson);

  const missingDoc = payload.missingDocType ?? null;

  if (missingDoc !== "cin") appendFile(form, "cin", path.join(docsDir, "cin.jpg"));
  if (missingDoc !== "fiche_paie_m1") appendFile(form, "fiche_paie_m1", path.join(docsDir, "pay1.jpg"));
  if (!payload.skipFicheM2 && missingDoc !== "fiche_paie_m2") {
    appendFile(form, "fiche_paie_m2", path.join(docsDir, "pay2.jpg"));
  }
  if (missingDoc !== "fiche_paie_m3") appendFile(form, "fiche_paie_m3", path.join(docsDir, "pay3.jpg"));
  if (missingDoc !== "attestation_travail") {
    appendFile(form, "attestation_travail", path.join(docsDir, "attestation.jpg"));
  }

  if (payload.includeLoyer) {
    if (missingDoc !== "justificatif_loyer") {
      appendFile(form, "justificatif_loyer", path.join(docsDir, "loyer.jpg"));
    }
  }

  if (payload.includeDevis) {
    if (missingDoc !== "devis") {
      appendFile(form, "devis", path.join(docsDir, "devis.jpg"));
    }
  }

  return form;
}

// ================= MAIN =================
async function main() {
  const opts = parseArgs(process.argv.slice(2));

  console.log(`Base URL: ${opts.baseUrl}`);
  console.log(`Timeout logique: ${opts.timeoutSec}s`);

  let payload;
  const shouldUseNonInteractive =
    opts.nom !== null || opts.prenom !== null || opts.cin !== null || opts.revenu !== null;

  if (opts.scenario === "ok") {
    payload = {
      declaredJson: JSON.stringify({
        nom: opts.nom ?? "ALI",
        prenom: opts.prenom ?? "SAMI",
        cin: opts.cin ?? "12345678",
        revenu_mensuel: opts.revenu ?? "2500",
        montant: 12000,
        aUnLoyer: true,
        loyer_mensuel: "600",
      }),
      includeLoyer: true,
      includeDevis: true,
      skipFicheM2: false,
    };
  } else if (opts.scenario === "no-loyer-no-devis") {
    payload = {
      declaredJson: JSON.stringify({
        nom: opts.nom ?? "ALI",
        prenom: opts.prenom ?? "SAMI",
        cin: opts.cin ?? "12345678",
        revenu_mensuel: opts.revenu ?? "2500",
        // On fixe <= 10000 pour ne pas déclencher l'obligation `devis` côté API
        montant: 10000,
        aUnLoyer: false,
        loyer_mensuel: "0",
      }),
      includeLoyer: false,
      includeDevis: false,
      skipFicheM2: false,
    };
  } else if (opts.scenario === "missing-doc") {
    payload = {
      declaredJson: JSON.stringify({
        nom: opts.nom ?? "ALI",
        prenom: opts.prenom ?? "SAMI",
        cin: opts.cin ?? "12345678",
        revenu_mensuel: opts.revenu ?? "2500",
        montant: 12000,
        aUnLoyer: true,
        loyer_mensuel: "600",
      }),
      includeLoyer: true,
      includeDevis: true,
      skipFicheM2: false,
      missingDocType: "attestation_travail",
    };
  } else if (shouldUseNonInteractive) {
    const nom = opts.nom ?? "ALI";
    const prenom = opts.prenom ?? "SAMI";
    const cin = opts.cin ?? "12345678";
    const revenu = opts.revenu ?? "2500";
    const montantNum = opts.montant === null ? 12000 : Number(opts.montant);
    const loyerNum = opts.loyer === null ? 600 : Number(opts.loyer);

    const aUnLoyer = Number.isFinite(loyerNum) ? loyerNum > 0 : true;
    const includeLoyer = aUnLoyer;
    const includeDevis = Number.isFinite(montantNum) ? montantNum > 10000 : true;

    const declared = {
      nom,
      prenom,
      cin,
      revenu_mensuel: revenu,
      montant: Number.isFinite(montantNum) ? montantNum : 0,
      aUnLoyer,
      loyer_mensuel: includeLoyer ? String(loyerNum) : "0",
    };

    payload = {
      declaredJson: JSON.stringify(declared),
      includeLoyer,
      includeDevis,
      skipFicheM2: false,
    };
  } else {
    const rl = readline.createInterface({ input, output });
    try {
      payload = await readInteractivePayload(rl);
    } finally {
      rl.close();
    }
  }

  // ===== HEALTH =====
  console.log("\n=== HEALTH ===");
  const health = await fetch(`${opts.baseUrl}/health`);
  console.log(await health.text());

  // ===== POST =====
  console.log("\n=== POST /coherence/check ===");

  const form = buildFormData(opts.docsDir, payload);
  if (opts.debug) {
    // Le backend prend `debug` soit via query string, soit via form-data.
    form.append("debug", "true");
  }

  const controller = AbortSignal.timeout(opts.timeoutSec * 1000);

  const res = await fetch(`${opts.baseUrl}/coherence/check`, {
    method: "POST",
    body: form,
    signal: controller
  });

  const text = await res.text();

  console.log(`STATUS: ${res.status}`);
  console.log(text);
}

main().catch(err => {
  console.error(err);
});