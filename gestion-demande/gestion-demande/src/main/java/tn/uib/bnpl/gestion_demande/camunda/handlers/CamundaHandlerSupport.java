package tn.uib.bnpl.gestion_demande.camunda.handlers;

final class CamundaHandlerSupport {

    private CamundaHandlerSupport() {
    }

    static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static String toString(Object value) {
        return value != null ? value.toString() : null;
    }
}
