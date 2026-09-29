package com.no8do.api.agent;

import java.net.IDN;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public final class AgentOperationalContextValidator {
    private static final Pattern SLUG = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}");
    private static final Pattern PATH_SEGMENT = Pattern.compile("[a-zA-Z0-9._~-]{1,128}");
    private static final Pattern REFERENCE_KEY = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9._~-]{0,127}");

    public AgentOperationalContextSignal canonicalize(AgentOperationalContextUpdateRequest request) {
        if (request == null) throw invalid();
        AgentOperationalContextSignal.Repository repository = canonicalRepository(request.repository());
        String branch = canonicalBranch(request.branch());
        String workingDirectory = canonicalWorkingDirectory(request.workingDirectory());
        List<AgentOperationalContextSignal.Reference> references = canonicalReferences(request.references());
        return new AgentOperationalContextSignal(repository, branch, workingDirectory, references);
    }

    private static AgentOperationalContextSignal.Repository canonicalRepository(
            AgentOperationalContextUpdateRequest.RepositorySignal input) {
        if (input == null) return null;
        String vcs = required(input.vcs(), 16).toUpperCase(Locale.ROOT);
        if (!vcs.equals("GIT")) throw invalid();
        String provider = slug(input.provider());
        String hostInput = required(input.host(), 253);
        if (hostInput.contains(":") || hostInput.contains("/") || hostInput.contains("@")
                || hostInput.contains("?") || hostInput.contains("#") || hostInput.contains("\\")) throw invalid();
        String host;
        try {
            host = IDN.toASCII(hostInput.endsWith(".") ? hostInput.substring(0, hostInput.length() - 1) : hostInput,
                    IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException malformed) {
            throw invalid();
        }
        if (host.isBlank() || host.length() > 253) throw invalid();
        for (String label : host.split("\\.", -1)) {
            if (label.isBlank() || label.length() > 63 || label.startsWith("-") || label.endsWith("-")) throw invalid();
        }
        String namespace = required(input.namespace(), 512);
        for (String segment : namespace.split("/", -1)) {
            if (!safePathSegment(segment)) throw invalid();
        }
        String name = required(input.name(), 255);
        if (!safePathSegment(name)) throw invalid();
        return new AgentOperationalContextSignal.Repository(vcs, provider, host, namespace, name);
    }

    private static String canonicalBranch(String value) {
        if (value == null) return null;
        if (value.isBlank() || value.length() > 255 || hasControl(value)) throw invalid();
        return value;
    }

    private static String canonicalWorkingDirectory(String value) {
        if (value == null) return null;
        if (value.isBlank() || value.length() > 1024 || hasControl(value)) throw invalid();
        String path = value.replace('\\', '/');
        if (path.startsWith("/") || path.startsWith("//") || path.matches("^[A-Za-z]:.*")) throw invalid();
        StringBuilder normalized = new StringBuilder();
        for (String part : path.split("/+", -1)) {
            if (part.isEmpty()) continue;
            if (part.equals(".") || part.equals("..") || !safePathSegment(part)) throw invalid();
            if (!normalized.isEmpty()) normalized.append('/');
            normalized.append(part);
        }
        if (normalized.isEmpty() || normalized.length() > 1024) throw invalid();
        return normalized.toString();
    }

    private static List<AgentOperationalContextSignal.Reference> canonicalReferences(
            List<AgentOperationalContextUpdateRequest.ReferenceSignal> inputs) {
        if (inputs == null) return List.of();
        if (inputs.size() > 20) throw invalid();
        List<AgentOperationalContextSignal.Reference> values = new ArrayList<>();
        Set<String> distinct = new HashSet<>();
        for (AgentOperationalContextUpdateRequest.ReferenceSignal input : inputs) {
            if (input == null || input.kind() == null) throw invalid();
            String provider = slug(input.provider());
            String key = required(input.key(), 128);
            String lowerKey = key.toLowerCase(Locale.ROOT);
            if (!REFERENCE_KEY.matcher(key).matches() || lowerKey.startsWith("ghp_")
                    || lowerKey.startsWith("gho_") || lowerKey.startsWith("ghu_")
                    || lowerKey.startsWith("ghs_") || lowerKey.startsWith("ghr_")
                    || lowerKey.startsWith("github_pat_")) {
                throw invalid();
            }
            String identity = input.kind().name() + "\u0000" + provider + "\u0000" + key;
            if (!distinct.add(identity)) throw invalid();
            values.add(new AgentOperationalContextSignal.Reference(input.kind(), provider, key));
        }
        values.sort(Comparator.comparing((AgentOperationalContextSignal.Reference item) -> item.kind().name())
                .thenComparing(AgentOperationalContextSignal.Reference::provider)
                .thenComparing(AgentOperationalContextSignal.Reference::key));
        return List.copyOf(values);
    }

    private static String slug(String value) {
        String normalized = required(value, 64).toLowerCase(Locale.ROOT);
        if (!SLUG.matcher(normalized).matches()) throw invalid();
        return normalized;
    }

    private static String required(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max || hasControl(value)) throw invalid();
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > max) throw invalid();
        return trimmed;
    }

    private static boolean safePathSegment(String value) {
        return !value.equals(".") && !value.equals("..") && PATH_SEGMENT.matcher(value).matches();
    }

    private static boolean hasControl(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid operational context");
    }
}
