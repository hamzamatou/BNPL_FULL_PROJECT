import {
  Document,
  Packer,
  Paragraph,
  TextRun,
  AlignmentType,
  HeadingLevel,
  BorderStyle,
  ShadingType,
  WidthType,
  Table,
  TableRow,
  TableCell,
  PageBreak
} from "docx";

import fs from "fs";
// Color palette
const BLUE_DARK  = "1F3864";
const BLUE_MID   = "2E75B6";
const BLUE_LIGHT = "D6E4F0";
const GOLD       = "C8A951";
const WHITE      = "FFFFFF";
const GRAY_LIGHT = "F5F7FA";
const TEXT_DARK  = "1A1A2E";

// ── helpers ──────────────────────────────────────────────────────────────────

function spacer(before = 0, after = 0) {
  return new Paragraph({ text: "", spacing: { before, after } });
}

function separator() {
  return new Paragraph({
    text: "",
    spacing: { before: 120, after: 120 },
    border: { bottom: { style: BorderStyle.SINGLE, size: 8, color: GOLD, space: 1 } }
  });
}

function coverTitle(text, color = WHITE, size = 52, bold = true) {
  return new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { before: 80, after: 80 },
    children: [new TextRun({ text, color, size, bold, font: "Arial" })]
  });
}

function coverSub(text, color = BLUE_LIGHT, size = 28) {
  return new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { before: 40, after: 40 },
    children: [new TextRun({ text, color, size, font: "Arial" })]
  });
}

function sectionHeading(text, lang = "fr") {
  const colors = { fr: BLUE_MID, en: "1A5276", ar: "6E2C00" };
  const col = colors[lang] || BLUE_MID;
  return new Paragraph({
    spacing: { before: 280, after: 140 },
    border: {
      bottom: { style: BorderStyle.SINGLE, size: 6, color: col, space: 2 },
      left:   { style: BorderStyle.THICK,  size: 12, color: col, space: 6 }
    },
    indent: { left: 160 },
    children: [
      new TextRun({ text, bold: true, size: 30, color: col, font: "Arial" })
    ]
  });
}

function bodyText(text, opts = {}) {
  return new Paragraph({
    alignment: opts.rtl ? AlignmentType.RIGHT : AlignmentType.JUSTIFIED,
    spacing: { before: 80, after: 80 },
    indent: { left: opts.rtl ? 0 : 160, right: opts.rtl ? 160 : 0 },
    children: [
      new TextRun({
        text,
        size:  opts.size  || 22,
        color: opts.color || TEXT_DARK,
        bold:  opts.bold  || false,
        font: "Arial",
        rtl:  opts.rtl   || false
      })
    ]
  });
}

function bullet(text, opts = {}) {
  return new Paragraph({
    alignment: opts.rtl ? AlignmentType.RIGHT : AlignmentType.LEFT,
    spacing: { before: 60, after: 60 },
    indent: { left: opts.rtl ? 0 : 560, right: opts.rtl ? 560 : 0, hanging: 280 },
    children: [
      new TextRun({ text: opts.rtl ? "◄ " : "▶ ", color: GOLD, size: 20, bold: true, font: "Arial" }),
      new TextRun({ text, size: 21, color: TEXT_DARK, font: "Arial", rtl: opts.rtl || false })
    ]
  });
}

function langBanner(label, color) {
  return new Table({
    width: { size: 9360, type: WidthType.DXA },
    columnWidths: [9360],
    rows: [
      new TableRow({
        children: [
          new TableCell({
            shading: { fill: color, type: ShadingType.CLEAR },
            margins: { top: 160, bottom: 160, left: 320, right: 320 },
            children: [
              new Paragraph({
                alignment: AlignmentType.CENTER,
                children: [
                  new TextRun({ text: label, bold: true, size: 34, color: WHITE, font: "Arial" })
                ]
              })
            ]
          })
        ]
      })
    ]
  });
}

