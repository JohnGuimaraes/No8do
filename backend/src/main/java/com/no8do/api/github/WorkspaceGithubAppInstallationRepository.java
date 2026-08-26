package com.no8do.api.github;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceGithubAppInstallationRepository extends JpaRepository<WorkspaceGithubAppInstallation, UUID> {
}
