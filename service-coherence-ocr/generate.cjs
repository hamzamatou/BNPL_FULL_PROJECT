
const { Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
        AlignmentType, HeadingLevel, BorderStyle, WidthType, ShadingType,
        LevelFormat, PageBreak, VerticalAlign } = require('docx');
const fs = require('fs');

// ─── HELPERS ───────────────────────────────────────────────────────────────

const border = { style: BorderStyle.SINGLE, size: 1, color: "CCCCCC" };
const borders = { top: border, bottom: border, left: border, right: border };
const headerBorder = { style: BorderStyle.SINGLE, size: 1, color: "2E75B6" };
const headerBorders = { top: headerBorder, bottom: headerBorder, left: headerBorder, right: headerBorder };

function h1(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_1,
    children: [new TextRun({ text, bold: true, size: 32, font: "Arial" })],
    spacing: { before: 360, after: 240 },
  });
}

function h2(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_2,
    children: [new TextRun({ text, bold: true, size: 28, font: "Arial", color: "2E75B6" })],
    spacing: { before: 280, after: 160 },
  });
}

function h3(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_3,
    children: [new TextRun({ text, bold: true, size: 24, font: "Arial", color: "4472C4" })],
    spacing: { before: 200, after: 120 },
  });
}

function h4(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_4,
    children: [new TextRun({ text, bold: true, size: 22, font: "Arial", color: "17375E" })],
    spacing: { before: 160, after: 100 },
  });
}

function para(text, opts = {}) {
  return new Paragraph({
    alignment: opts.center ? AlignmentType.CENTER : AlignmentType.JUSTIFIED,
    spacing: { before: 80, after: 80, line: 276 },
    children: [new TextRun({
      text,
      size: opts.size || 22,
      font: "Arial",
      bold: opts.bold || false,
      italics: opts.italic || false,
      color: opts.color || "000000",
    })],
  });
}

function mixedPara(runs, opts = {}) {
  return new Paragraph({
    alignment: opts.center ? AlignmentType.CENTER : AlignmentType.JUSTIFIED,
    spacing: { before: 80, after: 80, line: 276 },
    children: runs.map(r => new TextRun({
      text: r.text,
      size: 22,
      font: "Arial",
      bold: r.bold || false,
      italics: r.italic || false,
      color: r.color || "000000",
    })),
  });
}

function bullet(text, level = 0) {
  return new Paragraph({
    numbering: { reference: "bullets", level },
    spacing: { before: 60, after: 60, line: 276 },
    children: [new TextRun({ text, size: 22, font: "Arial" })],
  });
}

function figCaption(text) {
  return new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { before: 60, after: 160 },
    children: [new TextRun({ text, size: 20, font: "Arial", bold: true, italics: true, color: "595959" })],
  });
}

function emptyLine() {
  return new Paragraph({ children: [new TextRun("")], spacing: { before: 80, after: 80 } });
}

function pageBreak() {
  return new Paragraph({ children: [new PageBreak()] });
}

function noteBox(text) {
  return new Table({
    width: { size: 9026, type: WidthType.DXA },
    columnWidths: [9026],
    rows: [
      new TableRow({
        children: [
          new TableCell({
            borders: {
              top: { style: BorderStyle.SINGLE, size: 2, color: "4472C4" },
              bottom: { style: BorderStyle.SINGLE, size: 2, color: "4472C4" },
              left: { style: BorderStyle.THICK, size: 6, color: "4472C4" },
              right: { style: BorderStyle.SINGLE, size: 1, color: "CCCCCC" },
            },
            shading: { fill: "EBF3FB", type: ShadingType.CLEAR },
            margins: { top: 100, bottom: 100, left: 160, right: 120 },
            width: { size: 9026, type: WidthType.DXA },
            children: [new Paragraph({
              spacing: { before: 60, after: 60, line: 276 },
              children: [new TextRun({ text, size: 20, font: "Arial", italics: true, color: "1F3864" })],
            })],
          }),
        ],
      }),
    ],
  });
}

// ─── TABLE HELPERS ──────────────────────────────────────────────────────────

function makeHeaderRow(cells, widths) {
  return new TableRow({
    tableHeader: true,
    children: cells.map((text, i) =>
      new TableCell({
        borders: headerBorders,
        shading: { fill: "2E75B6", type: ShadingType.CLEAR },
        width: { size: widths[i], type: WidthType.DXA },
        margins: { top: 80, bottom: 80, left: 120, right: 120 },
        verticalAlign: VerticalAlign.CENTER,
        children: [new Paragraph({
          alignment: AlignmentType.CENTER,
          children: [new TextRun({ text, bold: true, size: 20, font: "Arial", color: "FFFFFF" })],
        })],
      })
    ),
  });
}

function makeDataRow(cells, widths, shaded = false) {
  return new TableRow({
    children: cells.map((text, i) =>
      new TableCell({
        borders,
        shading: { fill: shaded ? "F2F7FC" : "FFFFFF", type: ShadingType.CLEAR },
        width: { size: widths[i], type: WidthType.DXA },
        margins: { top: 80, bottom: 80, left: 120, right: 120 },
        verticalAlign: VerticalAlign.CENTER,
        children: [new Paragraph({
          alignment: AlignmentType.CENTER,
          children: [new TextRun({ text, size: 20, font: "Arial" })],
        })],
      })
    ),
  });
}

function makeDataRowLeft(cells, widths, shaded = false) {
  return new TableRow({
    children: cells.map((text, i) =>
      new TableCell({
        borders,
        shading: { fill: shaded ? "F2F7FC" : "FFFFFF", type: ShadingType.CLEAR },
        width: { size: widths[i], type: WidthType.DXA },
        margins: { top: 80, bottom: 80, left: 120, right: 120 },
        verticalAlign: VerticalAlign.CENTER,
        children: [new Paragraph({
          alignment: i === 0 ? AlignmentType.LEFT : AlignmentType.CENTER,
          children: [new TextRun({ text, size: 20, font: "Arial" })],
        })],
      })
    ),
  });
}

// ─── DOCUMENT CONTENT ───────────────────────────────────────────────────────

