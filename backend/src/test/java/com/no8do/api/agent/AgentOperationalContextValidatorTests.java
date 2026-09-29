package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AgentOperationalContextValidatorTests {
    private final AgentOperationalContextValidator validator = new AgentOperationalContextValidator();

    @Test
    void canonicalizesHostSeparatorsAndReferenceOrderWithoutLowercasingPathIdentity() {
        AgentOperationalContextSignal signal = validator.canonicalize(new AgentOperationalContextUpdateRequest(
                new AgentOperationalContextUpdateRequest.RepositorySignal("git", "GitHub", "GITHUB.com.",
                        "Owner/Team", "Repo"), "feature/x", "src\\main\\java",
                List.of(new AgentOperationalContextUpdateRequest.ReferenceSignal(
                        AgentContextReferenceKind.ISSUE, "GitHub", "AbC-1")), null));

        assertThat(signal.repository().vcs()).isEqualTo("GIT");
        assertThat(signal.repository().provider()).isEqualTo("github");
        assertThat(signal.repository().host()).isEqualTo("github.com");
        assertThat(signal.repository().namespace()).isEqualTo("Owner/Team");
        assertThat(signal.repository().name()).isEqualTo("Repo");
        assertThat(signal.workingDirectory()).isEqualTo("src/main/java");
        assertThat(signal.references()).extracting(AgentOperationalContextSignal.Reference::provider)
                .containsExactly("github");
    }

    @Test
    void rejectsUnsafeWorkingDirectories() {
        for (String path : List.of("C:\\repo", "\\\\server\\share", "/repo", "../secret", "src/../../secret",
                "src/./main", "src/\nmain")) {
            assertThatThrownBy(() -> validator.canonicalize(request(path, null, null)))
                    .isInstanceOf(ResponseStatusException.class);
        }
    }

    @Test
    void rejectsMalformedRepositoryIdentityBranchesAndReferences() {
        assertThatThrownBy(() -> validator.canonicalize(new AgentOperationalContextUpdateRequest(
                new AgentOperationalContextUpdateRequest.RepositorySignal("GIT", "github", "https://user:pw@host/repo",
                        "owner", "repo"), null, null, null, null))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> validator.canonicalize(request(null, " ", null)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> validator.canonicalize(request(null, "b".repeat(256), null)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> validator.canonicalize(request(null, null, List.of(
                new AgentOperationalContextUpdateRequest.ReferenceSignal(AgentContextReferenceKind.TASK,
                        "jira", "x".repeat(129)))))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> validator.canonicalize(request(null, null, List.of(
                new AgentOperationalContextUpdateRequest.ReferenceSignal(AgentContextReferenceKind.TASK,
                        "jira", "ghp_sensitive-looking-value"))))).isInstanceOf(ResponseStatusException.class);
        List<AgentOperationalContextUpdateRequest.ReferenceSignal> tooMany = java.util.stream.IntStream.range(0, 21)
                .mapToObj(i -> new AgentOperationalContextUpdateRequest.ReferenceSignal(
                        AgentContextReferenceKind.TASK, "tracker", "T-" + i)).toList();
        assertThatThrownBy(() -> validator.canonicalize(request(null, null, tooMany)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void acceptsBearerPrefixedReferenceIdentifiersButRejectsAuthorizationValues() {
        AgentOperationalContextUpdateRequest accepted = request(null, null, List.of(
                new AgentOperationalContextUpdateRequest.ReferenceSignal(
                        AgentContextReferenceKind.TASK, "tracker", "Bearer-123")));
        assertThat(validator.canonicalize(accepted).references())
                .extracting(AgentOperationalContextSignal.Reference::key)
                .containsExactly("Bearer-123");

        for (String value : List.of("Bearer eyJhbGciOiJIUzI1NiJ9.payload.signature",
                "Bearer\teyJhbGciOiJIUzI1NiJ9.payload.signature")) {
            AgentOperationalContextUpdateRequest rejected = request(null, null, List.of(
                    new AgentOperationalContextUpdateRequest.ReferenceSignal(
                            AgentContextReferenceKind.TASK, "tracker", value)));
            assertThatThrownBy(() -> validator.canonicalize(rejected))
                    .isInstanceOf(ResponseStatusException.class);
        }
    }

    private static AgentOperationalContextUpdateRequest request(String cwd, String branch,
            List<AgentOperationalContextUpdateRequest.ReferenceSignal> references) {
        return new AgentOperationalContextUpdateRequest(null, branch, cwd, references, null);
    }
}
