const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
  HeadingLevel, AlignmentType, PageNumber, Header, Footer,
  WidthType, BorderStyle, ShadingType, VerticalAlign,
  PageBreak
} = require('docx');

const fs = require('fs');

const TNR  = "Times New Roman";
const SZ   = 24;
const SZH1 = 32;
const SZH2 = 28;
const SZH3 = 26;
const LINE = 360;
const AFT  = 140;

// ── helpers texte ──────────────────────────────────────────
function p(text, opts = {}) {
  return new Paragraph({
    alignment: AlignmentType.JUSTIFIED,
    spacing: { line: LINE, before: 0, after: AFT },
    children: [new TextRun({
      text,
      font: TNR,
      size: SZ,
      bold: opts.bold || false,
      italics: opts.italic || false,
      color: opts.color || "000000"
    })]
  });
}

function h1(t) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_1,
    spacing: { before: 500, after: 220 },
    children: [new TextRun({
      text: t,
      font: TNR,
      size: SZH1,
      bold: true,
      color: "1F3864"
    })]
  });
}

function h2(t) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_2,
    spacing: { before: 360, after: 180 },
    children: [new TextRun({
      text: t,
      font: TNR,
      size: SZH2,
      bold: true,
      color: "2E4057"
    })]
  });
}

function h3(t) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_3,
    spacing: { before: 260, after: 140 },
    children: [new TextRun({
      text: t,
      font: TNR,
      size: SZH3,
      bold: true,
      italics: true,
      color: "1F3864"
    })]
  });
}

function sp() {
  return new Paragraph({
    children: [new TextRun({ text: "" })]
  });
}

function pb() {
  return new Paragraph({
    children: [new PageBreak()]
  });
}

function caption(text) {
  return new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { line: LINE, before: 60, after: 200 },
    children: [new TextRun({
      text,
      font: TNR,
      size: 20,
      italics: true,
      color: "555555"
    })]
  });
}

function encadre(title, text, color) {
  return new Paragraph({
    alignment: AlignmentType.JUSTIFIED,
    spacing: { line: LINE, before: 160, after: 160 },
    border: {
      top: { style: "single", size: 6, color },
      bottom: { style: "single", size: 6, color },
      left: { style: "single", size: 18, color },
      right: { style: "single", size: 6, color },
    },
    shading: { fill: "F5F8FF", type: ShadingType.CLEAR },
    indent: { left: 200, right: 200 },
    children: [
      new TextRun({
        text: title + "  ",
        font: TNR,
        size: SZ,
        bold: true,
        color
      }),
      new TextRun({
        text,
        font: TNR,
        size: SZ,
        color: "333333"
      })
    ]
  });
}

// ── helpers tableau ──────────────────────────────────────────
const BD = {
  style: BorderStyle.SINGLE,
  size: 1,
  color: "CCCCCC"
};

const BS = {
  top: BD,
  bottom: BD,
  left: BD,
  right: BD
};

function cell(text, opts = {}) {
  return new TableCell({
    borders: BS,
    shading: opts.header
      ? { fill: "1F3864", type: ShadingType.CLEAR }
      : opts.shade
      ? { fill: "EEF2F7", type: ShadingType.CLEAR }
      : { fill: "FFFFFF", type: ShadingType.CLEAR },

    margins: {
      top: 80,
      bottom: 80,
      left: 120,
      right: 120
    },

    verticalAlign: VerticalAlign.CENTER,

    children: [
      new Paragraph({
        alignment: AlignmentType.JUSTIFIED,
        children: [
          new TextRun({
            text,
            font: TNR,
            size: 20,
            bold: opts.bold || opts.header || false,
            color: opts.header ? "FFFFFF" : "000000"
          })
        ]
      })
    ]
  });
}

function hrow(labels) {
  return new TableRow({
    children: labels.map(l => cell(l, { header: true }))
  });
}

function row(cells, shade = false) {
  return new TableRow({
    children: cells.map(c => cell(c, { shade }))
  });
}

// ── tableaux ──────────────────────────────────────────
const tCompar = new Table({
  width: { size: 9026, type: WidthType.DXA },
  rows: [
    hrow(["Modèle", "AUC-ROC", "F1", "Retenu"]),
    row(["Régression logistique", "0,6500", "0,19", "Non"]),
    row(["XGBoost", "0,7966", "0,44", "Non"], true),
    row(["LightGBM", "0,8222", "0,49", "✅ Oui"])
  ]
});

const tParams = new Table({
  width: { size: 9026, type: WidthType.DXA },
  rows: [
    hrow(["Paramètre", "Valeur", "Description"]),
    row(["n_estimators", "2500", "Nombre d’arbres de décision"]),
    row(["learning_rate", "0,02", "Apprentissage lent et précis"], true),
    row(["num_leaves", "63", "Nombre maximal de branches"]),
    row(["subsample", "0,8", "80 % des données par arbre"], true)
  ]
});

