package tn.uib.bnpl.gestion_demande.services;

import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.uib.bnpl.gestion_demande.config.ClientUtilisateurFeign;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Résout les analystes actifs via {@code gestion-utilisateur} en utilisant le
 * {@code codeBanque} retourné dans {@code banquesRoutees} (UIB, EL_AMEN, EL_BARAKA…).
 */
@Service
public class RoutageBanqueAnalysteResolver {

    private static final Logger log = LoggerFactory.getLogger(RoutageBanqueAnalysteResolver.class);

    private final ClientUtilisateurFeign utilisateurFeign;

    public RoutageBanqueAnalysteResolver(ClientUtilisateurFeign utilisateurFeign) {
        this.utilisateurFeign = utilisateurFeign;
    }

    public List<Long> resolveAnalysteUserIds(String codeBanque) {
        return resolveAnalystesActifs(codeBanque).stream()
                .map(ClientUtilisateurFeign.AnalysteRoutageResponse::getId)
                .filter(Objects::nonNull)
                .toList();
    }

    public List<ClientUtilisateurFeign.AnalysteRoutageResponse> resolveAnalystesActifs(String codeBanque) {
        if (codeBanque == null || codeBanque.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<ClientUtilisateurFeign.AnalysteRoutageResponse> analystes =
                    utilisateurFeign.listerAnalystesActifsParCodeBanque(codeBanque.trim());
            if (analystes == null || analystes.isEmpty()) {
                log.warn("Aucun analyste pour codeBanque={}", codeBanque);
                return Collections.emptyList();
            }
            return analystes;
        } catch (FeignException.NotFound ex) {
            log.error("Endpoint analystes introuvable sur gestion-utilisateur — redémarrer le service 8080. codeBanque={}",
                    codeBanque);
            return Collections.emptyList();
        } catch (FeignException ex) {
            log.error("Erreur Feign analystes codeBanque={} : {}", codeBanque, ex.getMessage());
            return Collections.emptyList();
        }
    }
}
