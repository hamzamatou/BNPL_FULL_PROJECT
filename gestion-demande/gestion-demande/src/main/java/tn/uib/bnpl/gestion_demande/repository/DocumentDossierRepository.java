package tn.uib.bnpl.gestion_demande.repository;


import org.springframework.data.jpa.repository.JpaRepository;

import tn.uib.bnpl.gestion_demande.classes.DocumentDossier;

import java.util.List;

public interface DocumentDossierRepository extends JpaRepository<DocumentDossier, Long> {

    List<DocumentDossier> findByDossierClientId(Long dossierClientId);

    List<DocumentDossier> findByDossierClientIdAndTypeDocument(Long dossierClientId, String typeDocument);
}