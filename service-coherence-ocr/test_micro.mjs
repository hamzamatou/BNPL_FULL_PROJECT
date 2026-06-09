#!/usr/bin/env node

/**
 * Tests terminal (Node 18+) : GET /health puis POST /coherence/check (multipart).
 *
 * Scenarios :
 *   ok              — dossier coherent (revenu 2500, loyer 600, montant 12000)
 *   missing-doc     — document manquant
 *   no-loyer-no-devis
 *   revenu-tolerance — revenu declare 2500, extrait ~2500 (OK)
 *   revenu-trop-haut — revenu declare 2400 < extrait 2500 (BLOQUANT)
 *   revenu-trop-bas  — revenu declare 3000, extrait 2500 < 2700 (BLOQUANT)
 *   loyer-diff       — loyer declare 550 != extrait 600 (BLOQUANT)
 *
 * Usage :
 *   node test_micro.mjs ok
 *   node test_micro.mjs --all
 *   node test_micro.mjs --revenu 2500 --loyer 600
 */

import fs from "node:fs";
import path from "node:path";
import readline from "node:readline/promises";
import { stdin as input, stdout as output } from "node:process";
import { fileURLToPath } from "node:url";
import { Agent, setGlobalDispatcher } from "undici";

setGlobalDispatcher(new Agent({
  headersTimeout: 0,
  bodyTimeout: 0,
}));

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const SCENARIOS = [
  "ok",
  "missing-doc",
  "no-loyer-no-devis",
  "revenu-tolerance",
  "revenu-trop-haut",
  "revenu-trop-bas",
  "loyer-diff",
];

