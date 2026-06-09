const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
  HeadingLevel, AlignmentType, PageNumber, Header, Footer,
  WidthType, BorderStyle, ShadingType, VerticalAlign, ImageRun, PageBreak
} = require('docx');
const fs = require('fs');
const path = require('path');

const OUT_DIR = __dirname;
const OUT_FILE = path.join(OUT_DIR, 'phase5_evaluation_complete.docx');
const FIG_DIR = path.join(OUT_DIR, 'figures');
fs.mkdirSync(FIG_DIR, { recursive: true });

const MISSING_FIGURES = [];

const TNR = "Times New Roman", SZ = 24, SZH1 = 34, SZH2 = 28, SZH3 = 26;
const LINE = 360, AFT = 140;

const p = (text, opts = {}) => new Paragraph({
  alignment: AlignmentType.JUSTIFIED,
  spacing: { line: LINE, before: 0, after: AFT },
  children: [new TextRun({ text, font: TNR, size: SZ,
    bold: opts.bold || false, italics: opts.italic || false,
    color: opts.color || "000000" })]
});

const h1 = (t) => new Paragraph({ alignment: AlignmentType.CENTER,
  spacing: { before: 500, after: 300 },
  children: [new TextRun({ text: t, font: TNR, size: SZH1, bold: true, color: "1F3864" })]
});

const h2 = (t) => new Paragraph({ heading: HeadingLevel.HEADING_2,
  spacing: { before: 380, after: 180 },
  children: [new TextRun({ text: t, font: TNR, size: SZH2, bold: true, color: "1F3864" })]
});

const h3 = (t) => new Paragraph({ heading: HeadingLevel.HEADING_3,
  spacing: { before: 260, after: 140 },
  children: [new TextRun({ text: t, font: TNR, size: SZH3, bold: true, italics: true, color: "2E4057" })]
});

const sp = () => new Paragraph({ spacing: { before: 0, after: 80 },
  children: [new TextRun({ text: "", font: TNR, size: SZ })] });

const caption = (text) => new Paragraph({ alignment: AlignmentType.CENTER,
  spacing: { line: LINE, before: 60, after: 220 },
  children: [new TextRun({ text, font: TNR, size: 20, italics: true, color: "555555" })]
});

const note = (emoji, title, text, color) => new Paragraph({
  alignment: AlignmentType.JUSTIFIED,
  spacing: { line: LINE, before: 180, after: 180 },
  shading: { fill: "EEF6FF", type: ShadingType.CLEAR },
  children: [
    new TextRun({ text: emoji + " " + title + " — ", font: TNR, size: SZ, bold: true, color }),
    new TextRun({ text, font: TNR, size: SZ, color: "333333" })
  ]
});

const formule = (text) => new Paragraph({ alignment: AlignmentType.CENTER,
  spacing: { line: LINE, before: 120, after: 120 },
  shading: { fill: "EEF2F7", type: ShadingType.CLEAR },
  children: [new TextRun({ text, font: "Courier New", size: 22, bold: true, color: "1F3864" })]
});

const BD = { style: BorderStyle.SINGLE, size: 1, color: "CCCCCC" };
const BS = { top: BD, bottom: BD, left: BD, right: BD };

const cell = (text, opts = {}) => new TableCell({
  borders: BS,
  shading: opts.header ? { fill: "1F3864", type: ShadingType.CLEAR }
         : opts.shade  ? { fill: "EEF2F7", type: ShadingType.CLEAR }
         : { fill: "FFFFFF", type: ShadingType.CLEAR },
  margins: { top: 90, bottom: 90, left: 130, right: 130 },
  verticalAlign: VerticalAlign.CENTER,
  width: opts.w ? { size: opts.w, type: WidthType.DXA } : undefined,
  children: [new Paragraph({ alignment: AlignmentType.JUSTIFIED,
    spacing: { line: 276, before: 0, after: 0 },
    children: [new TextRun({ text, font: TNR, size: 20,
      bold: opts.bold || opts.header || false,
      color: opts.header ? "FFFFFF" : "000000" })] })]
});

