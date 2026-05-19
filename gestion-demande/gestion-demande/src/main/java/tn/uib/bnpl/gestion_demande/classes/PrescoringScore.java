package tn.uib.bnpl.gestion_demande.classes;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Score ML calculé après consentement du client.
 *
 * Champs conservés depuis prescore_dossier() (prescoring_service.py) :
 *
 *   "pd_pct"      → probabiliteDefaut   : probabilité de défaut en % (ex: 23.4)
 *   "score"       → score               : score /1000 pts = round(1000 × (1 - pd))
 *   "zone"        → zoneCode/zoneLibelle: "vert" | "orange" | "rouge"
 *   "explications"→ explicationsJson    : List<String> phrases analyste (sérialisée JSON)
 *
 * Relation : DemandeFinancement 1 ←→ 1 PrescoringScore (FK ici, UNIQUE)
 */
@Entity
@Table(name = "prescoring_score")
public class PrescoringScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "demande_id", nullable = false, unique = true)
    private DemandeFinancement demande;

    /** pd_pct — probabilité de défaut en % (0.0 → 100.0). */
    @Column(nullable = false)
    private double probabiliteDefaut;

    /** score — score sur 1000 points. */
    @Column(nullable = false)
    private int score;

    /**
     * zone.code + zone.libelle concaténés pour faciliter l'affichage.
     * Ex : zoneCode = "vert", zoneLibelle = "Zone verte (PD 0 % - 30 %)"
     */
    @Column(nullable = false)
    private String zoneCode;

  

    /**
     * explications — List<String> sérialisée en JSON.
     * Contient les phrases analyste (foret + zone + SHAP) telles que retournées
     * par _explications_texte_analyste() côté Python.
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String explicationsJson;

    @Column(nullable = false)
    private LocalDateTime computedAt;

    // ─── Factory ─────────────────────────────────────────────────────────────

    public static PrescoringScore of(DemandeFinancement demande,
                                     double probabiliteDefaut,
                                     int score,
                                     String zoneCode,
                                 
                                     String explicationsJson) {
        PrescoringScore ps  = new PrescoringScore();
        ps.demande           = demande;
        ps.probabiliteDefaut = probabiliteDefaut;
        ps.score             = score;
        ps.zoneCode          = zoneCode;
        
        ps.explicationsJson  = explicationsJson;
        ps.computedAt        = LocalDateTime.now();
        return ps;
    }

    // ─── Getters / Setters ────────────────────────────────────────────────────

    public Long getId()                                   { return id; }

    public DemandeFinancement getDemande()                { return demande; }
    public void setDemande(DemandeFinancement v)          { this.demande = v; }

    public double getProbabiliteDefaut()                  { return probabiliteDefaut; }
    public void setProbabiliteDefaut(double v)            { this.probabiliteDefaut = v; }

    public int getScore()                                 { return score; }
    public void setScore(int v)                           { this.score = v; }

    public String getZoneCode()                           { return zoneCode; }
    public void setZoneCode(String v)                     { this.zoneCode = v; }


    public String getExplicationsJson()                   { return explicationsJson; }
    public void setExplicationsJson(String v)             { this.explicationsJson = v; }

    public LocalDateTime getComputedAt()                  { return computedAt; }
    public void setComputedAt(LocalDateTime v)            { this.computedAt = v; }

    public PrescoringScore() {}
}