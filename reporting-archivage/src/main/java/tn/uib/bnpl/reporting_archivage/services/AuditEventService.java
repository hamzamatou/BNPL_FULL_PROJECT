package tn.uib.bnpl.reporting_archivage.services;

import tn.uib.bnpl.reporting_archivage.dto.AuditEventRequest;

public interface AuditEventService {

    void traiterEvenement(AuditEventRequest request);
}
