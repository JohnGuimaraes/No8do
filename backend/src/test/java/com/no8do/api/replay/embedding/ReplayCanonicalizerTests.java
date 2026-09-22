package com.no8do.api.replay.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import com.no8do.api.replay.Replay;
import com.no8do.api.replay.ReplayStatus;
import com.no8do.api.replay.ReplayType;
import com.no8do.api.replay.ReplayVersion;
import com.no8do.api.user.User;
import com.no8do.api.workspace.Workspace;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReplayCanonicalizerTests {

    private final ReplayCanonicalizer canonicalizer = new ReplayCanonicalizer();

    @Test
    void producesStableTextAndHashForSameVersion() {
        ReplayVersion version = version("Título", "Problema", "Contexto", "Solução", new String[] { "auth", "security" }, new String[] { "Java", "PostgreSQL" });

        CanonicalReplayContent first = canonicalizer.canonicalize(version);
        CanonicalReplayContent second = canonicalizer.canonicalize(version);

        assertThat(first.text()).isEqualTo(second.text());
        assertThat(first.contentHash()).isEqualTo(second.contentHash()).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void normalizesNewlinesAndOuterWhitespace() {
        ReplayVersion lf = version(" Título\nprincipal ", "Problema\ncom detalhes", " Contexto\ncom detalhes ", "Solução\ncom detalhes", new String[] { "auth\nsecurity" }, new String[] { "Java\nSpring" });
        ReplayVersion crlf = version("Título\r\nprincipal", "Problema\r\ncom detalhes", "Contexto\rcom detalhes", "Solução\r\ncom detalhes", new String[] { "auth\r\nsecurity" }, new String[] { "Java\rSpring" });

        assertThat(canonicalizer.canonicalize(lf)).isEqualTo(canonicalizer.canonicalize(crlf));
    }

    @Test
    void preservesTermOrderBecauseTheReplayDomainVersionsThatChange() {
        ReplayVersion authThenSecurity = version("Título", "Problema", "Contexto", "Solução", new String[] { "auth", "security" }, new String[] { "Java", "PostgreSQL" });
        ReplayVersion securityThenAuth = version("Título", "Problema", "Contexto", "Solução", new String[] { "security", "auth" }, new String[] { "PostgreSQL", "Java" });

        assertThat(canonicalizer.canonicalize(authThenSecurity))
                .isNotEqualTo(canonicalizer.canonicalize(securityThenAuth));
        assertThat(canonicalizer.canonicalize(authThenSecurity).contentHash())
                .isNotEqualTo(canonicalizer.canonicalize(securityThenAuth).contentHash());
    }

    @Test
    void omitsOptionalNullAndBlankFieldsDeterministically() {
        ReplayVersion version = version("Título", null, "   ", null, new String[] { null, " ", "auth" }, new String[0]);

        assertThat(canonicalizer.canonicalize(version).text())
                .isEqualTo("Título: Título\nTipo: PATTERN\nTags: auth");
    }

    @Test
    void semanticFieldChangesAlterHash() {
        CanonicalReplayContent baseline = canonicalizer.canonicalize(version("Título", "Problema", "Contexto", "Solução", new String[] { "auth" }, new String[] { "Java" }));

        assertThat(canonicalizer.canonicalize(version("Outro título", "Problema", "Contexto", "Solução", new String[] { "auth" }, new String[] { "Java" })).contentHash()).isNotEqualTo(baseline.contentHash());
        assertThat(canonicalizer.canonicalize(version("Título", "Outro problema", "Contexto", "Solução", new String[] { "auth" }, new String[] { "Java" })).contentHash()).isNotEqualTo(baseline.contentHash());
        assertThat(canonicalizer.canonicalize(version("Título", "Problema", "Outro contexto", "Solução", new String[] { "auth" }, new String[] { "Java" })).contentHash()).isNotEqualTo(baseline.contentHash());
        assertThat(canonicalizer.canonicalize(version("Título", "Problema", "Contexto", "Outra solução", new String[] { "auth" }, new String[] { "Java" })).contentHash()).isNotEqualTo(baseline.contentHash());
        assertThat(canonicalizer.canonicalize(version("Título", "Problema", "Contexto", "Solução", new String[] { "security" }, new String[] { "Java" })).contentHash()).isNotEqualTo(baseline.contentHash());
        assertThat(canonicalizer.canonicalize(version("Título", "Problema", "Contexto", "Solução", new String[] { "auth" }, new String[] { "Spring" })).contentHash()).isNotEqualTo(baseline.contentHash());
    }

    @Test
    void excludesOperationalFieldsFromCanonicalContent() {
        ReplayVersion first = version("Título", "Problema", "Contexto", "Solução", new String[] { "auth" }, new String[] { "Java" });
        ReplayVersion second = version("Título", "Problema", "Contexto", "Solução", new String[] { "auth" }, new String[] { "Java" });
        ReflectionTestUtils.setField(first, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(second, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(first, "version", 1);
        ReflectionTestUtils.setField(second, "version", 99);
        ReflectionTestUtils.setField(first, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
        ReflectionTestUtils.setField(second, "createdAt", Instant.parse("2026-02-01T00:00:00Z"));

        assertThat(canonicalizer.canonicalize(first)).isEqualTo(canonicalizer.canonicalize(second));
    }

    private ReplayVersion version(String title, String problem, String context, String solution, String[] tags, String[] stack) {
        Workspace workspace = new Workspace("Workspace");
        Replay replay = new Replay(workspace, null, title, ReplayType.PATTERN, new User("User", "canonical@example.com", "hash"));
        replay.setProblem(problem);
        replay.setContext(context);
        replay.setSolution(solution);
        replay.setTags(tags);
        replay.setStack(stack);
        replay.setStatus(ReplayStatus.VALIDATED);
        return new ReplayVersion(replay, null);
    }
}