const hrow = (labels, widths) => new TableRow({ tableHeader: true,
  children: labels.map((l, i) => cell(l, { header: true, w: widths ? widths[i] : undefined }))
});

const row = (cells, shade = false) => new TableRow({
  children: cells.map(c => cell(c, { shade }))
});

const resolveFigurePath = (filename) => {
  const base = path.basename(filename);
  const candidates = [
    path.join(FIG_DIR, base),
    path.join(OUT_DIR, base),
    filename,
  ];
  for (const candidate of candidates) {
    if (candidate && fs.existsSync(candidate)) return candidate;
  }
  return null;
};

const img = (filePath, w, h, label) => {
  const resolved = resolveFigurePath(filePath);
  const name = label || path.basename(String(filePath));
  if (!resolved) {
    if (!MISSING_FIGURES.includes(name)) MISSING_FIGURES.push(name);
    return caption(`[Figure : ${name} — copier le PNG dans le dossier figures/]`);
  }
  return new Paragraph({ alignment: AlignmentType.CENTER,
    spacing: { before: 200, after: 80 },
    children: [new ImageRun({
      data: fs.readFileSync(resolved),
      transformation: { width: w, height: h },
    })] });
};

const imgIf = (filename, w, h) => img(filename, w, h, path.basename(filename));

// ── TABLEAUX ─────────────────────────────────────────────────

const tRoutage = new Table({ width: { size: 9026, type: WidthType.DXA },
  columnWidths: [1400, 2200, 2800, 2626],
  rows: [
    hrow(["Zone", "Probabilite de defaut", "Comportement du systeme", "Role de l'analyste"],
         [1400, 2200, 2800, 2626]),
    row(["Verte", "PD < 8 %", "Transmission avec recommandation d'acceptation", "Instruit et valide"], false),
    row(["Orange", "8 % <= PD < 30 %", "Transmission pour examen approfondi", "Instruit, analyse et decide"], true),
    row(["Rouge", "PD >= 30 %", "Rejet automatique — sans intervention humaine", "Aucun — decision finale du modele"], false),
  ]
});

const tErreurs = new Table({ width: { size: 9026, type: WidthType.DXA },
  columnWidths: [1500, 2100, 2100, 3326],
  rows: [
    hrow(["Type d'erreur", "Definition", "Zone concernee", "Impact operationnel"],
         [1500, 2100, 2100, 3326]),
    row(["Vrai Positif (VP)", "Predit defaut -> defaut reel", "Rouge", "Rejet correct — risque evite pour l'UIB"], false),
    row(["Vrai Negatif (VN)", "Predit non-defaut -> rembourse", "Verte / Orange", "Routage correct vers l'analyste"], true),
    row(["Faux Positif (FP)", "Predit defaut -> rembourse", "Rouge", "Client solvable rejete definitivement — perte commerciale directe"], false),
    row(["Faux Negatif (FN)", "Predit non-defaut -> defaut reel", "Verte / Orange", "Transmis sans alerte — analyste reste filet de securite"], true),
  ]
});

const tBase = new Table({ width: { size: 9026, type: WidthType.DXA },
  columnWidths: [1500, 2000, 5526],
  rows: [
    hrow(["Terme", "Abreviation", "Signification concrete"], [1500, 2000, 5526]),
    row(["Vrai Positif", "VP", "Modele predit defaut -> client fait defaut (OK)"], false),
    row(["Vrai Negatif", "VN", "Modele predit non-defaut -> client rembourse (OK)"], true),
    row(["Faux Positif", "FP", "Modele predit defaut -> client rembourse (rejet injustifie)"], false),
    row(["Faux Negatif", "FN", "Modele predit non-defaut -> client fait defaut (defaut manque)"], true),
  ]
});

const tAUC = new Table({ width: { size: 9026, type: WidthType.DXA },
  columnWidths: [3000, 2000, 4026],
  rows: [
    hrow(["Modele", "AUC-ROC", "Interpretation"], [3000, 2000, 4026]),
    row(["Regression logistique", "0,6500", "65 % des paires correctement classees — proche du hasard"], false),
    row(["XGBoost", "0,7966", "79,66 % des paires correctement classees — bon"], true),
    row(["LightGBM (retenu)", "0,8222", "82,22 % des paires correctement classees — meilleur"], false),
  ]
});

