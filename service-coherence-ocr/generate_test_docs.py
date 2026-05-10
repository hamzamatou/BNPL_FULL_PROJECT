"""
Genere des images JPG synthetiques pour test_micro.mjs (OCR / coherence).
Les bulletins de paie suivent une forme proche du modele tunisien (tableau Code / Libelle / Gains / Retenues, 3 decimales).
Le justificatif de loyer (loyer.jpg) comporte des paragraphes en arabe (RTL) et un bloc de donnees structurees en francais.

Lance depuis la racine du service: python generate_test_docs.py
"""

from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

OUT_DIR = Path(__file__).resolve().parent / "test-docs"
WIDTH, HEIGHT = 1200, 1700
MARGIN = 48
NET_A_PAYER = "2500.000"  # aligne avec revenu_mensuel 2500 (presets / saisie test test_micro.mjs)


def _font(size: int = 32) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    candidates = [
        Path(r"C:\Windows\Fonts\arial.ttf"),
        Path(r"C:\Windows\Fonts\calibri.ttf"),
        Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"),
        Path("/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf"),
    ]
    for p in candidates:
        if p.is_file():
            return ImageFont.truetype(str(p), size)
    return ImageFont.load_default()


def _font_arabic(size: int = 30) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    """Police avec glyphes arabes (Arial Windows convient en general)."""
    return _font(size)


def _arabic_display(text: str) -> str:
    """Presentation RTL correcte pour le rendu PIL (reshape + bidi)."""
    try:
        import arabic_reshaper
        from bidi.algorithm import get_display

        reshaped = arabic_reshaper.reshape(text)
        return get_display(reshaped)
    except Exception:
        return text


def _text_width(draw: ImageDraw.ImageDraw, text: str, font: ImageFont.ImageFont) -> int:
    bbox = draw.textbbox((0, 0), text, font=font)
    return bbox[2] - bbox[0]


def _draw_rtl_line(
    draw: ImageDraw.ImageDraw,
    y: int,
    text_logical: str,
    font: ImageFont.ImageFont,
    right_x: int,
    fill: tuple[int, int, int] = (0, 0, 0),
) -> None:
    disp = _arabic_display(text_logical)
    w = _text_width(draw, disp, font)
    draw.text((right_x - w, y), disp, font=font, fill=fill)


AR_LINE_STEP = 52  # espacement vertical entre lignes arabes


def _save_jpg(img: Image.Image, name: str) -> Path:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    path = OUT_DIR / name
    rgb = img.convert("RGB")
    rgb.save(path, "JPEG", quality=92)
    return path


def _doc(lines: list[str], title: str) -> Image.Image:
    line_h = 42
    img = Image.new("RGB", (WIDTH, HEIGHT), (255, 255, 255))
    draw = ImageDraw.Draw(img)
    font_title = _font(36)
    font_body = _font(28)
    y = MARGIN
    draw.text((MARGIN, y), title, fill=(20, 20, 120), font=font_title)
    y += line_h + 16
    for line in lines:
        draw.text((MARGIN, y), line, fill=(0, 0, 0), font=font_body)
        y += line_h
    return img


def _draw_table_header(draw: ImageDraw.ImageDraw, y: int, font: ImageFont.ImageFont) -> int:
    cols_x = [MARGIN, MARGIN + 90, MARGIN + 520, MARGIN + 720, MARGIN + 900]
    headers = ["Code", "Libellé", "Nombre", "Gains", "Retenues"]
    w = WIDTH - MARGIN - cols_x[0]
    draw.rectangle([MARGIN, y, WIDTH - MARGIN, y + 36], outline=(0, 0, 0), width=2)
    for i, h in enumerate(headers):
        draw.text((cols_x[i] + 4, y + 6), h, fill=(0, 0, 0), font=font)
    return y + 36


