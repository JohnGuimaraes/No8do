package com.no8do.api.github;

import com.no8do.api.activity.ProjectActivity;
import com.no8do.api.activity.ProjectActivityRepository;
import com.no8do.api.activity.ProjectActivityType;
import com.no8do.api.agent.AgentOperationalContextService;
import com.no8do.api.project.Project;
import com.no8do.api.project.ProjectRepository;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.WorkspaceAuthorizationService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ProjectGithubRepositoryService {

    private final ProjectRepository projectRepository;
    private final ProjectGithubRepositoryRepository projectGithubRepositoryRepository;
    private final WorkspaceAuthorizationService workspaceAuthorizationService;
    private final WorkspaceGithubAppAuthenticationService authenticationService;
    private final GithubAppClient githubAppClient;
    private final ProjectActivityRepository projectActivityRepository;
    private final UserRepository userRepository;
    private final AgentOperationalContextService operationalContextService;

    public ProjectGithubRepositoryService(
            ProjectRepository projectRepository,
            ProjectGithubRepositoryRepository projectGithubRepositoryRepository,
            WorkspaceAuthorizationService workspaceAuthorizationService,
            WorkspaceGithubAppAuthenticationService authenticationService,
            GithubAppClient githubAppClient,
            ProjectActivityRepository projectActivityRepository,
            UserRepository userRepository,
            AgentOperationalContextService operationalContextService
    ) {
        this.projectRepository = projectRepository;
        this.projectGithubRepositoryRepository = projectGithubRepositoryRepository;
        this.workspaceAuthorizationService = workspaceAuthorizationService;
        this.authenticationService = authenticationService;
        this.githubAppClient = githubAppClient;
        this.projectActivityRepository = projectActivityRepository;
        this.userRepository = userRepository;
        this.operationalContextService = operationalContextService;
    }

    @Transactional(readOnly = true)
    public ProjectGithubRepositoryResponse get(UUID workspaceId, UUID projectId, UUID currentUserId) {
        Project project = requireProject(workspaceId, projectId, currentUserId);
        return projectGithubRepositoryRepository.findById(projectId)
            .map(association -> currentAssociation(project, association.getRepositoryId()))
            .orElseGet(ProjectGithubRepositoryResponse::notAssociated);
    }

    @Transactional
    public ProjectGithubRepositoryResponse associate(
            UUID workspaceId,
            UUID projectId,
            UUID currentUserId,
            long repositoryId
    ) {
        if (repositoryId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Repository id is invalid");
        }
        Project project = requireProjectForWrite(workspaceId, projectId, currentUserId);
        GithubAppRepositoryResponse repository = authorizedRepository(project, repositoryId);
        ProjectGithubRepository association = projectGithubRepositoryRepository.findById(projectId).orElse(null);
        Long oldRepositoryId = association == null ? null : association.getRepositoryId();

        if (association == null) {
            projectGithubRepositoryRepository.save(new ProjectGithubRepository(project, repositoryId));
            registerActivity(project, currentUserId, "Repositório GitHub associado: " + repository.fullName() + ".");
        } else if (association.getRepositoryId() != repositoryId) {
            association.setRepositoryId(repositoryId);
            registerActivity(project, currentUserId, "Repositório GitHub alterado: " + repository.fullName() + ".");
        }

        if (oldRepositoryId == null || oldRepositoryId != repositoryId) {
            scheduleResolutionRefresh(project.getWorkspace().getId(), oldRepositoryId, repositoryId);
        }

        return ProjectGithubRepositoryResponse.associated(repository);
    }

    @Transactional
    public void dissociate(UUID workspaceId, UUID projectId, UUID currentUserId) {
        Project project = requireProjectForWrite(workspaceId, projectId, currentUserId);
        projectGithubRepositoryRepository.findById(projectId).ifPresent(association -> {
            long oldRepositoryId = association.getRepositoryId();
            projectGithubRepositoryRepository.delete(association);
            registerActivity(project, currentUserId, "Repositório GitHub desassociado.");
            scheduleResolutionRefresh(project.getWorkspace().getId(), oldRepositoryId, null);
        });
    }

    public void prepareProjectDeletion(UUID workspaceId, UUID projectId) {
        projectGithubRepositoryRepository.findById(projectId).ifPresent(association ->
                scheduleResolutionRefresh(workspaceId, association.getRepositoryId(), null));
    }

    private ProjectGithubRepositoryResponse currentAssociation(Project project, long repositoryId) {
        try {
            return ProjectGithubRepositoryResponse.associated(authorizedRepository(project, repositoryId));
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()
                    || exception.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                return ProjectGithubRepositoryResponse.inaccessible(repositoryId);
            }
            throw exception;
        }
    }

    private GithubAppRepositoryResponse authorizedRepository(Project project, long repositoryId) {
        GithubAppInstallationAccessToken accessToken = authenticationService.accessTokenForWorkspace(project.getWorkspace().getId());
        return githubAppClient.getInstallationRepository(accessToken, repositoryId);
    }

    private Project requireProject(UUID workspaceId, UUID projectId, UUID currentUserId) {
        workspaceAuthorizationService.requireProjectAccess(projectId, workspaceId, currentUserId);
        return projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private Project requireProjectForWrite(UUID workspaceId, UUID projectId, UUID currentUserId) {
        workspaceAuthorizationService.requireProjectWriteAccess(projectId, workspaceId, currentUserId);
        return projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private void registerActivity(Project project, UUID currentUserId, String content) {
        projectActivityRepository.save(new ProjectActivity(
            project,
            userRepository.getReferenceById(currentUserId),
            ProjectActivityType.UPDATE,
            content
        ));
    }

    private void scheduleResolutionRefresh(UUID workspaceId, Long oldRepositoryId, Long newRepositoryId) {
        Runnable refresh = () -> {
            if (oldRepositoryId != null) operationalContextService.refreshRepositoryResolutionForWorkspaceAfterCommit(workspaceId, oldRepositoryId);
            if (newRepositoryId != null && !newRepositoryId.equals(oldRepositoryId)) {
                operationalContextService.refreshRepositoryResolutionForWorkspaceAfterCommit(workspaceId, newRepositoryId);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { refresh.run(); }
            });
        } else {
            refresh.run();
        }
    }
}