const tPrecision = new Table({ width: { size: 9026, type: WidthType.DXA },
  columnWidths: [2800, 2000, 4226],
  rows: [
    hrow(["Modele", "Precision (defaut)", "Lecture operationnelle — zone rouge"],
         [2800, 2000, 4226]),
    row(["Regression logistique", "0,11", "89 bons clients rejetes sur 100 alertes -> inacceptable"], false),
    row(["XGBoost", "0,31", "69 bons clients rejetes sur 100 alertes -> insuffisant"], true),
    row(["LightGBM (retenu)", "0,41", "59 bons clients rejetes sur 100 alertes -> meilleur resultat"], false),
  ]
});

const tRecall = new Table({ width: { size: 9026, type: WidthType.DXA },
  columnWidths: [2800, 1800, 2000, 2426],
  rows: [
    hrow(["Modele", "Recall (defaut)", "Defauts detectes", "Defauts transmis sans alerte"],
         [2800, 1800, 2000, 2426]),
    row(["Regression logistique", "0,68", "~4 149 / 6 102", "~1 953 dossiers"], false),
    row(["XGBoost", "0,74", "~4 515 / 6 102", "~1 587 dossiers"], true),
    row(["LightGBM (retenu)", "0,60", "~3 661 / 6 102", "~2 441 dossiers"], false),
  ]
});

const tF1 = new Table({ width: { size: 9026, type: WidthType.DXA },
  columnWidths: [2600, 1500, 1500, 1600, 1826],
  rows: [
    hrow(["Modele", "Precision", "Recall", "F1-score", "Lecture"], [2600, 1500, 1500, 1600, 1826]),
    row(["Regression logistique", "0,11", "0,68", "0,19", "Trop de rejets injustifies"], false),
    row(["XGBoost", "0,31", "0,74", "0,44", "Correct"], true),
    row(["LightGBM (retenu)", "0,41", "0,60", "0,49", "Meilleur equilibre operationnel"], false),
  ]
});

const tMatrice = new Table({ width: { size: 9026, type: WidthType.DXA },
  columnWidths: [2000, 3513, 3513],
  rows: [
    hrow(["", "Predit : Non-defaut (verte/orange)", "Predit : Defaut (rouge)"],
         [2000, 3513, 3513]),
    row(["Reel : Non-defaut", "VN — Routage correct vers analyste", "FP — Rejet automatique injustifie"], false),
    row(["Reel : Defaut", "FN — Transmis sans alerte (analyste instruit)", "VP — Rejet automatique correct"], true),
  ]
});

const tReport = new Table({ width: { size: 9026, type: WidthType.DXA },
  columnWidths: [3000, 1500, 1500, 1500, 1526],
  rows: [
    hrow(["Classe", "Precision", "Rappel", "F1-score", "Support"],
         [3000, 1500, 1500, 1500, 1526]),
    row(["Non-defaut (classe 0)", "0,92", "0,85", "0,88", "34 541"], false),
    row(["Defaut (classe 1)", "0,41", "0,60", "0,49", "6 102"], true),
    row(["Accuracy globale", "—", "—", "0,81", "40 643"], false),
    row(["Macro average", "0,67", "0,72", "0,69", "40 643"], true),
    row(["Weighted average", "0,85", "0,81", "0,82", "40 643"], false),
  ]
});

