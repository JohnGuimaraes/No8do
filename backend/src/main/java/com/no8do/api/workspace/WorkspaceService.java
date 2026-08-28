package com.no8do.api.workspace;

import com.no8do.api.activity.ProjectActivityRepository;
import com.no8do.api.credential.ProjectCredentialRepository;
import com.no8do.api.github.ProjectGithubRepositoryRepository;
import com.no8do.api.github.WorkspaceGithubAppInstallStateRepository;
import com.no8do.api.github.WorkspaceGithubAppInstallationRepository;
import com.no8do.api.github.WorkspaceGithubLinkRepository;
import com.no8do.api.idea.IdeaRepository;
import com.no8do.api.library.LibraryItemRepository;
import com.no8do.api.note.ProjectNoteRepository;
import com.no8do.api.project.FileStorageService;
import com.no8do.api.project.Project;
import com.no8do.api.project.ProjectRepository;
import com.no8do.api.technicalinfo.ProjectTechnicalInfoRepository;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workitem.ProjectWorkItemRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkspaceService {

    private final UserRepository userRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceAuthorizationService workspaceAuthorizationService;
    private final ProjectRepository projectRepository;
    private final ProjectActivityRepository projectActivityRepository;
    private final ProjectNoteRepository projectNoteRepository;
    private final ProjectCredentialRepository projectCredentialRepository;
    private final ProjectWorkItemRepository projectWorkItemRepository;
    private final ProjectTechnicalInfoRepository projectTechnicalInfoRepository;
    private final ProjectGithubRepositoryRepository projectGithubRepositoryRepository;
    private final IdeaRepository ideaRepository;
    private final LibraryItemRepository libraryItemRepository;
    private final WorkspaceInviteRepository workspaceInviteRepository;
    private final WorkspaceGithubLinkRepository workspaceGithubLinkRepository;
    private final WorkspaceGithubAppInstallationRepository workspaceGithubAppInstallationRepository;
    private final WorkspaceGithubAppInstallStateRepository workspaceGithubAppInstallStateRepository;
    private final FileStorageService fileStorageService;

    public WorkspaceService(
            UserRepository userRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceAuthorizationService workspaceAuthorizationService,
            ProjectRepository projectRepository,
            ProjectActivityRepository projectActivityRepository,
            ProjectNoteRepository projectNoteRepository,
            ProjectCredentialRepository projectCredentialRepository,
            ProjectWorkItemRepository projectWorkItemRepository,
            ProjectTechnicalInfoRepository projectTechnicalInfoRepository,
            ProjectGithubRepositoryRepository projectGithubRepositoryRepository,
            IdeaRepository ideaRepository,
            LibraryItemRepository libraryItemRepository,
            WorkspaceInviteRepository workspaceInviteRepository,
            WorkspaceGithubLinkRepository workspaceGithubLinkRepository,
            WorkspaceGithubAppInstallationRepository workspaceGithubAppInstallationRepository,
            WorkspaceGithubAppInstallStateRepository workspaceGithubAppInstallStateRepository,
            FileStorageService fileStorageService
    ) {
        this.userRepository = userRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceAuthorizationService = workspaceAuthorizationService;
        this.projectRepository = projectRepository;
        this.projectActivityRepository = projectActivityRepository;
        this.projectNoteRepository = projectNoteRepository;
        this.projectCredentialRepository = projectCredentialRepository;
        this.projectWorkItemRepository = projectWorkItemRepository;
        this.projectTechnicalInfoRepository = projectTechnicalInfoRepository;
        this.projectGithubRepositoryRepository = projectGithubRepositoryRepository;
        this.ideaRepository = ideaRepository;
        this.libraryItemRepository = libraryItemRepository;
        this.workspaceInviteRepository = workspaceInviteRepository;
        this.workspaceGithubLinkRepository = workspaceGithubLinkRepository;
        this.workspaceGithubAppInstallationRepository = workspaceGithubAppInstallationRepository;
        this.workspaceGithubAppInstallStateRepository = workspaceGithubAppInstallStateRepository;
        this.fileStorageService = fileStorageService;
    }

    @Transactional(readOnly = true)
    public List<WorkspaceResponse> listForUser(UUID currentUserId) {
        return workspaceMemberRepository.findByUserId(currentUserId)
            .stream()
            .sorted(Comparator.comparing(member -> member.getWorkspace().getCreatedAt()))
            .map(WorkspaceResponse::from)
            .toList();
    }

    @Transactional
    public WorkspaceResponse create(UUID currentUserId, CreateWorkspaceRequest request) {
        Workspace workspace = workspaceRepository.save(new Workspace(normalizeRequiredName(request.name())));
        WorkspaceMember member = workspaceMemberRepository.save(new WorkspaceMember(
            workspace,
            userRepository.getReferenceById(currentUserId),
            WorkspaceRole.OWNER
        ));
        return WorkspaceResponse.from(member);
    }

    @Transactional
    public void delete(UUID workspaceId, UUID currentUserId, DeleteWorkspaceRequest request) {
        workspaceAuthorizationService.requireWorkspaceRole(workspaceId, currentUserId, WorkspaceRole.OWNER);
        Workspace workspace = workspaceRepository.findById(workspaceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));
        if (!workspace.getName().equals(request.confirmationName().trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workspace confirmation does not match");
        }

        List<Project> projects = projectRepository.findByWorkspaceId(workspaceId);
        List<String> coverImageKeys = projects.stream()
            .map(Project::getCoverImageKey)
            .filter(java.util.Objects::nonNull)
            .toList();

        workspaceGithubAppInstallStateRepository.deleteByWorkspaceId(workspaceId);
        workspaceGithubAppInstallationRepository.deleteByWorkspaceId(workspaceId);
        workspaceGithubLinkRepository.deleteByWorkspaceId(workspaceId);
        ideaRepository.deleteByWorkspaceId(workspaceId);
        libraryItemRepository.deleteByWorkspaceId(workspaceId);
        workspaceInviteRepository.deleteByWorkspaceId(workspaceId);

        for (Project project : projects) {
            UUID projectId = project.getId();
            projectGithubRepositoryRepository.deleteByProjectId(projectId);
            projectTechnicalInfoRepository.deleteByProjectId(projectId);
            projectWorkItemRepository.deleteByProjectId(projectId);
            projectCredentialRepository.deleteByProjectId(projectId);
            projectNoteRepository.deleteByProjectId(projectId);
            projectActivityRepository.deleteByProjectId(projectId);
            projectRepository.delete(project);
        }

        workspaceMemberRepository.deleteByWorkspaceId(workspaceId);
        workspaceRepository.delete(workspace);
        deleteCoversAfterCommit(coverImageKeys);
    }

    private void deleteCoversAfterCommit(List<String> coverImageKeys) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    coverImageKeys.forEach(fileStorageService::delete);
                }
            }
        });
    }

    private String normalizeRequiredName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workspace name is required");
        }
        return name.trim();
    }
}