def _draw_table_row(
    draw: ImageDraw.ImageDraw,
    y: int,
    cells: tuple[str, str, str, str, str],
    font: ImageFont.ImageFont,
    cols_x: list[int],
    row_h: int = 34,
) -> int:
    draw.rectangle([MARGIN, y, WIDTH - MARGIN, y + row_h], outline=(0, 0, 0), width=1)
    for i, text in enumerate(cells):
        draw.text((cols_x[i] + 4, y + 6), text, fill=(0, 0, 0), font=font)
    return y + row_h


def bulletin_paie_tunis(month_name: str, date_edition: str, mois_paie: str) -> Image.Image:
    """Modele proche du bulletin tunisien (Salaire Mensuel, tableau, Net a Payer)."""
    img = Image.new("RGB", (WIDTH, HEIGHT), (255, 255, 255))
    draw = ImageDraw.Draw(img)
    f_title = _font(34)
    f_meta = _font(22)
    f_small = _font(20)
    f_cell = _font(19)

    y = 36
    title = "BULLETIN DE PAIE"
    bb = draw.textbbox((0, 0), title, font=f_title)
    tw = bb[2] - bb[0]
    draw.text(((WIDTH - tw) // 2, y), title, fill=(0, 0, 0), font=f_title)
    y += 50

    # Bloc droit (comme sur le modele)
    rx = WIDTH - MARGIN - 380
    draw.rectangle([rx, y, WIDTH - MARGIN, y + 120], outline=(100, 100, 100), width=1)
    draw.text((rx + 8, y + 8), f"Edite le: {date_edition}", fill=(0, 0, 0), font=f_meta)
    draw.text((rx + 8, y + 36), "Annee: 2026", fill=(0, 0, 0), font=f_meta)
    draw.text((rx + 8, y + 60), f"Mois: {mois_paie}", fill=(0, 0, 0), font=f_meta)
    draw.text((rx + 8, y + 84), "Nature Paie: Salaire Mensuel", fill=(0, 0, 0), font=f_meta)
    y += 130

    # Zone employeur / employe
    draw.rectangle([MARGIN, y, WIDTH - MARGIN, y + 140], outline=(80, 80, 80), width=1)
    ey = y + 10
    draw.text((MARGIN + 10, ey), "Matricule Employeur: 00012345", font=f_meta, fill=(0, 0, 0))
    draw.text((MARGIN + 10, ey + 28), "Statut: Personnel Mensuel    Regime Horaire: 40", font=f_meta, fill=(0, 0, 0))
    draw.text((MARGIN + 10, ey + 56), "Matricule: 9876", font=f_meta, fill=(0, 0, 0))
    draw.text((MARGIN + 10, ey + 84), "Nom / Prenom: ALI SAMI", font=f_meta, fill=(0, 0, 0))
    draw.text((MARGIN + 10, ey + 112), "N SS: 1234567890123    Affectation: Direction Financiere", font=f_meta, fill=(0, 0, 0))
    y += 150

    draw.text((MARGIN, y), "Emploi: Ingenieur", font=f_meta, fill=(0, 0, 0))
    y += 32
    draw.text(
        (MARGIN, y),
        "Nombre: 30.00    Categorie / Echelle / Echelon: ---    Salaire de base: 3000.000",
        font=f_meta,
        fill=(0, 0, 0),
    )
    y += 44

    cols_x = [MARGIN, MARGIN + 88, MARGIN + 500, MARGIN + 700, MARGIN + 880]
    y = _draw_table_header(draw, y, f_small)

    # Lignes type modele (3 decimales) — aboutissent au Net a payer = NET_A_PAYER pour l’OCR
    rows: list[tuple[str, str, str, str, str]] = [
        ("100", "Salaire de base", "30.00", "3000.000", "0.000"),
        ("200", "Salaire Brut", "0.00", "3000.000", "0.000"),
        ("310", "C.N.S.S.", "0.00", "0.000", "275.000"),
        ("240", "Salaire Imposable", "0.00", "2725.000", "0.000"),
        ("350", "Impôts", "0.00", "0.000", "225.000"),
        ("400", "Net a payer avant deductions", "0.00", "2500.000", "0.000"),
        ("410", "Net a payer hors arrondi", "0.00", "2500.000", "0.000"),
        ("500", "Net a Payer", "0.00", NET_A_PAYER, "0.000"),
    ]
    for row in rows:
        y = _draw_table_row(draw, y, row, f_cell, cols_x)

    y += 16
    draw.rectangle([WIDTH - MARGIN - 320, y, WIDTH - MARGIN, y + 40], outline=(0, 0, 0), width=2)
    draw.text((WIDTH - MARGIN - 308, y + 8), f"Net a Payer: {NET_A_PAYER}", font=f_meta, fill=(0, 0, 0))

    y += 60
    draw.rectangle([MARGIN, y, MARGIN + 340, y + 100], outline=(120, 120, 120), width=1)
    draw.text((MARGIN + 8, y + 8), "Conges en jours depuis le debut de l'annee", font=f_small, fill=(0, 0, 0))
    draw.text((MARGIN + 8, y + 36), "Droit: 1.50    Pris: 0.00    Solde: 1.50", font=f_small, fill=(0, 0, 0))

    bx = MARGIN + 380
    draw.rectangle([bx, y, WIDTH - MARGIN, y + 100], outline=(120, 120, 120), width=1)
    draw.text((bx + 8, y + 8), "Mode de paiement: Virement", font=f_small, fill=(0, 0, 0))
    draw.text((bx + 8, y + 36), "Banque / Agence / Compte: STB ---", font=f_small, fill=(0, 0, 0))

    y += 120
    draw.text((MARGIN, y), f"Bulletin — periode: {month_name} (document de test OCR)", font=f_small, fill=(100, 100, 100))
    draw.text((MARGIN, y + 24), "Emargement: ___________________", font=f_small, fill=(0, 0, 0))

    return img


def justificatif_loyer_image() -> Image.Image:
    """
    Quitittance bilingue : paragraphes en arabe (RTL) + bloc donnees structure (FR).
    Montant 600 TND aligne avec les tests.
    """
    img = Image.new("RGB", (WIDTH, HEIGHT), (255, 255, 255))
    draw = ImageDraw.Draw(img)
    f_title = _font(34)
    f_h = _font(26)
    f_b = _font(22)
    f_ar = _font_arabic(27)
    f_ar_small = _font_arabic(23)
    f_label = _font(20)
    rx = WIDTH - MARGIN

    y = MARGIN
    draw.text((MARGIN, y), "JUSTIFICATIF DE LOYER — QUITTANCE", fill=(20, 40, 120), font=f_title)
    y += 50
    draw.text(
        (MARGIN, y),
        "Document bilingue (arabe + donnees structurees en francais) — test OCR",
        fill=(90, 90, 90),
        font=f_label,
    )
    y += 40

    # --- Encadre 1 : paragraphe(s) arabe ---
    box1_h = 220
    draw.rectangle([MARGIN, y, WIDTH - MARGIN, y + box1_h], outline=(55, 55, 90), width=2)
    draw.text((MARGIN + 10, y + 8), "1) Texte contractuel (arabe)", fill=(55, 55, 90), font=f_b)
    inner_y = y + 46
    arabic_intro = [
        "يشهد هذا المستند بأن المستأجر أدى معلوم الكراء الشهري المتفق عليه عن السكن المؤجر،",
        "وذلك عن الفترة المذكورة في الجدول أدناه، وقد تم استلام المبلغ بالدينار التونسي.",
    ]
    for line in arabic_intro:
        _draw_rtl_line(draw, inner_y, line, f_ar, rx - 14)
        inner_y += AR_LINE_STEP
    y += box1_h + 20

    # --- Bloc structure : tableau FR (cle / valeur) ---
    draw.text((MARGIN, y), "2) Donnees structurees (extrait)", fill=(0, 0, 0), font=f_h)
    y += 40
    rows_fr: list[tuple[str, str]] = [
        ("Locataire (Prenom / Nom)", "SAMI ALI"),
        ("CIN", "12345678"),
        ("Periode de location", "Janvier 2026"),
        ("Adresse du logement", "Tunis — cite El Khadra, immeuble 5, appartement 12"),
        ("Loyer mensuel (TND)", "600"),
        ("Reference contrat", "LOC-2025-8844"),
        ("Date de paiement / quittance", "05/01/2026"),
    ]
    row_h = 42
    col_label_w = 400
    for i, (label, value) in enumerate(rows_fr):
        ry = y + i * row_h
        draw.rectangle([MARGIN, ry, WIDTH - MARGIN, ry + row_h], outline=(110, 110, 130), width=1)
        draw.text((MARGIN + 10, ry + 10), label, font=f_b, fill=(0, 0, 0))
        draw.text((MARGIN + col_label_w, ry + 10), value, font=f_b, fill=(0, 0, 0))
    y += len(rows_fr) * row_h + 28

    # --- Encadre 2 : paragraphe arabe (confirmation / signature) ---
    box2_h = 200
    draw.rectangle([MARGIN, y, WIDTH - MARGIN, y + box2_h], outline=(55, 55, 90), width=2)
    draw.text((MARGIN + 10, y + 8), "3) Confirmation du bailleur (arabe)", fill=(55, 55, 90), font=f_b)
    inner_y = y + 46
    arabic_outro = [
        "يؤكد المؤجر استلام المبلغ الموضح أعلاه، وهذا المستند يعد إثباتا للوفاء بالكراء.",
        "حرر بتونس. الختم والتوقيع: ________________________",
    ]
    for line in arabic_outro:
        _draw_rtl_line(draw, inner_y, line, f_ar_small, rx - 14)
        inner_y += AR_LINE_STEP - 4
    y += box2_h + 24

    draw.text(
        (MARGIN, y),
        "Mentions: معلوم الكراء — loyer mensuel 600 TND (document de demonstration)",
        fill=(120, 120, 120),
        font=f_label,
    )
    return img


def main() -> int:
    cin_lines = [
        "REPUBLIQUE TUNISIENNE",
        "CARTE NATIONALE D'IDENTITE",
        "Nom: ALI",
        "Prenom: SAMI",
        "CIN: 12345678",
        "Date naissance: 01/01/1990",
    ]
    att_lines = [
        "ATTESTATION DE TRAVAIL",
        "Certifions que M. SAMI ALI",
        "CIN 12345678",
        "Exerce la fonction d'ingenieur",
        "Anciennete dans l'emploi: 36 mois",
        "Salaire mensuel net: 2500 dinars",
    ]
    devis_lines = [
        "DEVIS N 2026-042",
        "Client: SAMI ALI",
        "CIN 12345678",
        "Montant total TTC: 12000 TND",
        "Echeance mensuelle indicative: 500 TND",
    ]

    pay_specs = [
        ("pay1.jpg", "Novembre 2025", "15/11/2025", "Novembre"),
        ("pay2.jpg", "Decembre 2025", "15/12/2025", "Decembre"),
        ("pay3.jpg", "Janvier 2026", "10/01/2026", "Janvier"),
    ]

    files: list[tuple[str, Image.Image]] = [
        ("cin.jpg", _doc(cin_lines, "PIECE D'IDENTITE")),
        ("attestation.jpg", _doc(att_lines, "ATTESTATION")),
        ("loyer.jpg", justificatif_loyer_image()),
        ("devis.jpg", _doc(devis_lines, "DEVIS")),
    ]
    for name, month_label, date_ed, mois_paie in pay_specs:
        files.append((name, bulletin_paie_tunis(month_label, date_ed, mois_paie)))

    # Ordre stable pour les messages
    order = ["cin.jpg", "pay1.jpg", "pay2.jpg", "pay3.jpg", "attestation.jpg", "loyer.jpg", "devis.jpg"]
    by_name = dict(files)
    for name in order:
        p = _save_jpg(by_name[name], name)
        print(f"OK {p}")

    print(f"\n{len(order)} fichiers dans: {OUT_DIR}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