// ================= ARGUMENTS =================
function parseArgs(argv) {
  const out = {
    baseUrl: "http://localhost:8090",
    scenario: null,
    runAll: false,
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
    else if (a === "--all" || a === "-a") out.runAll = true;
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

  const interactive = out.interactiveExplicit || (out.scenario === null && !out.runAll);
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
    includeLoyer: aUnLoyer,
    includeDevis,
    skipFicheM2: false,
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

// ================= SCENARIOS =================
function buildScenarioPayload(scenario, opts) {
  const baseDeclared = {
    nom: opts.nom ?? "ALI",
    prenom: opts.prenom ?? "SAMI",
    cin: opts.cin ?? "12345678",
    montant: 12000,
    aUnLoyer: true,
    loyer_mensuel: "600",
  };

  switch (scenario) {
    case "ok":
    case "revenu-tolerance":
      return {
        declaredJson: JSON.stringify({
          ...baseDeclared,
          revenu_mensuel: opts.revenu ?? "2500",
        }),
        includeLoyer: true,
        includeDevis: true,
        skipFicheM2: false,
        expect: {
          status: 200,
          noScoreCoherence: true,
          anomaliesEmpty: true,
        },
      };

    case "revenu-trop-haut":
      return {
        declaredJson: JSON.stringify({
          ...baseDeclared,
          revenu_mensuel: "2400",
        }),
        includeLoyer: true,
        includeDevis: true,
        skipFicheM2: false,
        expect: {
          status: 200,
          noScoreCoherence: true,
          hasAnomalyCode: "COH_REVENU_DIFF",
          anomalyNiveau: "BLOQUANT",
        },
      };

    case "revenu-trop-bas":
      return {
        declaredJson: JSON.stringify({
          ...baseDeclared,
          revenu_mensuel: "3000",
        }),
        includeLoyer: true,
        includeDevis: true,
        skipFicheM2: false,
        expect: {
          status: 200,
          noScoreCoherence: true,
          hasAnomalyCode: "COH_REVENU_DIFF",
          anomalyNiveau: "BLOQUANT",
        },
      };

    case "loyer-diff":
      return {
        declaredJson: JSON.stringify({
          ...baseDeclared,
          revenu_mensuel: opts.revenu ?? "2500",
          loyer_mensuel: "550",
        }),
        includeLoyer: true,
        includeDevis: true,
        skipFicheM2: false,
        expect: {
          status: 200,
          noScoreCoherence: true,
          hasAnomalyCode: "COH_LOYER_DIFF",
          anomalyNiveau: "BLOQUANT",
        },
      };

    case "no-loyer-no-devis":
      return {
        declaredJson: JSON.stringify({
          nom: opts.nom ?? "ALI",
          prenom: opts.prenom ?? "SAMI",
          cin: opts.cin ?? "12345678",
          revenu_mensuel: opts.revenu ?? "2500",
          montant: 10000,
          aUnLoyer: false,
          loyer_mensuel: "0",
        }),
        includeLoyer: false,
        includeDevis: false,
        skipFicheM2: false,
        expect: {
          status: 200,
          noScoreCoherence: true,
          anomaliesEmpty: true,
        },
      };

    case "missing-doc":
      return {
        declaredJson: JSON.stringify({
          ...baseDeclared,
          revenu_mensuel: opts.revenu ?? "2500",
        }),
        includeLoyer: true,
        includeDevis: true,
        skipFicheM2: false,
        missingDocType: "attestation_travail",
        expect: {
          status: 200,
          noScoreCoherence: true,
          hasDocumentsManquants: true,
        },
      };

    default:
      throw new Error(`Scenario inconnu: ${scenario}`);
  }
}

// ================= ASSERTIONS =================
function assertResponse(scenario, status, body, expect) {
  const errors = [];

  if (expect?.status != null && status !== expect.status) {
    errors.push(`status attendu ${expect.status}, recu ${status}`);
  }

  if (expect?.noScoreCoherence && Object.prototype.hasOwnProperty.call(body, "score_coherence")) {
    errors.push("score_coherence encore present dans la reponse (doit etre supprime)");
  }

  const anomalies = body.anomalies ?? [];

  if (expect?.anomaliesEmpty && anomalies.length > 0) {
    errors.push(`anomalies attendues vides, recu ${JSON.stringify(anomalies)}`);
  }

  if (expect?.hasAnomalyCode) {
    const found = anomalies.some((a) => a.code === expect.hasAnomalyCode);
    if (!found) {
      errors.push(`anomalie ${expect.hasAnomalyCode} attendue, recu ${JSON.stringify(anomalies)}`);
    }
  }

  if (expect?.anomalyNiveau && expect?.hasAnomalyCode) {
    const item = anomalies.find((a) => a.code === expect.hasAnomalyCode);
    if (item && item.niveau !== expect.anomalyNiveau) {
      errors.push(`niveau attendu ${expect.anomalyNiveau}, recu ${item.niveau}`);
    }
  }

  if (expect?.hasDocumentsManquants) {
    const docs = body.documents_manquants ?? [];
    if (!Array.isArray(docs) || docs.length === 0) {
      errors.push("documents_manquants attendu non vide");
    }
  }

  if (errors.length > 0) {
    throw new Error(`[${scenario}] ${errors.join(" | ")}`);
  }

  return true;
}

// ================= RUN ONE =================
async function runScenario(opts, scenario) {
  console.log(`\n${"=".repeat(60)}`);
  console.log(`SCENARIO: ${scenario}`);
  console.log("=".repeat(60));

  const payload = buildScenarioPayload(scenario, opts);
  const form = buildFormData(opts.docsDir, payload);
  if (opts.debug) form.append("debug", "true");

  const controller = AbortSignal.timeout(opts.timeoutSec * 1000);
  const res = await fetch(`${opts.baseUrl}/coherence/check`, {
    method: "POST",
    body: form,
    signal: controller,
  });

  const text = await res.text();
  let body;
  try {
    body = JSON.parse(text);
  } catch {
    body = { _raw: text };
  }

  console.log(`STATUS: ${res.status}`);
  console.log(JSON.stringify(body, null, 2));

  if (payload.expect) {
    assertResponse(scenario, res.status, body, payload.expect);
    console.log(`✅ ${scenario} — OK`);
  }

  if (opts.save) {
    const outPath = path.join(opts.save, `${scenario}.json`);
    fs.mkdirSync(opts.save, { recursive: true });
    fs.writeFileSync(outPath, JSON.stringify({ status: res.status, body }, null, 2));
  }

  return { scenario, status: res.status, body, ok: true };
}

// ================= MAIN =================
async function main() {
  const opts = parseArgs(process.argv.slice(2));

  console.log(`Base URL: ${opts.baseUrl}`);
  console.log(`Timeout: ${opts.timeoutSec}s`);
  console.log(`Docs: ${opts.docsDir}`);

  // HEALTH
  console.log("\n=== HEALTH ===");
  const health = await fetch(`${opts.baseUrl}/health`);
  const healthText = await health.text();
  console.log(healthText);
  if (!health.ok) {
    throw new Error(`Service indisponible sur ${opts.baseUrl}/health`);
  }

  const failures = [];

  if (opts.runAll) {
    for (const scenario of SCENARIOS) {
      try {
        await runScenario(opts, scenario);
      } catch (err) {
        console.error(`❌ ${scenario} — ${err.message}`);
        failures.push({ scenario, error: err.message });
      }
    }
  } else if (opts.interactive) {
    const rl = readline.createInterface({ input, output });
    let payload;
    try {
      payload = await readInteractivePayload(rl);
    } finally {
      rl.close();
    }

    console.log("\n=== POST /coherence/check (interactif) ===");
    const form = buildFormData(opts.docsDir, payload);
    if (opts.debug) form.append("debug", "true");

    const res = await fetch(`${opts.baseUrl}/coherence/check`, {
      method: "POST",
      body: form,
      signal: AbortSignal.timeout(opts.timeoutSec * 1000),
    });
    const text = await res.text();
    console.log(`STATUS: ${res.status}`);
    console.log(text);

    if (Object.prototype.hasOwnProperty.call(JSON.parse(text), "score_coherence")) {
      console.warn("⚠️  score_coherence encore present");
    }
  } else {
    const scenario = opts.scenario ?? "ok";
    try {
      await runScenario(opts, scenario);
    } catch (err) {
      console.error(`❌ ${err.message}`);
      process.exitCode = 1;
      return;
    }
  }

  if (failures.length > 0) {
    console.log(`\n${failures.length} echec(s) sur ${SCENARIOS.length} scenarios`);
    for (const f of failures) console.log(`  - ${f.scenario}: ${f.error}`);
    process.exitCode = 1;
    return;
  }

  if (opts.runAll) {
    console.log(`\n✅ Tous les scenarios (${SCENARIOS.length}) sont OK`);
  }
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
