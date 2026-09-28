package com.no8do.api.connection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ConnectionMetadataValidator {

    private static final int MAX_FIELDS = 32;
    private static final int MAX_VALUE_LENGTH = 256;
    private static final Set<String> SENSITIVE_KEY_PARTS = Set.of(
            "secret", "token", "password", "apikey", "accesskey", "refreshkey", "privatekey",
            "ciphertext", "cipher", "iv", "credential", "authorization", "cookie", "header",
            "connectionstring", "clientsecret");
    private static final Pattern SECRET_VALUE = Pattern.compile(
            "(?i)(-----BEGIN [A-Z ]*PRIVATE KEY-----|\\bBearer\\s+|\\bBasic\\s+|"
                    + "github_pat_[A-Za-z0-9_]+|gh[pousr]_[A-Za-z0-9]+|glpat-[A-Za-z0-9_-]+|"
                    + "xox[baprs]-[A-Za-z0-9-]+|eyJ[A-Za-z0-9_-]*\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+|"
                    + "(?i)(?:token|secret|password|api[_-]?key|authorization)\\s*[:=]\\s*\\S+)");

    private final ObjectMapper objectMapper;

    public ConnectionMetadataValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode normalize(JsonNode supplied) {
        if (supplied == null || supplied.isNull()) return objectMapper.createObjectNode();
        if (!supplied.isObject() || supplied.size() > MAX_FIELDS) throw invalidMetadata();

        ObjectNode safe = objectMapper.createObjectNode();
        Iterator<Map.Entry<String, JsonNode>> fields = supplied.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = field.getKey();
            String normalizedKey = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (!key.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")
                    || SENSITIVE_KEY_PARTS.stream().anyMatch(normalizedKey::contains)) throw invalidMetadata();

            JsonNode value = field.getValue();
            if (value == null || value.isNull()) {
                safe.putNull(key);
            } else if (value.isTextual()) {
                String text = value.asText();
                validateDisplayValue(text);
                safe.put(key, text);
            } else if (value.isNumber()) {
                safe.set(key, value.deepCopy());
            } else if (value.isBoolean()) {
                safe.put(key, value.booleanValue());
            } else {
                throw invalidMetadata();
            }
        }
        return safe;
    }

    public void validateDisplayValue(String value) {
        if (value == null || value.length() > MAX_VALUE_LENGTH || SECRET_VALUE.matcher(value).find()
                || value.contains("-----BEGIN") || value.contains("Authorization:") || looksLikeOpaqueSecret(value)) {
            throw invalidMetadata();
        }
    }

    private boolean looksLikeOpaqueSecret(String value) {
        if (value.length() < 32) return false;
        Map<Character, Integer> counts = new HashMap<>();
        for (int index = 0; index < value.length(); index++) counts.merge(value.charAt(index), 1, Integer::sum);
        double entropy = 0;
        for (int count : counts.values()) {
            double probability = (double) count / value.length();
            entropy -= probability * (Math.log(probability) / Math.log(2));
        }
        return entropy >= 3.7;
    }

    private ResponseStatusException invalidMetadata() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Connection metadata must contain non-sensitive scalar values");
    }
}