function highlightBox(text, fillColor = BLUE_LIGHT) {
  return new Table({
    width: { size: 9360, type: WidthType.DXA },
    columnWidths: [9360],
    rows: [
      new TableRow({
        children: [
          new TableCell({
            shading: { fill: fillColor, type: ShadingType.CLEAR },
            margins: { top: 120, bottom: 120, left: 280, right: 280 },
            borders: {
              top:    { style: BorderStyle.SINGLE, size: 4, color: BLUE_MID },
              bottom: { style: BorderStyle.SINGLE, size: 4, color: BLUE_MID },
              left:   { style: BorderStyle.THICK,  size: 12, color: GOLD },
              right:  { style: BorderStyle.SINGLE, size: 4, color: BLUE_MID }
            },
            children: [
              new Paragraph({
                alignment: AlignmentType.JUSTIFIED,
                children: [new TextRun({ text, size: 21, color: TEXT_DARK, font: "Arial", italics: true })]
              })
            ]
          })
        ]
      })
    ]
  });
}

function twoColRow(label, value, labelColor = BLUE_MID) {
  const cellBorder = { style: BorderStyle.SINGLE, size: 1, color: "CCCCCC" };
  const borders = { top: cellBorder, bottom: cellBorder, left: cellBorder, right: cellBorder };
  return new TableRow({
    children: [
      new TableCell({
        width: { size: 2800, type: WidthType.DXA },
        borders,
        shading: { fill: BLUE_LIGHT, type: ShadingType.CLEAR },
        margins: { top: 80, bottom: 80, left: 120, right: 120 },
        children: [
          new Paragraph({
            children: [new TextRun({ text: label, bold: true, size: 21, color: labelColor, font: "Arial" })]
          })
        ]
      }),
      new TableCell({
        width: { size: 6560, type: WidthType.DXA },
        borders,
        margins: { top: 80, bottom: 80, left: 120, right: 120 },
        children: [
          new Paragraph({
            children: [new TextRun({ text: value, size: 21, color: TEXT_DARK, font: "Arial" })]
          })
        ]
      })
    ]
  });
}

function infoTable(rows) {
  return new Table({
    width: { size: 9360, type: WidthType.DXA },
    columnWidths: [2800, 6560],
    rows
  });
}

// ── COVER PAGE ────────────────────────────────────────────────────────────────
function makeCoverPage() {
  return [
    // Top banner
    new Table({
      width: { size: 9360, type: WidthType.DXA },
      columnWidths: [9360],
      rows: [
        new TableRow({
          children: [
            new TableCell({
              shading: { fill: BLUE_DARK, type: ShadingType.CLEAR },
              margins: { top: 400, bottom: 400, left: 400, right: 400 },
              children: [
                coverTitle("RÉSUMÉ DU PROJET DE FIN D'ÉTUDES", WHITE, 40),
                coverTitle("مُلَخَّص مشروع التخرج", GOLD, 36),
                coverSub("Project Summary / ملخص المشروع", BLUE_LIGHT, 26),
              ]
            })
          ]
        })
      ]
    }),
    spacer(400, 200),
    new Table({
      width: { size: 9360, type: WidthType.DXA },
      columnWidths: [9360],
      rows: [
        new TableRow({
          children: [
            new TableCell({
              shading: { fill: GRAY_LIGHT, type: ShadingType.CLEAR },
              margins: { top: 300, bottom: 300, left: 400, right: 400 },
              borders: {
                top:    { style: BorderStyle.THICK,  size: 12, color: GOLD },
                bottom: { style: BorderStyle.THICK,  size: 12, color: GOLD },
                left:   { style: BorderStyle.SINGLE, size: 2,  color: "CCCCCC" },
                right:  { style: BorderStyle.SINGLE, size: 2,  color: "CCCCCC" }
              },
              children: [
                new Paragraph({
                  alignment: AlignmentType.CENTER,
                  spacing: { before: 80, after: 60 },
                  children: [new TextRun({ text: "Conception et Développement d'une Marketplace Financière BNPL", bold: true, size: 34, color: BLUE_DARK, font: "Arial" })]
                }),
                new Paragraph({
                  alignment: AlignmentType.CENTER,
                  spacing: { before: 40, after: 60 },
                  children: [new TextRun({ text: "basée sur un Moteur BPM et un Pré-scoring Intelligent", size: 26, color: BLUE_MID, font: "Arial", italics: true })]
                }),
                new Paragraph({
                  alignment: AlignmentType.CENTER,
                  spacing: { before: 20, after: 60 },
                  children: [new TextRun({ text: "تصميم وتطوير منصة مالية BNPL مبنية على محرك BPM والتنقيط الذكي المسبق", size: 26, color: GOLD, font: "Arial" })]
                }),
              ]
            })
          ]
        })
      ]
    }),
    spacer(300, 100),
    infoTable([
      twoColRow("Établissement",   "Institut Supérieur des Études Technologiques de Charguia (ISET)"),
      twoColRow("Société d'accueil", "Union Internationale de Banques (UIB)"),
      twoColRow("Étudiants",       "Maatougui Hamza & Homry Sywar"),
      twoColRow("Encadrants",      "Mme Latifa Jnifene Aounallah | M. Mohamed Aymen Ben Brahim"),
      twoColRow("Diplôme",         "Licence Appliquée en Technologies de l'Informatique – Parcours DSI"),
      twoColRow("Année universitaire", "2025 / 2026"),
    ]),
    spacer(300, 0),
    // Language flags row
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { before: 200, after: 100 },
      children: [
        new TextRun({ text: "🇫🇷 Français  •  🇬🇧 English  •  🇹🇳 العربية", size: 26, color: BLUE_MID, font: "Arial" })
      ]
    }),
    new Paragraph({
      children: [new PageBreak()]
    })
  ];
}