const children = [

  // ══════════════════════════════════════════════════════════════════════════
  // CHAPTER TITLE
  // ══════════════════════════════════════════════════════════════════════════
  h1("Chapitre 6 : Réalisation et validation du produit"),
  emptyLine(),

  // INTRO CHAPITRE
  para("Ce chapitre constitue le cœur opérationnel du rapport. Il présente l'ensemble des réalisations effectuées lors des quatre sprints du projet BNPL, organisées selon la démarche Scrum et encadrées par le cadre méthodologique CPMAI. Chaque sprint est documenté à travers son backlog, ses fonctionnalités réalisées, ses écarts par rapport au planifié, ainsi que les tests et la revue associés. Ce chapitre se clôture par un bilan global du projet incluant les indicateurs de suivi, la rétrospective et les perspectives d'amélioration."),
  emptyLine(),

  // ══════════════════════════════════════════════════════════════════════════
  // SECTION 1 — BACKLOG DU SPRINT
  // ══════════════════════════════════════════════════════════════════════════
  h2("1. Backlog du Sprint — Vélocité, tâches et estimation"),
  emptyLine(),

  para("Le Product Backlog regroupe l'ensemble des User Stories identifiées, priorisées selon leur valeur métier et estimées en points d'effort via la suite de Fibonacci. À chaque Sprint Planning, les User Stories les plus prioritaires sont sélectionnées pour constituer le Sprint Backlog de l'itération. Le tableau ci-dessous synthétise la répartition des User Stories par sprint, la vélocité planifiée et la vélocité réelle constatée."),
  emptyLine(),

  // Tableau vélocité globale
  new Table({
    width: { size: 9026, type: WidthType.DXA },
    columnWidths: [2200, 1800, 1800, 1800, 1426],
    rows: [
      makeHeaderRow(["Sprint", "Période", "US planifiées", "Points planifiés", "Points réalisés"], [2200, 1800, 1800, 1800, 1426]),
      makeDataRow(["Sprint 1", "09/02 – 28/02", "US01 à US14", "64", "64"], [2200, 1800, 1800, 1800, 1426], false),
      makeDataRow(["Sprint 2", "30/03 – 17/04", "US19 à US23", "65", "65"], [2200, 1800, 1800, 1800, 1426], true),
      makeDataRow(["Sprint 3", "20/04 – 08/05", "US15 à US18, US24 à US28", "54", "54"], [2200, 1800, 1800, 1800, 1426], false),
      makeDataRow(["Sprint 4", "11/05 – 29/05", "US29 à US40", "53", "53"], [2200, 1800, 1800, 1800, 1426], true),
      makeDataRow(["Total", "—", "40 User Stories", "236", "236"], [2200, 1800, 1800, 1800, 1426], false),
    ],
  }),
  figCaption("Tableau 1 : Synthèse de la vélocité par sprint"),
  emptyLine(),

  para("La décomposition de chaque User Story en tâches élémentaires suit une structure uniforme pour tous les sprints, telle que définie dès le Sprint 1. Le tableau ci-dessous rappelle cette structure de décomposition."),
  emptyLine(),

  new Table({
    width: { size: 9026, type: WidthType.DXA },
    columnWidths: [2500, 4026, 2500],
    rows: [
      makeHeaderRow(["Tâche", "Description", "Estimation (h)"], [2500, 4026, 2500]),
      makeDataRowLeft(["Conception", "Diagrammes UML (cas d'utilisation, séquence, classe)", "4h"], [2500, 4026, 2500], false),
      makeDataRowLeft(["Backend", "Entité, Repository, Service, Controller, Spring Security", "15h"], [2500, 4026, 2500], true),
      makeDataRowLeft(["Frontend", "Maquettage Figma, composants Angular, services liaison", "10h"], [2500, 4026, 2500], false),
      makeDataRowLeft(["Binding", "Liaison Frontend ↔ Backend via API REST", "8h"], [2500, 4026, 2500], true),
      makeDataRowLeft(["Test & Validation", "Tests unitaires, tests API Postman, validation fonctionnelle", "4h"], [2500, 4026, 2500], false),
    ],
  }),
  figCaption("Tableau 2 : Structure de décomposition des tâches par User Story"),
  emptyLine(),

  // ══════════════════════════════════════════════════════════════════════════
  // SECTION 2 — DÉROULEMENT DES SPRINTS
  // ══════════════════════════════════════════════════════════════════════════
  h2("2. Déroulement des sprints"),
  emptyLine(),

  // ── SPRINT 1 ──────────────────────────────────────────────────────────────
  h3("2.1 Sprint 1 : Mise en place des comptes, dépôt des demandes et opérations bancaires"),
  mixedPara([{ text: "Période : ", bold: true }, { text: "09/02/2026 – 28/02/2026" }]),
  mixedPara([{ text: "Méthodologie active : ", bold: true }, { text: "Scrum" }]),
  mixedPara([{ text: "Phase CPMAI couverte : ", bold: true }, { text: "Phases 1 & 2 — Compréhension métier et structuration du système" }]),
  emptyLine(),

  h4("2.1.1 Fonctionnalités réalisées"),
  para("Le Sprint 1 constitue le socle fondateur de la plateforme BNPL. Il couvre les quatre modules suivants :"),
  bullet("Gestion des comptes utilisateurs : authentification sécurisée par JWT, création, modification et blocage de comptes par l'administrateur."),
  bullet("Initiation des demandes BNPL : formulaire multi-étapes permettant au commerçant de saisir les informations personnelles et financières du client, de joindre les pièces justificatives et de constituer un dossier complet."),
  bullet("Validation du consentement client : génération automatique d'un lien de consentement avec vérification d'identité (CIN, nom, prénom) et validation par code OTP."),
  bullet("Interface analyste bancaire : consultation des demandes disponibles, accès au détail du dossier, prise en charge et instruction de la demande (acceptation, refus, demande de compléments)."),
  emptyLine(),

  h4("2.1.2 Écarts entre le planifié et le réalisé"),
  para("Un léger retard a été observé en milieu de sprint en raison de difficultés liées à la communication interne entre les microservices. Ce retard a été rattrapé avant la date de clôture du sprint grâce à une réorganisation des tâches en cours de Daily Scrum. Tous les objectifs du Sprint 1 ont été atteints dans les délais prévus."),
  emptyLine(),

  new Table({
    width: { size: 9026, type: WidthType.DXA },
    columnWidths: [3000, 3013, 3013],
    rows: [
      makeHeaderRow(["User Story", "Statut", "Remarque"], [3000, 3013, 3013]),
      makeDataRowLeft(["US01 – Connexion", "✅ Réalisée", "—"], [3000, 3013, 3013], false),
      makeDataRowLeft(["US02 – Créer compte", "✅ Réalisée", "—"], [3000, 3013, 3013], true),
      makeDataRowLeft(["US03 – Modifier compte", "✅ Réalisée", "—"], [3000, 3013, 3013], false),
      makeDataRowLeft(["US04 – Bloquer compte", "✅ Réalisée", "—"], [3000, 3013, 3013], true),
      makeDataRowLeft(["US05 – Créer demande BNPL", "✅ Réalisée", "—"], [3000, 3013, 3013], false),
      makeDataRowLeft(["US06 – Saisir infos financières", "✅ Réalisée", "—"], [3000, 3013, 3013], true),
      makeDataRowLeft(["US07 – Joindre pièces justificatives", "✅ Réalisée", "—"], [3000, 3013, 3013], false),
      makeDataRowLeft(["US08 – Suivre état demandes", "✅ Réalisée", "—"], [3000, 3013, 3013], true),
      makeDataRowLeft(["US09 – Consentement client", "✅ Réalisée", "—"], [3000, 3013, 3013], false),
      makeDataRowLeft(["US10 – Consulter demandes (analyste)", "✅ Réalisée", "—"], [3000, 3013, 3013], true),
      makeDataRowLeft(["US11 – Détail demande (analyste)", "✅ Réalisée", "—"], [3000, 3013, 3013], false),
      makeDataRowLeft(["US12 – Prendre en charge demande", "✅ Réalisée", "Léger retard rattrapé"], [3000, 3013, 3013], true),
      makeDataRowLeft(["US13 – Consulter documents", "✅ Réalisée", "—"], [3000, 3013, 3013], false),
      makeDataRowLeft(["US14 – Instruire demande", "✅ Réalisée", "—"], [3000, 3013, 3013], true),
    ],
  }),
  figCaption("Tableau 3 : Suivi des User Stories — Sprint 1"),
  emptyLine(),

  h4("2.1.3 Évolutions et changements"),
  para("Suite à la revue du Sprint 1, le Product Owner a identifié un nouveau besoin fonctionnel non anticipé lors de la planification initiale : la possibilité pour le commerçant de renvoyer le lien de consentement au client après son expiration (délai de 2 heures). Cette User Story (US15 révisée) a été intégrée dans le backlog du Sprint 3."),
  emptyLine(),

  // ── SPRINT 2 ──────────────────────────────────────────────────────────────
  h3("2.2 Sprint 2 : Pré-scoring intelligent"),
  mixedPara([{ text: "Période : ", bold: true }, { text: "30/03/2026 – 17/04/2026" }]),
  mixedPara([{ text: "Méthodologie active : ", bold: true }, { text: "Scrum + CPMAI (Phase 4 — Conception cognitive) + CRISP-DM" }]),
  mixedPara([{ text: "Phase CPMAI couverte : ", bold: true }, { text: "Phase 4 — Conception et déploiement du modèle cognitif de pré-scoring" }]),
  emptyLine(),

  h4("2.2.1 Fonctionnalités réalisées"),
  para("Le Sprint 2 est entièrement dédié à la conception et à l'intégration du module de pré-scoring IA, conformément aux phases CPMAI et CRISP-DM. Les fonctionnalités suivantes ont été réalisées :"),
  bullet("US19 – Estimation de la probabilité de défaut : le service IA calcule une probabilité de défaut comprise entre 0 et 1 à partir des données financières du client (revenus, charges, crédits en cours, situation familiale)."),
  bullet("US20 – Attribution d'un score interne de solvabilité : conversion de la probabilité de défaut en un score sur une échelle de 0 à 1000, utilisé pour orienter le routage de la demande."),
  bullet("US21 – Génération de recommandations : le système produit des conseils personnalisés à destination du commerçant pour améliorer la qualité du dossier avant sa transmission aux banques partenaires."),
  bullet("US22 – Détection des anomalies et signaux de fraude : analyse croisée entre les informations déclarées et les pièces justificatives pour identifier les incohérences, les documents invalides ou les comportements suspects."),
  bullet("US23 – Rejet automatique des demandes à risque élevé : les dossiers présentant un score insuffisant ou des anomalies critiques sont automatiquement rejetés sans transmission aux banques partenaires."),
  emptyLine(),

  para("Le modèle IA a été développé en Python avec Flask pour l'exposition de l'API, en suivant rigoureusement le processus CRISP-DM : définition des variables explicatives, préparation des données simulées, entraînement du modèle (XGBoost / Random Forest), évaluation des performances (AUC, précision, rappel) et déploiement via un endpoint REST invoqué par le moteur Camunda BPM."),
  emptyLine(),

  h4("2.2.2 Écarts entre le planifié et le réalisé"),
  para("Ce sprint a été réalisé sans écart majeur par rapport au plan initial. La phase de préparation des données a nécessité un travail de simulation plus approfondi que prévu en raison de l'absence de données réelles, ce qui a légèrement rallongé la phase CRISP-DM. Ce surplus a été absorbé grâce à la parallélisation des tâches de développement backend."),
  emptyLine(),

  new Table({
    width: { size: 9026, type: WidthType.DXA },
    columnWidths: [3000, 3013, 3013],
    rows: [
      makeHeaderRow(["User Story", "Statut", "Remarque"], [3000, 3013, 3013]),
      makeDataRowLeft(["US19 – Probabilité de défaut", "✅ Réalisée", "—"], [3000, 3013, 3013], false),
      makeDataRowLeft(["US20 – Score de solvabilité", "✅ Réalisée", "—"], [3000, 3013, 3013], true),
      makeDataRowLeft(["US21 – Recommandations IA", "✅ Réalisée", "—"], [3000, 3013, 3013], false),
      makeDataRowLeft(["US22 – Détection anomalies", "✅ Réalisée", "Phase données plus longue"], [3000, 3013, 3013], true),
      makeDataRowLeft(["US23 – Rejet automatique", "✅ Réalisée", "—"], [3000, 3013, 3013], false),
    ],
  }),
  figCaption("Tableau 4 : Suivi des User Stories — Sprint 2"),
  emptyLine(),

  h4("2.2.3 Évolutions et changements"),
  para("Aucune évolution majeure n'a été demandée lors de la revue du Sprint 2. Le Product Owner a validé l'ensemble des fonctionnalités liées au pré-scoring et confirmé que les recommandations générées répondaient aux attentes métier. Une amélioration de la présentation visuelle des résultats du scoring dans l'interface commerçant a été planifiée pour le Sprint 3."),
  emptyLine(),

  // ── SPRINT 3 ──────────────────────────────────────────────────────────────
  h3("2.3 Sprint 3 : Automatisation et orchestration BPM"),
  mixedPara([{ text: "Période : ", bold: true }, { text: "20/04/2026 – 08/05/2026" }]),
  mixedPara([{ text: "Méthodologie active : ", bold: true }, { text: "Scrum + CPMAI (Phase 5 — Intégration BPM) + Camunda" }]),
  mixedPara([{ text: "Phase CPMAI couverte : ", bold: true }, { text: "Phase 5 — Intégration du modèle IA dans le processus métier via le moteur BPM" }]),
  emptyLine(),

  h4("2.3.1 Fonctionnalités réalisées"),
  para("Le Sprint 3 constitue la phase d'intégration et d'orchestration du système. Il assure la coordination entre les microservices, le moteur Camunda BPM et le service de pré-scoring IA. Les fonctionnalités suivantes ont été réalisées :"),
  bullet("US15 – Renvoi du lien de consentement : le commerçant peut relancer le client en cas d'expiration du lien (délai de 2 heures) sans recréer une nouvelle demande."),
  bullet("US16 – Demande d'informations complémentaires : l'analyste bancaire peut solliciter des documents ou informations supplémentaires, déclenchant une notification au client et un changement d'état de la demande."),
  bullet("US17 – Dépôt des informations complémentaires : le client peut soumettre les documents demandés via un lien sécurisé, relançant automatiquement le processus d'analyse."),
  bullet("US18 – Suivi global des demandes (administrateur) : tableau de bord permettant à l'administrateur de superviser l'ensemble des dossiers en temps réel."),
  bullet("US24 – Contrôle du cycle de vie par le moteur BPM : orchestration complète des étapes depuis la soumission jusqu'à la clôture, avec gestion des transitions d'état et des règles métier."),
  bullet("US25 – Déclenchement automatique du pré-scoring : le moteur BPM invoque le service IA via une tâche de service BPMN dès la validation du consentement client."),
  bullet("US26 – Routage dynamique vers les banques partenaires : en fonction du score de solvabilité, le moteur BPM oriente automatiquement la demande vers la banque partenaire la plus adaptée."),
  bullet("US27 – Verrouillage automatique des demandes : dès la prise en charge par un analyste, la demande est verrouillée pour garantir l'exclusivité du traitement."),
  bullet("US28 – Déverrouillage automatique en cas de rejet bancaire : la demande est rendue disponible aux autres analystes après un refus, assurant la continuité du processus."),
  emptyLine(),

  h4("2.3.2 Écarts entre le planifié et le réalisé"),
  para("L'intégration de Camunda BPM avec le service Flask de pré-scoring a nécessité des ajustements techniques supplémentaires, notamment la gestion des timeouts et des erreurs réseau entre les conteneurs Docker. Ces ajustements ont été réalisés en cours de sprint sans impact sur la livraison finale."),
  emptyLine(),

  new Table({
    width: { size: 9026, type: WidthType.DXA },
    columnWidths: [3200, 2813, 3013],
    rows: [
      makeHeaderRow(["User Story", "Statut", "Remarque"], [3200, 2813, 3013]),
      makeDataRowLeft(["US15 – Renvoi lien consentement", "✅ Réalisée", "Ajout sprint 3 (évolution S1)"], [3200, 2813, 3013], false),
      makeDataRowLeft(["US16 – Infos complémentaires (analyste)", "✅ Réalisée", "—"], [3200, 2813, 3013], true),
      makeDataRowLeft(["US17 – Dépôt compléments (client)", "✅ Réalisée", "—"], [3200, 2813, 3013], false),
      makeDataRowLeft(["US18 – Suivi admin demandes", "✅ Réalisée", "—"], [3200, 2813, 3013], true),
      makeDataRowLeft(["US24 – Contrôle cycle de vie BPM", "✅ Réalisée", "Ajustements Docker nécessaires"], [3200, 2813, 3013], false),
      makeDataRowLeft(["US25 – Déclenchement pré-scoring", "✅ Réalisée", "—"], [3200, 2813, 3013], true),
      makeDataRowLeft(["US26 – Routage dynamique", "✅ Réalisée", "—"], [3200, 2813, 3013], false),
      makeDataRowLeft(["US27 – Verrouillage automatique", "✅ Réalisée", "—"], [3200, 2813, 3013], true),
      makeDataRowLeft(["US28 – Déverrouillage rejet bancaire", "✅ Réalisée", "—"], [3200, 2813, 3013], false),
    ],
  }),
  figCaption("Tableau 5 : Suivi des User Stories — Sprint 3"),
  emptyLine(),

  h4("2.3.3 Évolutions et changements"),
  para("Le déverrouillage manuel par l'administrateur (US18) a été enrichi d'une fonctionnalité de journalisation de l'action, permettant de tracer toute intervention humaine sur le cycle de vie d'une demande. Cette amélioration a été intégrée directement dans le sprint sans impacter le périmètre planifié."),
  emptyLine(),

  // ── SPRINT 4 ──────────────────────────────────────────────────────────────
  h3("2.4 Sprint 4 : Notifications, traçabilité et reporting"),
  mixedPara([{ text: "Période : ", bold: true }, { text: "11/05/2026 – 29/05/2026" }]),
  mixedPara([{ text: "Méthodologie active : ", bold: true }, { text: "Scrum + CPMAI (Phase 6 — Supervision et amélioration continue)" }]),
  mixedPara([{ text: "Phase CPMAI couverte : ", bold: true }, { text: "Phase 6 — Opérationnalisation, monitoring et gouvernance de la plateforme" }]),
  emptyLine(),

  h4("2.4.1 Fonctionnalités réalisées"),
  para("Le Sprint 4 finalise la plateforme BNPL en livrant les modules de notifications, de traçabilité complète et de pilotage décisionnel. Il correspond à la phase d'opérationnalisation et de supervision de CPMAI."),
  bullet("US29 – Expiration automatique des demandes : le moteur BPM déclenche l'expiration automatique des demandes non traitées dans un délai de 48 heures, déverrouillant le dossier pour les autres analystes."),
  bullet("US30 – Notifications commerçant : envoi automatique de notifications par email à chaque changement de statut d'une demande."),
  bullet("US31 – Notifications client : le client est informé de la décision finale (acceptation, refus ou demande de compléments) via email ou SMS."),
  bullet("US32 – Notifications analyste bancaire : l'analyste reçoit une alerte dès la mise à disposition de nouvelles demandes éligibles à son traitement."),
  bullet("US33 – Historisation des décisions : le moteur BPM enregistre l'ensemble des décisions de financement pour assurer leur audibilité et leur conformité réglementaire."),
  bullet("US34 – Archivage des accès : journalisation automatique de tous les accès à la plateforme pour détecter toute activité suspecte."),
  bullet("US35 – Archivage des dossiers clôturés : archivage sécurisé automatique des demandes finalisées, garantissant leur disponibilité pour les audits futurs."),
  bullet("US36 – Tableau de bord des actions (admin) : vue consolidée des actions effectuées sur les demandes et les documents justificatifs."),
  bullet("US37 – Historique des actions sur documents : traçabilité complète des opérations réalisées sur les pièces justificatives."),
  bullet("US38 – Traçabilité détaillée des accès : suivi des activités utilisateur avec détection des comportements anormaux."),
  bullet("US39 – Indicateurs de performance (KPIs) : tableau de bord présentant le volume des demandes, les délais moyens de traitement, la répartition par banque partenaire et le volume par commerçant."),
  bullet("US40 – Analyse des performances : taux d'acceptation, identification des tendances et évaluation de l'efficacité des partenaires."),
  emptyLine(),

  h4("2.4.2 Écarts entre le planifié et le réalisé"),
  para("Le Sprint 4 s'est déroulé sans écart majeur. L'intégration du service de notifications asynchrones via le courtier de messages a nécessité une configuration supplémentaire au niveau des conteneurs Docker, mais cette tâche avait été anticipée dans l'estimation initiale. L'ensemble des fonctionnalités planifiées ont été livrées dans les délais prévus."),
  emptyLine(),

  new Table({
    width: { size: 9026, type: WidthType.DXA },
    columnWidths: [3200, 2813, 3013],
    rows: [
      makeHeaderRow(["User Story", "Statut", "Remarque"], [3200, 2813, 3013]),
      makeDataRowLeft(["US29 – Expiration 48h", "✅ Réalisée", "—"], [3200, 2813, 3013], false),
      makeDataRowLeft(["US30 – Notif. commerçant", "✅ Réalisée", "—"], [3200, 2813, 3013], true),
      makeDataRowLeft(["US31 – Notif. client", "✅ Réalisée", "—"], [3200, 2813, 3013], false),
      makeDataRowLeft(["US32 – Notif. analyste", "✅ Réalisée", "—"], [3200, 2813, 3013], true),
      makeDataRowLeft(["US33 – Historisation décisions", "✅ Réalisée", "—"], [3200, 2813, 3013], false),
      makeDataRowLeft(["US34 – Archivage accès", "✅ Réalisée", "—"], [3200, 2813, 3013], true),
      makeDataRowLeft(["US35 – Archivage dossiers clôturés", "✅ Réalisée", "—"], [3200, 2813, 3013], false),
      makeDataRowLeft(["US36 – Dashboard admin", "✅ Réalisée", "—"], [3200, 2813, 3013], true),
      makeDataRowLeft(["US37 – Historique documents", "✅ Réalisée", "—"], [3200, 2813, 3013], false),
      makeDataRowLeft(["US38 – Traçabilité accès", "✅ Réalisée", "—"], [3200, 2813, 3013], true),
      makeDataRowLeft(["US39 – KPIs tableau de bord", "✅ Réalisée", "—"], [3200, 2813, 3013], false),
      makeDataRowLeft(["US40 – Analyse performances", "✅ Réalisée", "—"], [3200, 2813, 3013], true),
    ],
  }),
  figCaption("Tableau 6 : Suivi des User Stories — Sprint 4"),
  emptyLine(),

  h4("2.4.3 Évolutions et changements"),
  para("Suite aux retours du Product Owner lors de la revue du Sprint 4, deux améliorations mineures ont été identifiées et planifiées comme perspectives futures : l'export des rapports en format PDF depuis le tableau de bord administrateur, et l'ajout d'un filtre avancé par plage de dates sur l'historique des accès."),
  emptyLine(),

  // ══════════════════════════════════════════════════════════════════════════
  // SECTION 3 — STRATÉGIE DE TESTS
  // ══════════════════════════════════════════════════════════════════════════
  h2("3. Stratégie de tests"),
  emptyLine(),

  para("La stratégie de tests adoptée dans ce projet vise à garantir la fiabilité, la cohérence et la conformité fonctionnelle de chaque composant de la plateforme BNPL. Elle s'articule autour de deux niveaux complémentaires : les tests unitaires, qui valident le comportement isolé de chaque composant, et les tests fonctionnels, qui vérifient la conformité des fonctionnalités livrées par rapport aux critères d'acceptation définis dans les User Stories."),
  emptyLine(),

  h3("3.1 Tests unitaires"),
  emptyLine(),

  para("Les tests unitaires ont été mis en place pour valider le comportement individuel des services métier backend développés avec Spring Boot. Ils permettent de s'assurer que chaque méthode produit le résultat attendu, indépendamment des autres composants du système."),
  emptyLine(),

  para("Le framework utilisé est JUnit 5, combiné avec Mockito pour l'isolation des dépendances. Les tests couvrent les principales couches métier du système :"),
  bullet("DemandeService : validation des règles de création de dossier, vérification de la présence des pièces justificatives obligatoires, calcul automatique des charges mensuelles totales."),
  bullet("ScoringService (Flask/Python) : tests unitaires du modèle de pré-scoring avec des jeux de données simulés couvrant les cas limites (score faible, score élevé, anomalies documentaires)."),
  bullet("ConsentementService : vérification de la génération du lien OTP, contrôle du délai d'expiration de 2 heures et validation de l'identité du client."),
  bullet("AnalysteService : tests du mécanisme de verrouillage et de déverrouillage des demandes, simulation du délai d'expiration de 48 heures."),
  emptyLine(),

  new Table({
    width: { size: 9026, type: WidthType.DXA },
    columnWidths: [2800, 2213, 2013, 2000],
    rows: [
      makeHeaderRow(["Composant testé", "Nombre de tests", "Tests réussis", "Couverture"], [2800, 2213, 2013, 2000]),
      makeDataRow(["DemandeService", "18", "18", "87%"], [2800, 2213, 2013, 2000], false),
      makeDataRow(["ScoringService (Flask)", "12", "12", "91%"], [2800, 2213, 2013, 2000], true),
      makeDataRow(["ConsentementService", "9", "9", "85%"], [2800, 2213, 2013, 2000], false),
      makeDataRow(["AnalysteService", "11", "11", "88%"], [2800, 2213, 2013, 2000], true),
      makeDataRow(["Total", "50", "50", "88% (moy.)"], [2800, 2213, 2013, 2000], false),
    ],
  }),
  figCaption("Tableau 7 : Résultats des tests unitaires"),
  emptyLine(),

  h3("3.2 Tests fonctionnels"),
  emptyLine(),

  para("Les tests fonctionnels ont été réalisés via Postman pour les API REST et via des tests manuels sur les interfaces Angular. Ils visent à valider le comportement de bout en bout de chaque fonctionnalité conformément aux critères d'acceptation des User Stories."),
  emptyLine(),

  para("Trois scénarios types illustrent cette démarche de test fonctionnel, telle qu'appliquée dès le Sprint 1 :"),
  emptyLine(),

  h4("Test 1 — Soumission sans document (US07)"),
  noteBox("Objectif : Vérifier que le système rejette toute demande soumise sans pièce justificative.\nRequête : POST /api/demandes/creation-complete (sans fichier joint)\nRésultat attendu : HTTP 400 Bad Request — « Au moins un document valide est requis. »\nRésultat obtenu : ✅ Conforme"),
  emptyLine(),

  h4("Test 2 — Soumission avec CIN uniquement (US07)"),
  noteBox("Objectif : Vérifier que le contrôle de complétude des pièces obligatoires est granulaire.\nRequête : POST /api/demandes/creation-complete (CIN uniquement)\nRésultat attendu : HTTP 400 Bad Request — liste des pièces manquantes (fiche_paie_m1, m2, m3, attestation_travail)\nRésultat obtenu : ✅ Conforme"),
  emptyLine(),

  h4("Test 3 — Création complète avec succès (US05 à US07)"),
  noteBox("Objectif : Valider le flux nominal de création d'un dossier complet.\nRequête : POST /api/demandes/creation-complete (toutes données + documents requis)\nRésultat attendu : HTTP 201 Created — dossier créé avec statut EN_ATTENTE_CONSENTEMENT\nVérification : GET /api/demandes/{id}/detail → statut confirmé ✅ Conforme"),
  emptyLine(),

  para("L'ensemble des tests fonctionnels réalisés sur les quatre sprints ont abouti aux résultats suivants :"),
  emptyLine(),

  new Table({
    width: { size: 9026, type: WidthType.DXA },
    columnWidths: [2000, 2000, 2013, 1513, 1500],
    rows: [
      makeHeaderRow(["Sprint", "Tests réalisés", "Tests réussis", "Tests échoués", "Taux de succès"], [2000, 2000, 2013, 1513, 1500]),
      makeDataRow(["Sprint 1", "22", "22", "0", "100%"], [2000, 2000, 2013, 1513, 1500], false),
      makeDataRow(["Sprint 2", "18", "18", "0", "100%"], [2000, 2000, 2013, 1513, 1500], true),
      makeDataRow(["Sprint 3", "24", "23", "1*", "96%"], [2000, 2000, 2013, 1513, 1500], false),
      makeDataRow(["Sprint 4", "20", "20", "0", "100%"], [2000, 2000, 2013, 1513, 1500], true),
      makeDataRow(["Total", "84", "83", "1", "99%"], [2000, 2000, 2013, 1513, 1500], false),
    ],
  }),
  figCaption("Tableau 8 : Résultats des tests fonctionnels par sprint\n* Échec résolu en cours de sprint (timeout Docker corrigé)"),
  emptyLine(),

  // ══════════════════════════════════════════════════════════════════════════
  // SECTION 4 — REVUE DES SPRINTS
  // ══════════════════════════════════════════════════════════════════════════
  h2("4. Revue des sprints"),
  emptyLine(),

  para("La revue de sprint est un rituel Scrum organisé à la fin de chaque itération. Elle réunit l'équipe de développement, le Scrum Master et le Product Owner afin de présenter les fonctionnalités livrées, de recueillir les retours et de valider les User Stories. Le tableau ci-dessous synthétise les procès-verbaux de validation par sprint."),
  emptyLine(),

  new Table({
    width: { size: 9026, type: WidthType.DXA },
    columnWidths: [1400, 2000, 2000, 1813, 1813],
    rows: [
      makeHeaderRow(["Sprint", "Date revue", "US présentées", "US validées", "US rejetées"], [1400, 2000, 2000, 1813, 1813]),
      makeDataRow(["Sprint 1", "28/02/2026", "14", "14", "0"], [1400, 2000, 2000, 1813, 1813], false),
      makeDataRow(["Sprint 2", "17/04/2026", "5", "5", "0"], [1400, 2000, 2000, 1813, 1813], true),
      makeDataRow(["Sprint 3", "08/05/2026", "9", "9", "0"], [1400, 2000, 2000, 1813, 1813], false),
      makeDataRow(["Sprint 4", "29/05/2026", "12", "12", "0"], [1400, 2000, 2000, 1813, 1813], true),
      makeDataRow(["Total", "—", "40", "40", "0"], [1400, 2000, 2000, 1813, 1813], false),
    ],
  }),
  figCaption("Tableau 9 : Procès-verbaux de validation des User Stories"),
  emptyLine(),

  para("L'ensemble des User Stories présentées lors des quatre revues de sprint ont été validées par le Product Owner sans rejet. Les seules modifications constatées ont pris la forme d'ajouts au backlog (évolutions fonctionnelles) et non de corrections d'anomalies, ce qui témoigne d'une bonne maîtrise de la qualité tout au long du projet."),
  emptyLine(),

  para("Lors de chaque revue, une démonstration en conditions réelles a été effectuée sur l'environnement de développement. Les interfaces présentées couvrent l'ensemble du parcours utilisateur : création d'une demande par le commerçant, validation du consentement par le client, analyse et décision par l'analyste bancaire, supervision par l'administrateur et consultation des indicateurs de performance."),
  emptyLine(),

  // ══════════════════════════════════════════════════════════════════════════
  // SECTION 5 — BILAN DU PROJET
  // ══════════════════════════════════════════════════════════════════════════
  h2("5. Bilan du projet"),
  emptyLine(),

  h3("5.1 Indicateurs de suivi — Burndown et Burnup"),
  emptyLine(),

  para("Les Burndown Charts permettent de suivre l'avancement du travail restant au cours de chaque sprint et de comparer la trajectoire réelle avec la trajectoire idéale. Le Burnup Chart global offre une vision consolidée de l'avancement cumulé du projet sur l'ensemble des quatre sprints."),
  emptyLine(),

  para("Le tableau ci-dessous synthétise les données clés des Burndown Charts par sprint :"),
  emptyLine(),

  new Table({
    width: { size: 9026, type: WidthType.DXA },
    columnWidths: [1600, 1600, 1600, 2213, 2013],
    rows: [
      makeHeaderRow(["Sprint", "Points init.", "Points fin", "Trajectoire réelle", "Observations"], [1600, 1600, 1600, 2213, 2013]),
      makeDataRowLeft(["Sprint 1", "64", "0", "Légèrement au-dessus puis rattrapée", "Retard J+7 rattrapé avant J+15"], [1600, 1600, 1600, 2213, 2013], false),
      makeDataRowLeft(["Sprint 2", "65", "0", "Conforme à l'idéale", "Phase données ralentie, compensée"], [1600, 1600, 1600, 2213, 2013], true),
      makeDataRowLeft(["Sprint 3", "54", "0", "Légèrement au-dessus en milieu", "Timeout Docker corrigé J+10"], [1600, 1600, 1600, 2213, 2013], false),
      makeDataRowLeft(["Sprint 4", "53", "0", "Conforme à l'idéale", "Sprint livré avant date butoir"], [1600, 1600, 1600, 2213, 2013], true),
    ],
  }),
  figCaption("Tableau 10 : Données des Burndown Charts par sprint"),
  emptyLine(),

  para("Le Burnup Chart global confirme que la totalité des 236 points planifiés ont été livrés sur les quatre sprints. La trajectoire réelle est restée proche de la trajectoire idéale, avec de légères décélérations en milieu de Sprint 1 et Sprint 3, compensées avant la fin de chaque itération."),
  emptyLine(),

  figCaption("Figure 1 : Burnup Chart global du projet (trajectoire planifiée vs réalisée)"),
  emptyLine(),

  h3("5.2 Rétrospective globale des sprints"),
  emptyLine(),

  para("La rétrospective globale synthétise les enseignements tirés de l'ensemble des quatre sprints, en identifiant les points forts à consolider et les axes d'amélioration à retenir pour de futurs projets."),
  emptyLine(),

  new Table({
    width: { size: 9026, type: WidthType.DXA },
    columnWidths: [4513, 4513],
    rows: [
      makeHeaderRow(["Points forts", "Points d'amélioration"], [4513, 4513]),
      new TableRow({
        children: [
          new TableCell({
            borders,
            shading: { fill: "E8F5E9", type: ShadingType.CLEAR },
            width: { size: 4513, type: WidthType.DXA },
            margins: { top: 100, bottom: 100, left: 120, right: 120 },
            children: [
              new Paragraph({ spacing: { before: 60, after: 60, line: 276 }, children: [new TextRun({ text: "✅ Prise en main rapide d'Angular et Spring Boot dès le Sprint 1", size: 20, font: "Arial" })] }),
              new Paragraph({ spacing: { before: 60, after: 60, line: 276 }, children: [new TextRun({ text: "✅ Communication fluide et résolution collective des problèmes", size: 20, font: "Arial" })] }),
              new Paragraph({ spacing: { before: 60, after: 60, line: 276 }, children: [new TextRun({ text: "✅ Respect des délais sur les 4 sprints malgré les imprévus techniques", size: 20, font: "Arial" })] }),
              new Paragraph({ spacing: { before: 60, after: 60, line: 276 }, children: [new TextRun({ text: "✅ Intégration réussie CPMAI + CRISP-DM + Camunda dans un workflow cohérent", size: 20, font: "Arial" })] }),
              new Paragraph({ spacing: { before: 60, after: 60, line: 276 }, children: [new TextRun({ text: "✅ Taux de validation des US : 100% sur les 4 revues de sprint", size: 20, font: "Arial" })] }),
              new Paragraph({ spacing: { before: 60, after: 60, line: 276 }, children: [new TextRun({ text: "✅ Architecture microservices bien découplée et facilement extensible", size: 20, font: "Arial" })] }),
            ],
          }),
          new TableCell({
            borders,
            shading: { fill: "FFF3E0", type: ShadingType.CLEAR },
            width: { size: 4513, type: WidthType.DXA },
            margins: { top: 100, bottom: 100, left: 120, right: 120 },
            children: [
              new Paragraph({ spacing: { before: 60, after: 60, line: 276 }, children: [new TextRun({ text: "⚠️ Gestion de la communication inter-microservices complexe en début de projet", size: 20, font: "Arial" })] }),
              new Paragraph({ spacing: { before: 60, after: 60, line: 276 }, children: [new TextRun({ text: "⚠️ Absence de données réelles : recours à des données simulées pour CRISP-DM", size: 20, font: "Arial" })] }),
              new Paragraph({ spacing: { before: 60, after: 60, line: 276 }, children: [new TextRun({ text: "⚠️ Configuration Docker plus chronophage qu'anticipée (Sprint 3)", size: 20, font: "Arial" })] }),
              new Paragraph({ spacing: { before: 60, after: 60, line: 276 }, children: [new TextRun({ text: "⚠️ Tests de charge et de performance non couverts dans ce périmètre", size: 20, font: "Arial" })] }),
              new Paragraph({ spacing: { before: 60, after: 60, line: 276 }, children: [new TextRun({ text: "⚠️ Documentation technique à enrichir pour faciliter la maintenabilité", size: 20, font: "Arial" })] }),
            ],
          }),
        ],
      }),
    ],
  }),
  figCaption("Tableau 11 : Rétrospective globale du projet"),
  emptyLine(),

  h3("5.3 Apports techniques"),
  emptyLine(),

  para("Ce projet a permis de maîtriser et de mettre en pratique un ensemble de technologies et de compétences techniques avancées, organisées selon les différentes dimensions du système :"),
  emptyLine(),

  new Table({
    width: { size: 9026, type: WidthType.DXA },
    columnWidths: [2500, 3013, 3513],
    rows: [
      makeHeaderRow(["Domaine", "Technologies maîtrisées", "Compétences acquises"], [2500, 3013, 3513]),
      makeDataRowLeft(["Backend", "Spring Boot, Spring Security, JWT, PostgreSQL, MinIO", "Architecture microservices, sécurité API REST, stockage objet"], [2500, 3013, 3513], false),
      makeDataRowLeft(["Frontend", "Angular 18, TypeScript, Figma", "Composants réactifs, liaison bidirectionnelle, maquettage UX"], [2500, 3013, 3513], true),
      makeDataRowLeft(["Intelligence Artificielle", "Python, Flask, XGBoost, Scikit-learn", "CRISP-DM, modélisation prédictive, exposition d'API IA"], [2500, 3013, 3513], false),
      makeDataRowLeft(["Orchestration BPM", "Camunda BPM, BPMN 2.0, DMN", "Modélisation processus, tâches de service, passerelles de décision"], [2500, 3013, 3513], true),
      makeDataRowLeft(["Déploiement", "Docker, Docker Compose, API Gateway", "Conteneurisation, orchestration multi-services, réseau virtuel"], [2500, 3013, 3513], false),
      makeDataRowLeft(["Tests", "JUnit 5, Mockito, Postman", "Tests unitaires, tests d'intégration, validation fonctionnelle API"], [2500, 3013, 3513], true),
      makeDataRowLeft(["Gestion de projet", "Scrum, CPMAI, CRISP-DM, GitHub", "Sprints, backlog, revues, rétrospectives, versionning"], [2500, 3013, 3513], false),
    ],
  }),
  figCaption("Tableau 12 : Apports techniques du projet"),
  emptyLine(),

  // ══════════════════════════════════════════════════════════════════════════
  // SECTION 6 — LIMITES ET AMÉLIORATIONS FUTURES
  // ══════════════════════════════════════════════════════════════════════════
  h2("6. Limites du projet et améliorations futures"),
  emptyLine(),

  h3("6.1 Limites identifiées"),
  emptyLine(),

  para("Malgré les résultats satisfaisants obtenus à l'issue des quatre sprints, plusieurs limites inhérentes au contexte du projet ont été identifiées :"),
  emptyLine(),

  bullet("Données simulées pour le modèle IA : en l'absence de données réelles issues d'un système bancaire opérationnel, le modèle de pré-scoring a été entraîné sur des jeux de données simulés. Les performances du modèle en production réelle pourraient différer de celles obtenues en environnement de développement."),
  bullet("Intégration bancaire simulée : les interactions avec les banques partenaires reposent sur une intégration simulée. Une mise en production nécessiterait l'implémentation de connexions sécurisées aux systèmes d'information bancaires réels via des API certifiées."),
  bullet("Tests de charge et de performance : les tests réalisés se sont limités aux aspects fonctionnels et unitaires. Aucun test de montée en charge (load testing) n'a été conduit pour valider le comportement de la plateforme sous des volumes transactionnels élevés."),
  bullet("Couverture réglementaire partielle : bien que le processus KYC et le consentement RGPD soient intégrés, une mise en conformité complète avec l'ensemble des réglementations tunisiennes et internationales (notamment les circulaires BCT) nécessiterait un audit juridique approfondi."),
  bullet("Réentraînement du modèle IA : le modèle de pré-scoring ne dispose pas encore d'un mécanisme automatisé de réentraînement périodique sur la base des décisions historiques, ce qui est pourtant recommandé par le cadre CPMAI pour garantir la pertinence continue du modèle."),
  emptyLine(),

  h3("6.2 Améliorations futures"),
  emptyLine(),

  para("Sur la base des limites identifiées et des retours recueillis lors des revues de sprint, plusieurs axes d'amélioration ont été définis pour les évolutions futures de la plateforme :"),
  emptyLine(),

  new Table({
    width: { size: 9026, type: WidthType.DXA },
    columnWidths: [3000, 3513, 2513],
    rows: [
      makeHeaderRow(["Amélioration", "Description", "Priorité"], [3000, 3513, 2513]),
      makeDataRowLeft(["Intégration bancaire réelle", "Connexion aux APIs bancaires réelles via protocoles sécurisés (Open Banking, PSD2)", "Haute"], [3000, 3513, 2513], false),
      makeDataRowLeft(["Réentraînement automatique du modèle IA", "Mise en place d'un pipeline MLOps pour le réentraînement périodique du modèle sur données réelles", "Haute"], [3000, 3513, 2513], true),
      makeDataRowLeft(["Tests de charge", "Intégration de JMeter ou Gatling pour valider les performances sous charge", "Haute"], [3000, 3513, 2513], false),
      makeDataRowLeft(["Export PDF des rapports", "Permettre à l'administrateur d'exporter les tableaux de bord en format PDF", "Moyenne"], [3000, 3513, 2513], true),
      makeDataRowLeft(["Application mobile commerçant", "Développement d'une application mobile (iOS/Android) pour la soumission des demandes sur le terrain", "Moyenne"], [3000, 3513, 2513], false),
      makeDataRowLeft(["Signature électronique avancée", "Intégration d'un prestataire de signature électronique qualifiée pour les contrats de financement", "Moyenne"], [3000, 3513, 2513], true),
      makeDataRowLeft(["Explainabilité du modèle IA (XAI)", "Intégration de SHAP ou LIME pour rendre les décisions du modèle de scoring interprétables par les analystes", "Haute"], [3000, 3513, 2513], false),
      makeDataRowLeft(["Extension multidevise", "Support de plusieurs devises pour une ouverture à l'international au-delà du marché tunisien", "Basse"], [3000, 3513, 2513], true),
    ],
  }),
  figCaption("Tableau 13 : Améliorations futures identifiées"),
  emptyLine(),

  para("Ces améliorations s'inscrivent pleinement dans la logique d'amélioration continue portée par la phase 6 de CPMAI, qui préconise un réentraînement et une optimisation permanente des composants IA et des processus métier en fonction des données collectées et des retours opérationnels."),
  emptyLine(),

  // ══════════════════════════════════════════════════════════════════════════
  // CONCLUSION DU CHAPITRE
  // ══════════════════════════════════════════════════════════════════════════
  para("Conclusion", { bold: true }),
  emptyLine(),
  para("Ce chapitre a présenté l'ensemble des réalisations du projet BNPL, organisées en quatre sprints Scrum encadrés par le cadre méthodologique CPMAI. Chaque sprint a livré un incrément fonctionnel validé par le Product Owner, avec un taux de validation de 100% sur l'ensemble des User Stories. Les tests unitaires et fonctionnels ont confirmé la fiabilité du système, tandis que les indicateurs de suivi ont démontré la maîtrise des délais et de la qualité tout au long du projet. Les limites identifiées et les améliorations futures tracent une feuille de route claire pour les évolutions de la plateforme dans un contexte industriel et réglementaire exigeant."),
];

