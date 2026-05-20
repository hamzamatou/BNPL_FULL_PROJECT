package tn.uib.bnpl.gestion_demande.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tn.uib.bnpl.gestion_demande.exceptions.CoherenceAnomalyException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CoherenceAnomalyException.class)
    public ResponseEntity<Map<String, Object>> handleCoherenceAnomaly(CoherenceAnomalyException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", ex.getMessage());
        body.put("anomalies", ex.getAnomalies());
        if (!ex.getCorrections().isEmpty()) {
            body.put("corrections", ex.getCorrections());
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Erreur technique, veuillez réessayer."));
    }
}
