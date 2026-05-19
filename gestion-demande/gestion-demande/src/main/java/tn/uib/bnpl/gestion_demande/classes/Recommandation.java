package tn.uib.bnpl.gestion_demande.classes;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Recommandation financière calculée avant création de la demande.
 *
 * Seul le résultat final est persisté :
 *   "recommandations" → recommandationsJson
 *       List<String> retournée par generer_recommandations() (service_recommendation.py).
 *       Ex : ["Option 1 : réduire le montant à 4800 TND sur 12 mois.",
 *             "Option 2 : allonger la durée à 18 mois."]
 *
 * Relation : DemandeFinancement 1 ←→ 1 Recommandation (FK ici, UNIQUE)
 */
@Entity
@Table(name = "recommandation")
public class Recommandation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "demande_id", nullable = false, unique = true)
    private DemandeFinancement demande;

    /**
     * recommandations — List<String> sérialisée en JSON.
     * Contient les recommandations textuelles finales (LLM ou fallback).
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String recommandationsJson;

    @Column(nullable = false)
    private LocalDateTime generatedAt;

    // ─── Factory ─────────────────────────────────────────────────────────────

    public static Recommandation of(DemandeFinancement demande,
                                    String recommandationsJson) {
        Recommandation r      = new Recommandation();
        r.demande             = demande;
        r.recommandationsJson = recommandationsJson;
        r.generatedAt         = LocalDateTime.now();
        return r;
    }

    // ─── Getters / Setters ────────────────────────────────────────────────────

    public Long getId()                                   { return id; }

    public DemandeFinancement getDemande()                { return demande; }
    public void setDemande(DemandeFinancement v)          { this.demande = v; }

    public String getRecommandationsJson()                { return recommandationsJson; }
    public void setRecommandationsJson(String v)          { this.recommandationsJson = v; }

    public LocalDateTime getGeneratedAt()                 { return generatedAt; }
    public void setGeneratedAt(LocalDateTime v)           { this.generatedAt = v; }

    public Recommandation() {}
}