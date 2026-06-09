package tn.uib.bnpl.gestion_demande.camunda;

import java.util.LinkedHashMap;
import java.util.Map;

final class CamundaVariableMapper {

    private CamundaVariableMapper() {}

    static Map<String, Object> toCamundaVariables(Map<String, Object> variables) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (variables == null) {
            return out;
        }
        for (Map.Entry<String, Object> e : variables.entrySet()) {
            out.put(e.getKey(), wrap(e.getValue()));
        }
        return out;
    }

    private static Map<String, Object> wrap(Object value) {
        Map<String, Object> v = new LinkedHashMap<>();
        if (value == null) {
            v.put("value", null);
            v.put("type", "Null");
            return v;
        }
        if (value instanceof Boolean b) {
            v.put("value", b);
            v.put("type", "Boolean");
        } else if (value instanceof Integer i) {
            v.put("value", i);
            v.put("type", "Integer");
        } else if (value instanceof Number n) {
            v.put("value", n.doubleValue());
            v.put("type", "Double");
        } else if (value instanceof Long l) {
            v.put("value", l);
            v.put("type", "Long");
        } else if (value instanceof Double d) {
            v.put("value", d);
            v.put("type", "Double");
        } else {
            v.put("value", value.toString());
            v.put("type", "String");
        }
        return v;
    }
}
