package com.no8do.api.auth;

import com.no8do.api.agent.AgentCapabilityDeniedException;
import com.no8do.api.agent.AgentPolicyDeniedException;
import com.no8do.api.agent.AgentSessionDisconnectedException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        return ResponseEntity
            .status(ex.getStatusCode())
            .body(Map.of("error", ex.getReason()));
    }

    @ExceptionHandler(AgentCapabilityDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAgentCapabilityDenied(AgentCapabilityDeniedException ex) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).body(Map.of(
            "error", "AGENT_CAPABILITY_DENIED",
            "metadata", Map.of(
                "sessionId", ex.session().getId(),
                "runtimeMode", ex.session().getRuntimeMode(),
                "requiredCapability", ex.requiredCapability().name())));
    }

    @ExceptionHandler(AgentPolicyDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAgentPolicyDenied(AgentPolicyDeniedException ex) {
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("sessionId", ex.sessionId());
        metadata.put("policyId", ex.decision().policyId());
        metadata.put("reason", ex.decision().reason());
        return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).body(Map.of(
            "error", "AGENT_POLICY_DENIED",
            "metadata", metadata));
    }

    @ExceptionHandler(AgentSessionDisconnectedException.class)
    public ResponseEntity<Map<String, Object>> handleAgentSessionDisconnected(AgentSessionDisconnectedException ex) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT).body(Map.of(
            "error", "AGENT_SESSION_DISCONNECTED",
            "metadata", Map.of("sessionId", ex.sessionId())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation() {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid request"));
    }
}