const tDecision = new Table({ width: { size: 9026, type: WidthType.DXA },
  columnWidths: [2900, 900, 1800, 1700, 1726],
  rows: [
    hrow(["Critere", "Poids", "Regression log.", "XGBoost", "LightGBM"],
         [2900, 900, 1800, 1700, 1726]),
    row(["AUC-ROC (discrimination globale)", "x5", "0,65 --", "0,7966 ok", "0,8222 +++"], false),
    row(["Precision — fiabilite rejet auto", "x5", "0,11 --", "0,31 ok", "0,41 +++"], true),
    row(["Recall — detection des defauts", "x4", "0,68 ok", "0,74 +++", "0,60 ok"], false),
    row(["F1-score — equilibre global", "x4", "0,19 --", "0,44 ok", "0,49 +++"], true),
    row(["Contraintes metier monotones", "x5", "Non supporte", "Non supporte", "Natif +++"], false),
    row(["Verdict final", "—", "Ecarte", "Ecarte", "RETENU"], true),
  ]
});

const tAmeliorations = new Table({ width: { size: 9026, type: WidthType.DXA },
  columnWidths: [3200, 2000, 1800, 2026],
  rows: [
    hrow(["Piste d'amelioration", "Impact attendu", "Delai", "Statut"],
         [3200, 2000, 1800, 2026]),
    row(["Donnees reelles UIB BNPL (re-entrainement)", "+++ Tres fort", "6 a 12 mois", "Prevu cycle 2"], false),
    row(["Isolation Forest (profils atypiques)", "++ Fort", "Cycle 2 immediat", "En developpement"], true),
    row(["Remontee seuil zone rouge (30% -> 40%)", "+ Modere immediat", "Maintenant", "Arbitrage metier"], false),
    row(["Analyse SHAP des faux positifs", "++ Fort", "Cycle 2", "En developpement"], true),
    row(["Enrichissement features (bureau credit TN)", "++ Fort", "Moyen terme", "A planifier"], false),
  ]
});