// ── FRENCH SUMMARY ────────────────────────────────────────────────────────────
function makeFrench() {
  return [
    langBanner("🇫🇷  RÉSUMÉ EN FRANÇAIS", BLUE_DARK),
    spacer(200, 100),

    sectionHeading("Contexte et Problématique", "fr"),
    bodyText("Ce projet de fin d'études, réalisé au sein de l'Union Internationale de Banques (UIB), s'inscrit dans un contexte socioéconomique tunisien en pleine mutation. L'entrée en vigueur de la nouvelle loi sur les chèques a entraîné une baisse significative de l'activité commerciale, le chèque étant historiquement le principal instrument de paiement différé. Face à cette réalité, le modèle BNPL (Buy Now, Pay Later) s'impose comme une alternative numérique, sécurisée et traçable, capable de maintenir la fluidité des transactions commerciales tout en respectant le nouveau cadre réglementaire."),
    spacer(60, 0),
    highlightBox("Problématique centrale : Comment automatiser et fiabiliser la décision de financement grâce à un module de pré-scoring IA, tout en orchestrant l'intégralité du cycle de vie des demandes de manière cohérente, conforme et auditable ?"),

    sectionHeading("Solution Proposée", "fr"),
    bodyText("La plateforme développée constitue une Marketplace financière BNPL intégrée combinant trois piliers technologiques majeurs :"),
    bullet("Un module de pré-scoring IA exploitant les données financières et comportementales du client pour estimer la probabilité de défaut, détecter les incohérences documentaires et générer des recommandations personnalisées."),
    bullet("Un moteur Core BPM (Camunda 7) assurant l'orchestration complète du cycle de vie des demandes : routage dynamique, verrouillage des dossiers, gestion des délais et expiration automatique à 48 heures."),
    bullet("Une architecture orientée microservices offrant modularité, scalabilité et résilience, intégrant cinq services spécialisés : Identity Service, Dossier Service, AI Scoring Service, Notification Service et Reporting Service."),

    sectionHeading("Méthodologie", "fr"),
    bodyText("Le projet s'appuie sur une approche multi-méthodologique rigoureusement articulée :"),
    bullet("Scrum : cadre organisationnel global structurant le développement en 4 sprints itératifs (mars – mai 2026)."),
    bullet("CPMAI (Cognitive Project Management for AI) : cadre stratégique d'intégration de l'IA dans le processus métier BNPL."),
    bullet("CRISP-DM : méthodologie de data science dédiée à la construction et à l'évaluation du modèle de pré-scoring."),
    bullet("Camunda BPM & BPMN : orchestration opérationnelle et modélisation des processus métiers."),

    sectionHeading("Intelligence Artificielle", "fr"),
    bodyText("Le module de pré-scoring s'articule autour de deux composantes complémentaires :"),
    bullet("IA Générative (Llama 3 via Ollama) : extraction OCR des documents justificatifs (Tesseract), détection des anomalies documentaires (incohérences d'identité, écarts financiers), et génération de recommandations financières personnalisées en respectant le seuil réglementaire de 40 % du taux d'endettement (BCT)."),
    bullet("Machine Learning supervisé (LightGBM) : entraîné sur le dataset Home Credit Default Risk (307 000 observations), avec recalibration monétaire complète au contexte tunisien. Le modèle atteint une AUC-ROC de 0,8222 et produit un score de solvabilité normalisé sur une échelle de 0 à 1000."),

    sectionHeading("Architecture Technique", "fr"),
    bodyText("La stack technique retenue comprend : Angular (frontend), Spring Boot & Spring Security/JWT (backend), Flask/Python (microservice IA), PostgreSQL (données relationnelles), MinIO (stockage objet S3), Camunda 7 (moteur BPM), Docker (conteneurisation), et RabbitMQ (broker de messages asynchrones)."),

    sectionHeading("Résultats et Bilan", "fr"),
    bullet("45 User Stories livrées sur 4 sprints, représentant 273 points d'effort, avec un taux de validation de 100 %."),
    bullet("Stratégie de tests couvrant 50 tests unitaires et 84 tests fonctionnels, avec un taux de succès global de 99 %."),
    bullet("Modèle LightGBM : AUC-ROC 0,8222 — surpassant la régression logistique (0,65) et XGBoost (0,7966)."),
    bullet("Architecture découplée et évolutive, prête pour un déploiement industriel en environnement bancaire réglementé."),

    new Paragraph({ children: [new PageBreak()] })
  ];
}