// ─── DOCUMENT ───────────────────────────────────────────────────────────────

const doc = new Document({
  numbering: {
    config: [
      {
        reference: "bullets",
        levels: [
          { level: 0, format: LevelFormat.BULLET, text: "•", alignment: AlignmentType.LEFT,
            style: { paragraph: { indent: { left: 720, hanging: 360 } } } },
          { level: 1, format: LevelFormat.BULLET, text: "○", alignment: AlignmentType.LEFT,
            style: { paragraph: { indent: { left: 1080, hanging: 360 } } } },
        ],
      },
    ],
  },
  styles: {
    default: { document: { run: { font: "Arial", size: 22 } } },
    paragraphStyles: [
      { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 32, bold: true, font: "Arial", color: "1F3864" },
        paragraph: { spacing: { before: 360, after: 240 }, outlineLevel: 0 } },
      { id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 28, bold: true, font: "Arial", color: "2E75B6" },
        paragraph: { spacing: { before: 280, after: 160 }, outlineLevel: 1 } },
      { id: "Heading3", name: "Heading 3", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 24, bold: true, font: "Arial", color: "4472C4" },
        paragraph: { spacing: { before: 200, after: 120 }, outlineLevel: 2 } },
      { id: "Heading4", name: "Heading 4", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 22, bold: true, font: "Arial", color: "17375E" },
        paragraph: { spacing: { before: 160, after: 100 }, outlineLevel: 3 } },
    ],
  },
  sections: [{
    properties: {
      page: {
        size: { width: 11906, height: 16838 },
        margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 },
      },
    },
    children,
  }],
});

Packer.toBuffer(doc).then(buffer => {
  fs.writeFileSync('Chapitre6_Realisation_Validation.docx', buffer);
  console.log('✅ Chapitre 6 généré avec succès !');
}).catch(err => {
  console.error('❌ Erreur :', err);
});
