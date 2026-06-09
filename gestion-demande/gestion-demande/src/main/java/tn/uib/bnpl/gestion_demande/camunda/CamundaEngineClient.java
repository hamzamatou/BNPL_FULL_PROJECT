package tn.uib.bnpl.gestion_demande.camunda;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "camunda.enabled", havingValue = "true")
public class CamundaEngineClient {

    private static final Logger log = LoggerFactory.getLogger(CamundaEngineClient.class);

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_MAP =
            new ParameterizedTypeReference<>() {};

    private final RestTemplate restTemplate;
    private final CamundaProperties properties;

    public CamundaEngineClient(RestTemplate camundaRestTemplate, CamundaProperties properties) {
        this.restTemplate = camundaRestTemplate;
        this.properties = properties;
    }

    public String startProcess(String businessKey, Map<String, Object> variables) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("businessKey", businessKey);
        body.put("variables", CamundaVariableMapper.toCamundaVariables(variables));

        String url = properties.getBaseUrl()
                + "/process-definition/key/" + properties.getProcessKey() + "/start";

        ResponseEntity<Map> response = restTemplate.postForEntity(url, body, Map.class);
        Map<?, ?> result = response.getBody();
        if (result == null || result.get("id") == null) {
            throw new IllegalStateException("Démarrage processus Camunda sans id d'instance");
        }
        return result.get("id").toString();
    }

    public List<Map<String, Object>> getTasks(String processInstanceId, String taskDefinitionKey) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(properties.getBaseUrl() + "/task")
                .queryParam("processInstanceId", processInstanceId);
        if (taskDefinitionKey != null && !taskDefinitionKey.isBlank()) {
            builder.queryParam("taskDefinitionKey", taskDefinitionKey);
        }
        ResponseEntity<List<Map<String, Object>>> response =
                restTemplate.exchange(builder.toUriString(), HttpMethod.GET, null, LIST_MAP);
        return response.getBody() != null ? response.getBody() : List.of();
    }

    public void completeUserTask(String taskId, Map<String, Object> variables) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (variables != null && !variables.isEmpty()) {
            body.put("variables", CamundaVariableMapper.toCamundaVariables(variables));
        }
        String url = properties.getBaseUrl() + "/task/" + taskId + "/complete";
        restTemplate.postForEntity(url, body, Void.class);
    }

    public List<Map<String, Object>> getExternalTasks(String processInstanceId, String topic, String activityId) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(properties.getBaseUrl() + "/external-task")
                .queryParam("processInstanceId", processInstanceId);
        if (topic != null && !topic.isBlank()) {
            builder.queryParam("topicName", topic);
        }
        if (activityId != null && !activityId.isBlank()) {
            builder.queryParam("activityId", activityId);
        }
        ResponseEntity<List<Map<String, Object>>> response =
                restTemplate.exchange(builder.toUriString(), HttpMethod.GET, null, LIST_MAP);
        return response.getBody() != null ? response.getBody() : List.of();
    }

    public void lockExternalTask(String externalTaskId, long lockDurationMs) {
        Map<String, Object> body = Map.of(
                "workerId", properties.getWorkerId(),
                "lockDuration", lockDurationMs
        );
        String url = properties.getBaseUrl() + "/external-task/" + externalTaskId + "/lock";
        restTemplate.postForEntity(url, body, Void.class);
    }

    public Optional<Map<String, Object>> fetchAndLockExternalTask(String topic, long lockDurationMs) {
        Map<String, Object> topicReq = new LinkedHashMap<>();
        topicReq.put("topicName", topic);
        topicReq.put("lockDuration", lockDurationMs);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("workerId", properties.getWorkerId());
        body.put("maxTasks", 1);
        body.put("topics", List.of(topicReq));

        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                properties.getBaseUrl() + "/external-task/fetchAndLock",
                HttpMethod.POST,
                new HttpEntity<>(body),
                LIST_MAP);

        List<Map<String, Object>> tasks = response.getBody();
        if (tasks == null || tasks.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(tasks.get(0));
    }

    public void completeExternalTask(String externalTaskId, Map<String, Object> variables) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("workerId", properties.getWorkerId());
        if (variables != null && !variables.isEmpty()) {
            body.put("variables", CamundaVariableMapper.toCamundaVariables(variables));
        }
        String url = properties.getBaseUrl() + "/external-task/" + externalTaskId + "/complete";
        restTemplate.postForEntity(url, body, Void.class);
    }

    public void completeExternalTaskForProcess(String processInstanceId, String topic,
                                               String activityId, Map<String, Object> variables) {
        completeExternalTaskForProcess(processInstanceId, topic, activityId, variables, 15_000);
    }

    /**
     * Attend l'apparition de la tâche externe (le moteur la crée après la transition précédente).
     */
    public void completeExternalTaskForProcess(String processInstanceId, String topic,
                                               String activityId, Map<String, Object> variables,
                                               long maxWaitMs) {
        long deadline = System.currentTimeMillis() + maxWaitMs;
        while (System.currentTimeMillis() < deadline) {
            List<Map<String, Object>> tasks = findExternalTasks(processInstanceId, topic, activityId);
            if (!tasks.isEmpty()) {
                String externalTaskId = tasks.get(0).get("id").toString();
                try {
                    lockExternalTask(externalTaskId, 120_000);
                    completeExternalTask(externalTaskId, variables);
                    log.info("External task complétée — topic={} activityId={} instance={}",
                            topic, activityId, processInstanceId);
                    return;
                } catch (RestClientException ex) {
                    log.warn("Échec lock/complete external task {} : {}", externalTaskId, ex.getMessage());
                }
            }
            sleepBriefly(250);
        }
        log.warn(
                "External task introuvable après {} ms — topic={} activityId={} instance={} (worker de secours ou redéployer BPMN)",
                maxWaitMs, topic, activityId, processInstanceId);
    }

    private List<Map<String, Object>> findExternalTasks(String processInstanceId, String topic, String activityId) {
        List<Map<String, Object>> byTopic = getExternalTasks(processInstanceId, topic, null);
        if (!byTopic.isEmpty()) {
            return byTopic;
        }
        if (activityId != null && !activityId.isBlank()) {
            return getExternalTasks(processInstanceId, null, activityId);
        }
        return List.of();
    }

    private static void sleepBriefly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    public void completeUserTaskForProcess(String processInstanceId, String taskDefinitionKey,
                                           Map<String, Object> variables) {
        List<Map<String, Object>> tasks = getTasks(processInstanceId, taskDefinitionKey);
        for (Map<String, Object> task : tasks) {
            completeUserTask(task.get("id").toString(), variables);
        }
    }

    /**
     * Corrèle un message BPMN (ex. sortie du sous-processus fenêtre 48 h).
     */
    public void correlateMessage(String processInstanceId, String messageName,
                                 Map<String, Object> variables) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messageName", messageName);
        body.put("processInstanceId", processInstanceId);
        if (variables != null && !variables.isEmpty()) {
            body.put("processVariables", CamundaVariableMapper.toCamundaVariables(variables));
        }
        String url = properties.getBaseUrl() + "/message";
        restTemplate.postForEntity(url, body, Void.class);
        log.info("Message Camunda corrélé — name={} instance={}", messageName, processInstanceId);
    }

    public Object getProcessVariable(String processInstanceId, String name) {
        String url = properties.getBaseUrl()
                + "/process-instance/" + processInstanceId + "/variables/" + name;
        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
        Map<?, ?> body = response.getBody();
        return body != null ? body.get("value") : null;
    }

    public void setProcessVariables(String processInstanceId, Map<String, Object> variables) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("modifications", CamundaVariableMapper.toCamundaVariables(variables));
        String url = properties.getBaseUrl() + "/process-instance/" + processInstanceId + "/variables";
        restTemplate.postForEntity(url, body, Void.class);
    }

    /**
     * Attend la fin d'une tâche externe (worker ou API).
     * {@code completionVariable} : secours si le worker a fini entre deux polls (course rapide).
     */
    public void waitForExternalTaskProcessed(String processInstanceId, String topic,
                                             String activityId, long maxWaitMs,
                                             String completionVariable) {
        long deadline = System.currentTimeMillis() + maxWaitMs;
        boolean seen = false;
        while (System.currentTimeMillis() < deadline) {
            if (completionVariable != null
                    && isTruthy(getProcessVariableQuiet(processInstanceId, completionVariable))) {
                log.info("Étape terminée (variable {}) — topic={} instance={}",
                        completionVariable, topic, processInstanceId);
                return;
            }
            List<Map<String, Object>> tasks = findExternalTasks(processInstanceId, topic, activityId);
            if (!tasks.isEmpty()) {
                seen = true;
            } else if (seen) {
                log.info("External task traitée — topic={} instance={}", topic, processInstanceId);
                return;
            }
            sleepBriefly(300);
        }
        throw new IllegalStateException(
                "Timeout attente external task — topic=" + topic + " activityId=" + activityId
                        + " instance=" + processInstanceId + " (worker actif ?)");
    }

    public void waitForExternalTaskProcessed(String processInstanceId, String topic,
                                             String activityId, long maxWaitMs) {
        waitForExternalTaskProcessed(processInstanceId, topic, activityId, maxWaitMs, null);
    }

    public boolean hasUserTask(String processInstanceId, String taskDefinitionKey) {
        return !getTasks(processInstanceId, taskDefinitionKey).isEmpty();
    }

    /**
     * Attend qu'une variable booléenne du processus passe à {@code true} (secours / debug).
     */
    public void waitUntilTrue(String processInstanceId, String variableName, long maxWaitMs) {
        long deadline = System.currentTimeMillis() + maxWaitMs;
        while (System.currentTimeMillis() < deadline) {
            if (isTruthy(getProcessVariableQuiet(processInstanceId, variableName))) {
                return;
            }
            sleepBriefly(300);
        }
        throw new IllegalStateException(
                "Timeout attente variable " + variableName + " — instance=" + processInstanceId);
    }

    private static boolean isTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        return "true".equalsIgnoreCase(value.toString().trim());
    }

    public Object getProcessVariableQuiet(String processInstanceId, String name) {
        try {
            return getProcessVariable(processInstanceId, name);
        } catch (RestClientException ex) {
            return null;
        }
    }

    private static String stringVal(Object v) {
        return v == null ? "" : v.toString();
    }
}