// ── ENGLISH SUMMARY ───────────────────────────────────────────────────────────
function makeEnglish() {
  return [
    langBanner("🇬🇧  SUMMARY IN ENGLISH", "1A5276"),
    spacer(200, 100),

    sectionHeading("Context and Problem Statement", "en"),
    bodyText("This final-year project, developed within the Union Internationale de Banques (UIB), addresses a pressing socioeconomic challenge in Tunisia. The enactment of a stricter check law has significantly reduced commercial activity, as checks historically served as the primary deferred payment instrument. In this context, the BNPL (Buy Now, Pay Later) model emerges as a digital, secure, and traceable alternative capable of sustaining commercial transaction flow while complying with the new regulatory framework."),
    spacer(60, 0),
    highlightBox("Core challenge: How to automate and secure financing decisions through an AI pre-scoring module while orchestrating the entire loan application lifecycle in a coherent, compliant, and auditable manner?"),

    sectionHeading("Proposed Solution", "en"),
    bodyText("The developed platform constitutes an integrated BNPL Financial Marketplace built around three major technological pillars:"),
    bullet("An AI pre-scoring module leveraging financial and behavioural client data to estimate default probability, detect document inconsistencies, and generate personalized recommendations."),
    bullet("A Core BPM engine (Camunda 7) providing complete lifecycle orchestration: dynamic routing, dossier locking, deadline management, and automatic 48-hour expiration."),
    bullet("A microservices architecture ensuring modularity, scalability, and resilience through five specialized services: Identity Service, Dossier Service, AI Scoring Service, Notification Service, and Reporting Service."),

    sectionHeading("Methodology", "en"),
    bodyText("The project employs a rigorously articulated multi-methodological approach:"),
    bullet("Scrum: global organizational framework structuring development into 4 iterative sprints (March – May 2026)."),
    bullet("CPMAI (Cognitive Project Management for AI): strategic framework for integrating AI into the BNPL business process."),
    bullet("CRISP-DM: data science methodology dedicated to building and evaluating the pre-scoring model."),
    bullet("Camunda BPM & BPMN: operational orchestration and business process modelling."),

    sectionHeading("Artificial Intelligence", "en"),
    bodyText("The pre-scoring module consists of two complementary components:"),
    bullet("Generative AI (Llama 3 via Ollama): OCR document extraction (Tesseract), documentary anomaly detection (identity mismatches, financial discrepancies), and personalized financial recommendations aligned with the BCT's 40% debt ratio regulatory threshold."),
    bullet("Supervised Machine Learning (LightGBM): trained on the Home Credit Default Risk dataset (307,000 observations) with full monetary recalibration to the Tunisian economic context. The model achieves an AUC-ROC of 0.8222 and produces a normalized solvency score on a 0–1000 scale."),

    sectionHeading("Technical Architecture", "en"),
    bodyText("The selected technology stack includes: Angular (frontend), Spring Boot & Spring Security/JWT (backend), Flask/Python (AI microservice), PostgreSQL (relational data), MinIO (S3-compatible object storage), Camunda 7 (BPM engine), Docker (containerization), and RabbitMQ (asynchronous message broker)."),

    sectionHeading("Results and Outcomes", "en"),
    bullet("45 User Stories delivered across 4 sprints, representing 273 effort points, with a 100% validation rate."),
    bullet("Test strategy covering 50 unit tests and 84 functional tests, achieving an overall success rate of 99%."),
    bullet("LightGBM model: AUC-ROC 0.8222 — outperforming logistic regression (0.65) and XGBoost (0.7966)."),
    bullet("Decoupled and scalable architecture ready for industrial deployment in a regulated banking environment."),

    new Paragraph({ children: [new PageBreak()] })
  ];
}