const tReport = new Table({
  width: { size: 9026, type: WidthType.DXA },
  rows: [
    hrow(["Classe", "Précision", "Rappel", "F1-score"]),
    row(["Non-défaut", "0,92", "0,85", "0,88"]),
    row(["Défaut", "0,41", "0,60", "0,49"], true)
  ]
});

// ── document ──────────────────────────────────────────
const doc = new Document({
  sections: [{
    headers: {
      default: new Header({
        children: [
          new Paragraph({
            alignment: AlignmentType.RIGHT,
            children: [
              new TextRun({
                text: "Pré-scoring BNPL UIB",
                font: TNR,
                size: 18,
                italics: true,
                color: "888888"
              })
            ]
          })
        ]
      })
    },

    footers: {
      default: new Footer({
        children: [
          new Paragraph({
            alignment: AlignmentType.CENTER,
            children: [
              new TextRun({
                text: "Page ",
                font: TNR,
                size: 18
              }),
              new TextRun({
                children: [PageNumber.CURRENT]
              })
            ]
          })
        ]
      })
    },

    children: [

      // PAGE TITRE
      new Paragraph({
        alignment: AlignmentType.CENTER,
        spacing: { before: 3000, after: 400 },
        children: [
          new TextRun({
            text: "PHASES 4, 5 & 6",
            font: TNR,
            size: 40,
            bold: true,
            color: "1F3864"
          })
        ]
      }),

      new Paragraph({
        alignment: AlignmentType.CENTER,
        spacing: { after: 300 },
        children: [
          new TextRun({
            text: "Modélisation, Évaluation et Déploiement",
            font: TNR,
            size: 30,
            italics: true
          })
        ]
      }),

      pb(),

      // PHASE 4
      h1("Phase 4 — Modélisation"),

      h2("4.1 Introduction"),
      p("La modélisation consiste à entraîner un algorithme à reconnaître les comportements associés au risque de défaut de paiement à partir de données historiques."),

      encadre(
        "💡 Objectif",
        "Le modèle apprend à estimer automatiquement une probabilité de défaut pour chaque nouveau client.",
        "1F3864"
      ),

      h2("4.2 Comparaison des modèles"),
      p("Trois modèles ont été testés afin de sélectionner la meilleure solution pour le pré-scoring BNPL."),

      sp(),
      tCompar,
      sp(),

      h2("4.3 Modèle retenu — LightGBM"),
      p("Le modèle LightGBM a été retenu car il offre les meilleures performances tout en permettant l’intégration de contraintes métier bancaires."),

      h3("4.3.1 Paramètres principaux"),
      p("Les paramètres suivants ont été utilisés pour optimiser les performances du modèle :"),

      sp(),
      tParams,
      sp(),

      encadre(
        "✅ Avantage principal",
        "LightGBM permet d’imposer des règles métier cohérentes avec les pratiques bancaires.",
        "2E7D32"
      ),

      // PHASE 5
      pb(),

      h1("Phase 5 — Évaluation"),

      h2("5.1 Métriques d’évaluation"),
      p("La qualité du modèle est mesurée à l’aide de plusieurs indicateurs statistiques tels que l’AUC-ROC, la précision et le rappel."),

      encadre(
        "📊 AUC-ROC",
        "Une AUC proche de 1 signifie que le modèle distingue efficacement les bons clients des clients risqués.",
        "1F3864"
      ),

      h2("5.2 Résultats du modèle"),
      p("Les performances détaillées du modèle LightGBM sont présentées ci-dessous."),

      sp(),
      tReport,
      sp(),

      p("Le modèle atteint une AUC de 0,8222, ce qui représente une très bonne performance pour un système de pré-scoring bancaire."),

      encadre(
        "💡 Interprétation",
        "Le modèle ne remplace pas l’analyste crédit mais sert d’outil d’aide à la décision.",
        "2E7D32"
      ),

      // PHASE 6
      pb(),

      h1("Phase 6 — Déploiement"),

      h2("6.1 Déploiement du modèle"),
      p("Le modèle entraîné est intégré dans un microservice Flask afin d’être accessible par les applications UIB BNPL."),

      h2("6.2 Fonctionnement global"),
      p("Lorsqu’un dossier est soumis, le serveur applique automatiquement les étapes suivantes : validation, transformation des données, prédiction et génération du score final."),

      encadre(
        "⚙️ Pipeline",
        "Le serveur applique les mêmes traitements que ceux utilisés pendant l’entraînement du modèle.",
        "1F3864"
      ),

      h2("6.3 Perspectives"),
      p("Les prochaines versions intégreront des mécanismes d’explicabilité SHAP et des modèles de détection d’anomalies afin d’améliorer encore la fiabilité du système."),

      encadre(
        "🎯 Conclusion",
        "Le système BNPL UIB constitue une première base opérationnelle de pré-scoring automatisé adaptée au contexte bancaire.",
        "1F3864"
      )
    ]
  }]
});

// ── génération docx ──────────────────────────────────────────
Packer.toBuffer(doc).then(buffer => {

  fs.writeFileSync(
    "phases_4_5_6_version_pedagogique.docx",
    buffer
  );

  console.log("✅ Document généré avec succès !");
});