// ── DOCUMENT ──────────────────────────────────────────────────
const doc = new Document({
  styles: {
    default: { document: { run: { font: TNR, size: SZ } } },
    paragraphStyles: [
      { id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: SZH2, bold: true, font: TNR, color: "1F3864" },
        paragraph: { spacing: { before: 380, after: 180 }, outlineLevel: 1 } },
      { id: "Heading3", name: "Heading 3", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: SZH3, bold: true, italics: true, font: TNR, color: "2E4057" },
        paragraph: { spacing: { before: 260, after: 140 }, outlineLevel: 2 } },
    ]
  },
  sections: [{
    properties: { page: { size: { width: 11906, height: 16838 },
      margin: { top: 1440, right: 1440, bottom: 1440, left: 1701 } } },
    headers: { default: new Header({ children: [new Paragraph({
      alignment: AlignmentType.RIGHT, spacing: { after: 100 },
      children: [new TextRun({ text: "Phase 5 — Evaluation | Pre-scoring BNPL UIB",
        font: TNR, size: 18, italics: true, color: "888888" })]
    })] }) },
    footers: { default: new Footer({ children: [new Paragraph({
      alignment: AlignmentType.CENTER,
      children: [
        new TextRun({ text: "Page ", font: TNR, size: 18, color: "888888" }),
        new TextRun({ children: [PageNumber.CURRENT], font: TNR, size: 18, color: "888888" }),
        new TextRun({ text: " / ", font: TNR, size: 18, color: "888888" }),
        new TextRun({ children: [PageNumber.TOTAL_PAGES], font: TNR, size: 18, color: "888888" }),
      ]
    })] }) },
    children: [

      h1("Phase 5 — Evaluation (Evaluation)"),

      h2("5.1 Principe fondamental — Architecture decisionnelle du systeme"),
      p("Avant de presenter les metriques d'evaluation, il est indispensable de comprendre l'architecture decisionnelle exacte du systeme de pre-scoring BNPL UIB. Contrairement a un outil de scoring classique qui produirait uniquement une recommandation, le systeme implemente trois comportements distincts et automatises selon la zone de risque calculee pour chaque dossier."),
      sp(), tRoutage, sp(),
      p("Cette architecture tripartite a des consequences directes sur l'interpretation des metriques d'evaluation. Les dossiers en zone verte et orange passent systematiquement devant un analyste credit, qui reste le filet de securite final. Les dossiers en zone rouge sont rejetes sans instruction humaine : le modele prend seul la decision finale pour cette categorie. C'est cette asymetrie qui conditionne l'ensemble des choix methodologiques de la phase de modelisation et qui donne a la precision du modele une dimension commerciale autant que statistique."),
      note("Principe cle", "Routage", "Le systeme de pre-scoring n'est pas un systeme de decision automatique total. C'est un systeme de routage intelligent : il decide seul uniquement pour les cas les plus certains (zone rouge - rejet automatique), et oriente l'analyste humain pour tous les autres cas. L'analyste reste la garantie finale de la qualite des decisions d'octroi de credit.", "1F3864"),
      sp(),

      h2("5.2 Interpretation des erreurs du modele dans ce contexte"),
      p("Tout systeme de classification produit inevitablement deux types d'erreurs. Dans le contexte du routage tripartite UIB BNPL, ces erreurs n'ont pas le meme cout selon la zone dans laquelle elles se produisent."),
      sp(), tErreurs, sp(),
      p("Le Faux Positif en zone rouge est l'erreur la plus couteuse pour l'UIB. Un client solvable rejete automatiquement ne beneficie d'aucun recours humain — la decision est definitive. Cela represente une perte commerciale directe : un financement BNPL refuse a un bon client, qui se tournera vers la concurrence."),
      p("Le Faux Negatif est certes une erreur, mais son impact est significativement attenue par la presence de l'analyste credit en zones verte et orange. Un dossier presentant un risque reel mais classe en zone orange arrivera a l'analyste qui, par son expertise metier, peut identifier les signaux d'alerte que le modele n'a pas captures. Le systeme n'est pas concu pour etre infaillible — il est concu pour amplifier l'efficacite de l'instruction humaine, non pour la remplacer."),
      note("Point important", "Attention", "La precision de 0,41 du modele LightGBM signifie concretement que sur 100 dossiers rejetes automatiquement en zone rouge, 59 sont des clients solvables rejetes a tort. Ce resultat, bien qu'etant le meilleur parmi les trois modeles testes, constitue la principale limite de cette premiere iteration et justifie les pistes d'amelioration presentees en section 5.7.", "B71C1C"),
      sp(),

      h2("5.3 Metriques d'evaluation — Definitions et formules"),
      p("Quatre metriques complementaires ont ete retenues pour evaluer et comparer les trois modeles testes. Chacune repond a une question metier precise dans le contexte du routage tripartite."),
      sp(), tBase, sp(),

      h3("5.3.1 AUC-ROC — Capacite discriminante globale"),
      p("Question metier : Le modele est-il capable de distinguer les bons clients des mauvais clients sur l'ensemble de la population, independamment du seuil de rejet automatique choisi ?"),
      p("La courbe ROC est construite en faisant varier le seuil de classification de 0 a 1 et en calculant a chaque valeur le Taux de Vrais Positifs (TVP) et le Taux de Faux Positifs (TFP) :"),
      formule("TVP = VP / (VP + FN)   ->  proportion de defauts reels detectes"),
      formule("TFP = FP / (FP + VN)   ->  proportion de bons clients incorrectement signales"),
      formule("AUC = aire sous la courbe ROC   (valeur entre 0,5 et 1,0)"),
      p("Une AUC de 0,82 signifie que si l'on presente au modele un dossier qui fera defaut et un dossier sain tires au hasard, le modele attribuera une probabilite de defaut plus elevee au dossier risque dans 82 % des cas. C'est une mesure de classement pur, independante du seuil de rejet automatique."),
      sp(), tAUC, sp(),
      imgIf("courbes_roc.png", 430, 375),
      caption("Figure 1 — Courbes ROC des trois modeles. LightGBM (courbe bleue foncee) domine sur toute la plage des seuils possibles."),
      sp(),
      imgIf("comparaison_auc.png", 400, 250),
      caption("Figure 2 — Comparaison des AUC-ROC. LightGBM depasse XGBoost de +2,56 points."),
      sp(),

      h3("5.3.2 Precision — Fiabilite du rejet automatique"),
      p("Question metier : Sur 100 dossiers envoyes en zone rouge (rejet automatique), combien sont de vrais defauts correctement rejetes ?"),
      formule("Precision = VP / (VP + FP)"),
      p("C'est le critere le plus directement lie a la zone rouge et aux pertes commerciales de l'UIB. Une precision faible signifie que le modele rejette de nombreux clients solvables definitivement, sans recours humain possible. C'est donc un critere commercial autant que statistique."),
      sp(), tPrecision, sp(),
      p("La precision de 0,41 de LightGBM est la meilleure obtenue parmi les trois modeles. Sur 100 dossiers rejetes automatiquement, 41 sont de vrais defauts correctement ecartes et 59 sont des clients solvables rejetes a tort. Ce resultat, bien qu'imparfait, est nettement superieur a XGBoost (0,31) et a la regression logistique (0,11). Son amelioration constitue la priorite principale du second cycle CRISP-DM."),

      h3("5.3.3 Recall — Exhaustivite de la detection"),
      p("Question metier : Sur 100 clients qui feront reellement defaut, combien le modele parvient-il a identifier et signaler correctement ?"),
      formule("Recall = VP / (VP + FN)"),
      p("Dans le contexte du routage tripartite, le recall doit etre interprete avec nuance. Les Faux Negatifs ne sont pas tous equivalents : ceux qui tombent en zone orange sont transmis a un analyste vigilant qui peut les rattraper ; ceux qui tombent en zone verte arrivent a l'analyste sans signal d'alerte, augmentant le risque residuel non detecte."),
      sp(), tRecall, sp(),
      p("LightGBM presente un recall de 0,60, legerement inferieur a XGBoost (0,74). Ce constat doit etre mis en perspective : le seuil operationnel de LightGBM est calibre a 22,8 % precisement pour equilibrer recall et precision. Abaisser davantage le seuil augmenterait le recall mais degraaderait la precision, multipliant les rejets automatiques injustifies de clients solvables — un compromis defavorable pour l'UIB. Les 40 % de defauts non detectes par le modele arrivent a l'analyste en zone verte ou orange et font l'objet d'une instruction humaine complete."),

      h3("5.3.4 F1-score — Equilibre entre precision et recall"),
      p("Question metier : Quel modele offre le meilleur equilibre entre detecter les defauts (recall) et limiter les rejets injustifies de clients solvables (precision) ?"),
      formule("Precision = VP / (VP + FP)"),
      formule("Recall    = VP / (VP + FN)"),
      formule("F1        = 2 x (Precision x Recall) / (Precision + Recall)"),
      p("La moyenne harmonique est utilisee car elle penalise fortement les desequilibres extremes entre les deux metriques. Dans le contexte du rejet automatique, le F1 synthetise parfaitement le compromis entre la protection contre les defauts et la preservation du potentiel commercial de l'UIB."),
      sp(), tF1, sp(),
      p("LightGBM obtient le meilleur F1-score (0,49) grace a sa precision nettement superieure (0,41 contre 0,31 pour XGBoost). Sur 100 dossiers signales comme risques par LightGBM, 41 sont de vrais defauts contre seulement 31 pour XGBoost. Cette difference de 10 points represente, dans un portefeuille BNPL a volume eleve, une reduction substantielle du nombre de bons clients rejetes definitivement chaque mois."),

      h3("5.3.5 Matrice de confusion — Analyse qualitative des erreurs"),
      p("La matrice de confusion offre une vision complete de la distribution des predictions du modele. Dans le contexte du routage tripartite, elle permet d'evaluer precisement le volume de chaque type de routage et l'impact des erreurs sur l'activite de l'UIB."),
      sp(), tMatrice, sp(),
      p("Deux indicateurs complementaires calcules a partir de la matrice de LightGBM :"),
      formule("Accuracy     = (VP + VN) / (VP + VN + FP + FN) = 0,81"),
      formule("Specificite  = VN / (VN + FP)                  = 0,85"),
      p("La specificite de 0,85 signifie que le modele identifie correctement 85 % des clients solvables, limitant les rejets injustifies et preservant le potentiel commercial du portefeuille."),
      sp(),
      imgIf("matrices_confusion.png", 520, 162),
      caption("Figure 3 — Matrices de confusion des trois modeles. VN : vrais negatifs, FP : faux positifs, FN : faux negatifs, VP : vrais positifs."),
      sp(),
      p("L'analyse comparative des trois matrices revele des comportements tres contrastes dans le contexte du rejet automatique. La regression logistique genere un volume considerable de faux positifs en zone rouge : la majorite des dossiers rejetes seraient des clients solvables, ce qui est commercialement inacceptable. XGBoost ameliore cet equilibre mais reste inferieur a LightGBM. Ce dernier presente la matrice la plus favorable : ses rejets automatiques sont fondes dans la proportion la plus elevee (41 %), minimisant ainsi les pertes commerciales liees aux rejets injustifies."),
      sp(),

      h2("5.4 Rapport de classification final — LightGBM"),
      p("Le rapport de classification complet du modele LightGBM a ete produit sur le jeu de test independant, constitue de 40 643 observations dont 6 102 defauts reels, soit exactement 15 % de taux de defaut, conformement au taux cible defini lors de la preparation des donnees. Ces resultats sont directement issus de l'execution reelle du script d'entrainement train_GBMlight.py sur le dataset dataset_bnpl_tunisien_merged.csv."),
      sp(), tReport, sp(),
      p("La precision de 0,92 sur la classe non-defaut confirme que le modele identifie correctement la grande majorite des clients solvables, limitant les rejets injustifies. Le recall de 0,85 sur cette meme classe signifie que 85 % des clients solvables recoivent effectivement une zone verte ou orange et sont transmis a l'analyste, preservant le potentiel commercial du portefeuille BNPL de l'UIB. Le F1-score de 0,49 sur la classe defaut, associe a une accuracy globale de 0,81, confirme la robustesse generale du modele sur l'ensemble de la population."),
      sp(),

      h2("5.5 Resultats reels issus des captures d'entrainement"),
      p("Les resultats presentes dans ce chapitre sont directement issus de l'execution reelle des scripts d'entrainement sur le dataset BNPL UIB tunisien recalibre. Les trois modeles ont ete entraines et evalues dans les memes conditions, sur le meme decoupage train/validation/test stratifie, garantissant la comparabilite des resultats."),
      p("La regression logistique a obtenu une AUC-ROC de 0,6500 sur le jeu de test, avec un F1 de 0,19 sur la classe defaut et une precision de 0,11, confirmant l'inadequation totale de ce modele lineaire pour un usage en rejet automatique. XGBoost a obtenu une AUC-ROC de 0,7966 apres GridSearch de 216 combinaisons, avec un F1 de 0,44 et une precision de 0,31 : performances solides mais precision insuffisante pour un rejet automatique fiable, et absence de contraintes monotones. LightGBM a obtenu une AUC-ROC de 0,8222, un seuil operationnel optimise a 0,2280, une accuracy globale de 0,81 et une precision de 0,41 sur la classe defaut : le meilleur compromis obtenu pour le contexte du rejet automatique en zone rouge."),
      sp(),

      h2("5.6 Synthese et justification du choix de LightGBM"),
      p("Le tableau de decision multicritere suivant synthetise l'ensemble de l'evaluation en integrant la dimension operationnelle du rejet automatique :"),
      sp(), tDecision, sp(),
      p("LightGBM est retenu comme modele de production sur cinq arguments convergents et complementaires."),
      p("Premierement, la superiorite de l'AUC (0,8222) garantit la meilleure discrimination globale sur l'ensemble des seuils de decision possibles, offrant a l'UIB la flexibilite d'ajuster le seuil de zone rouge sans degrader la qualite relative des classements."),
      p("Deuxiemement, la precision de 0,41 sur la classe defaut est la plus elevee des trois modeles et minimise directement les pertes commerciales liees aux rejets automatiques injustifies de clients solvables en zone rouge. C'est le critere le plus impactant sur l'activite commerciale de l'UIB."),
      p("Troisiemement, le F1-score de 0,49 confirme le meilleur equilibre operationnel entre detecter les defauts et preserver les bons clients, equilibre particulierement critique dans un systeme ou le rejet automatique est definitif."),
      p("Quatriemement, les contraintes monotones natives garantissent que les decisions automatiques de rejet respectent toujours la logique financiere fondamentale, ce qui est indispensable pour la conformite reglementaire des decisions automatisees aupres de la Banque Centrale de Tunisie."),
      p("Cinquiemement, la vitesse d'inference de LightGBM est compatible avec les exigences temps reel de l'API, permettant un traitement en moins de 50 millisecondes par dossier."),
      note("Conclusion", "LightGBM retenu", "LightGBM est retenu comme modele de production du systeme de pre-scoring BNPL UIB. Son AUC de 0,8222, sa precision de 0,41 sur les rejets automatiques, son F1 de 0,49 et ses contraintes monotones integrees en font le modele le mieux adapte au contexte operationnel et reglementaire de l'UIB parmi les trois candidats evalues.", "1F3864"),
      sp(),

      h2("5.7 Limites identifiees et pistes d'amelioration de la precision"),
      p("La principale limite de cette premiere iteration est la precision de 0,41 sur la classe defaut : 59 % des dossiers rejetes automatiquement en zone rouge concernent des clients solvables. Ce resultat, directement lie a l'utilisation d'un dataset d'entrainement etranger (Home Credit) en l'absence de donnees reelles UIB BNPL, constitue l'axe d'amelioration prioritaire du second cycle CRISP-DM."),
      p("Cinq pistes concretes ont ete identifiees pour ameliorer cette precision et renforcer la fiabilite du systeme de rejet automatique :"),
      sp(), tAmeliorations, sp(),
      p("La piste la plus impactante est le re-entrainement sur les donnees reelles UIB BNPL collectees en production. Des que 500 a 1 000 dossiers reels avec leur issue connue seront disponibles dans un environnement securise conforme a la loi organique n 2004-63, un nouveau cycle d'entrainement permettra au modele d'apprendre les vrais patterns de defaut du marche tunisien, reduisant significativement les faux positifs lies aux approximations de la recalibration Home Credit."),
      p("L'integration de l'Isolation Forest, deja prevue dans l'architecture du microservice (champ foret actuellement null dans la reponse JSON de l'API), permettra de detecter les profils atypiques dont les caracteristiques sont tres eloignees du domaine d'entrainement. Pour ces profils, plutot qu'un rejet automatique peu fiable, le systeme declenchera un routage force vers l'analyste avec le signal profil inhabituel, reduisant ainsi mecaniquement les faux positifs en zone rouge."),
      p("L'ajustement du seuil de la zone rouge, actuellement fixe a PD superieure ou egale a 30 %, peut etre remonte a 40 % voire 50 % selon la politique de risque de l'UIB. Cela reduira le volume de dossiers rejetes automatiquement mais augmentera la precision sur les rejets effectifs, puisque seuls les cas les plus certains seront traites de maniere automatique. C'est un arbitrage metier parametrable immediatement sans necessiter de re-entrainement du modele."),
      note("Objectif du second cycle CRISP-DM", "Cible", "Porter la precision sur la classe defaut de 0,41 a 0,55 ou plus, en combinant le re-entrainement sur donnees reelles UIB, l'Isolation Forest, l'analyse SHAP des faux positifs et l'ajustement du seuil de la zone rouge. Ces ameliorations seront documentees dans un second rapport d'evaluation suivant la meme methodologie CRISP-DM iterative.", "1F3864"),

    ]
  }]
});

Packer.toBuffer(doc).then((buf) => {
  fs.writeFileSync(OUT_FILE, buf);
  console.log(`Document genere : ${OUT_FILE}`);
  if (MISSING_FIGURES.length > 0) {
    console.log('\nFigures manquantes (legende inseree a la place) :');
    for (const f of MISSING_FIGURES) console.log(`  - figures/${f}`);
    console.log('\nCopiez vos PNG dans :', FIG_DIR);
  }
}).catch((err) => {
  console.error("Erreur generation DOCX :", err);
  process.exit(1);
});
