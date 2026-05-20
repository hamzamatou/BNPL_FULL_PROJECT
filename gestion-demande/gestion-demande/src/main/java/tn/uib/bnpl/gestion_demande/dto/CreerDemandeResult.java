package tn.uib.bnpl.gestion_demande.dto;

import tn.uib.bnpl.gestion_demande.classes.DemandeFinancement;

import java.util.List;

public record CreerDemandeResult(
        DemandeFinancement demande,
        List<String> recommandations
) {}