// ── ARABIC SUMMARY ────────────────────────────────────────────────────────────
function makeArabic() {
  const ar = { rtl: true };
  function arHeading(text) {
    return new Paragraph({
      alignment: AlignmentType.RIGHT,
      spacing: { before: 280, after: 140 },
      border: {
        bottom: { style: BorderStyle.SINGLE, size: 6, color: "6E2C00", space: 2 },
        right:  { style: BorderStyle.THICK,  size: 12, color: "6E2C00", space: 6 }
      },
      indent: { right: 160 },
      children: [new TextRun({ text, bold: true, size: 30, color: "6E2C00", font: "Arial", rtl: true })]
    });
  }
  function arBody(text) {
    return new Paragraph({
      alignment: AlignmentType.RIGHT,
      spacing: { before: 80, after: 80 },
      indent: { right: 160 },
      children: [new TextRun({ text, size: 22, color: TEXT_DARK, font: "Arial", rtl: true })]
    });
  }
  function arBullet(text) {
    return new Paragraph({
      alignment: AlignmentType.RIGHT,
      spacing: { before: 60, after: 60 },
      indent: { right: 560, hanging: 280 },
      children: [
        new TextRun({ text: " ◄", color: GOLD, size: 20, bold: true, font: "Arial", rtl: true }),
        new TextRun({ text: " " + text, size: 21, color: TEXT_DARK, font: "Arial", rtl: true })
      ]
    });
  }
  function arHighlight(text) {
    return new Table({
      width: { size: 9360, type: WidthType.DXA },
      columnWidths: [9360],
      rows: [
        new TableRow({
          children: [
            new TableCell({
              shading: { fill: "FEF9E7", type: ShadingType.CLEAR },
              margins: { top: 120, bottom: 120, left: 280, right: 280 },
              borders: {
                top:    { style: BorderStyle.SINGLE, size: 4, color: GOLD },
                bottom: { style: BorderStyle.SINGLE, size: 4, color: GOLD },
                right:  { style: BorderStyle.THICK,  size: 12, color: "6E2C00" },
                left:   { style: BorderStyle.SINGLE, size: 4, color: GOLD }
              },
              children: [
                new Paragraph({
                  alignment: AlignmentType.RIGHT,
                  children: [new TextRun({ text, size: 21, color: TEXT_DARK, font: "Arial", italics: true, rtl: true })]
                })
              ]
            })
          ]
        })
      ]
    });
  }

  return [
    langBanner("🇹🇳  الملخص باللغة العربية", "6E2C00"),
    spacer(200, 100),

    arHeading("السياق وإشكالية المشروع"),
    arBody("أُنجز هذا المشروع داخل بنك الاتحاد الدولي (UIB) في سياق اجتماعي واقتصادي تونسي في خضم تحولات عميقة. أفضى تطبيق القانون الجديد المتعلق بالشيكات إلى انخفاض ملحوظ في حجم المعاملات التجارية، إذ كان الشيك تاريخياً الأداة الرئيسية للدفع المؤجل في تونس. في مواجهة هذا الواقع، يبرز نموذج BNPL (اشتر الآن وادفع لاحقاً) كبديل رقمي آمن وقابل للتتبع، قادر على الحفاظ على انسيابية المعاملات التجارية مع الامتثال للإطار التنظيمي الجديد."),
    spacer(60, 0),
    arHighlight("الإشكالية المحورية: كيف يمكن أتمتة قرارات التمويل وتعزيز موثوقيتها عبر وحدة تنقيط ذكي مسبق مبنية على الذكاء الاصطناعي، مع تنسيق دورة حياة الطلبات كاملةً بطريقة متسقة ومتوافقة وقابلة للتدقيق؟"),

    arHeading("الحل المقترح"),
    arBody("تُشكّل المنصة المطورة سوقاً مالية متكاملة للـ BNPL تقوم على ثلاثة ركائز تقنية كبرى:"),
    arBullet("وحدة تنقيط ذكي مسبق بالذكاء الاصطناعي: تستثمر البيانات المالية والسلوكية للعميل لتقدير احتمال التعثر، واكتشاف التناقضات الوثائقية، وتوليد توصيات مخصصة."),
    arBullet("محرك BPM المركزي (Camunda 7): يضمن التنسيق الشامل لدورة حياة الطلبات من توجيه ديناميكي وتأمين للملفات وإدارة للمهل وانتهاء صلاحية تلقائي بعد 48 ساعة."),
    arBullet("معمارية قائمة على الخدمات المصغرة: تكفل المرونة والتوسعية والصمود عبر خمس خدمات متخصصة: خدمة الهوية، وإدارة الملفات، والتنقيط بالذكاء الاصطناعي، والإشعارات، والتقارير."),

    arHeading("المنهجية المعتمدة"),
    arBody("يرتكز المشروع على مقاربة منهجية متعددة المستويات ومحكمة التنسيق:"),
    arBullet("Scrum: الإطار التنظيمي الشامل الذي يُهيكل التطوير في 4 دورات تكرارية (مارس – مايو 2026)."),
    arBullet("CPMAI: الإطار الاستراتيجي لإدماج الذكاء الاصطناعي في العملية التجارية للـ BNPL."),
    arBullet("CRISP-DM: منهجية علم البيانات المخصصة لبناء نموذج التنقيط المسبق وتقييمه."),
    arBullet("Camunda BPM & BPMN: التنسيق التشغيلي ونمذجة العمليات التجارية."),

    arHeading("الذكاء الاصطناعي"),
    arBody("تنقسم وحدة التنقيط المسبق إلى مكوّنَين متكاملَين:"),
    arBullet("الذكاء الاصطناعي التوليدي (Llama 3 عبر Ollama): استخراج النصوص من الوثائق بتقنية OCR (Tesseract)، وكشف التناقضات الوثائقية (أخطاء الهوية والفجوات المالية)، وتوليد توصيات مالية مخصصة تراعي سقف نسبة الاستدانة البالغة 40% المحددة من البنك المركزي التونسي."),
    arBullet("التعلم الآلي المُشرَف (LightGBM): مدرَّب على مجموعة بيانات Home Credit Default Risk (307,000 سجل) مع معايرة نقدية كاملة تتوافق مع السياق الاقتصادي التونسي. يحقق النموذج AUC-ROC تساوي 0.8222 ويُنتج نقاطاً معيارية للملاءة على سلّم من 0 إلى 1000."),

    arHeading("البنية التقنية"),
    arBody("تشمل الحزمة التقنية المعتمدة: Angular (الواجهة الأمامية)، Spring Boot وSpring Security/JWT (الخلفية)، Flask/Python (الخدمة المصغرة للذكاء الاصطناعي)، PostgreSQL (قاعدة البيانات العلائقية)، MinIO (التخزين الكائني S3)، Camunda 7 (محرك BPM)، Docker (الحاويات)، وRabbitMQ (وسيط الرسائل غير المتزامنة)."),

    arHeading("النتائج والمخرجات"),
    arBullet("تسليم 45 قصة مستخدم عبر 4 دورات تطوير تمثّل 273 نقطة جهد، بمعدل تحقق 100%."),
    arBullet("استراتيجية اختبار شاملة: 50 اختبار وحدة و84 اختبار وظيفي بمعدل نجاح إجمالي 99%."),
    arBullet("نموذج LightGBM: AUC-ROC تساوي 0.8222 — يتفوق على الانحدار اللوجستي (0.65) وXGBoost (0.7966)."),
    arBullet("معمارية مفككة وقابلة للتطوير، جاهزة للنشر الصناعي في البيئة المصرفية المنظَّمة."),
  ];
}

// ── ASSEMBLE AND WRITE ────────────────────────────────────────────────────────
const doc = new Document({
  styles: {
    default: {
      document: { run: { font: "Arial", size: 22, color: TEXT_DARK } }
    }
  },
  sections: [
    {
      properties: {
        page: {
          size: { width: 12240, height: 15840 },
          margin: { top: 1008, right: 1008, bottom: 1008, left: 1008 }
        }
      },
      children: [
        ...makeCoverPage(),
        ...makeFrench(),
        ...makeEnglish(),
        ...makeArabic(),
      ]
    }
  ]
});

Packer.toBuffer(doc).then(buffer => {
  fs.writeFileSync(
  "Resume_Projet_BNPL_Trilingue.docx",
  buffer
);
  console.log("Done!");
});