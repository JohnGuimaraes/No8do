package com.no8do.api.project;

import com.no8do.api.client.Client;
import com.no8do.api.client.ClientRepository;
import com.no8do.api.activity.ProjectActivity;
import com.no8do.api.activity.ProjectActivityRepository;
import com.no8do.api.activity.ProjectActivityType;
import com.no8do.api.credential.ProjectCredentialRepository;
import com.no8do.api.idea.IdeaRepository;
import com.no8do.api.note.ProjectNoteRepository;
import com.no8do.api.technicalinfo.ProjectTechnicalInfoRepository;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workitem.ProjectWorkItemRepository;
import com.no8do.api.workspace.WorkspaceAuthorizationService;
import com.no8do.api.workspace.WorkspaceRepository;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProjectService {

    private static final int MAX_REPOSITORY_URL_LENGTH = 2048;

    private final ProjectRepository projectRepository;
    private final ProjectActivityRepository projectActivityRepository;
    private final ProjectNoteRepository projectNoteRepository;
    private final ProjectCredentialRepository projectCredentialRepository;
    private final ProjectWorkItemRepository projectWorkItemRepository;
    private final ProjectTechnicalInfoRepository projectTechnicalInfoRepository;
    private final IdeaRepository ideaRepository;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final WorkspaceAuthorizationService workspaceAuthorizationService;
    private final WorkspaceRepository workspaceRepository;
    private final FileStorageService fileStorageService;

    public ProjectService(
            ProjectRepository projectRepository,
            ProjectActivityRepository projectActivityRepository,
            ProjectNoteRepository projectNoteRepository,
            ProjectCredentialRepository projectCredentialRepository,
            ProjectWorkItemRepository projectWorkItemRepository,
            ProjectTechnicalInfoRepository projectTechnicalInfoRepository,
            IdeaRepository ideaRepository,
            ClientRepository clientRepository,
            UserRepository userRepository,
            WorkspaceAuthorizationService workspaceAuthorizationService,
            WorkspaceRepository workspaceRepository,
            FileStorageService fileStorageService
    ) {
        this.projectRepository = projectRepository;
        this.projectActivityRepository = projectActivityRepository;
        this.projectNoteRepository = projectNoteRepository;
        this.projectCredentialRepository = projectCredentialRepository;
        this.projectWorkItemRepository = projectWorkItemRepository;
        this.projectTechnicalInfoRepository = projectTechnicalInfoRepository;
        this.ideaRepository = ideaRepository;
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
        this.workspaceAuthorizationService = workspaceAuthorizationService;
        this.workspaceRepository = workspaceRepository;
        this.fileStorageService = fileStorageService;
    }

    @Transactional
    public ProjectResponse uploadCover(UUID workspaceId, UUID projectId, UUID currentUserId, MultipartFile file) {
        workspaceAuthorizationService.requireProjectAccess(projectId, workspaceId, currentUserId);
        Project project = projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        FileStorageService.StoredFile stored = fileStorageService.storeProjectCover(file);
        String previousKey = project.getCoverImageKey();
        project.setCoverImageKey(stored.key());
        project.setCoverImageUpdatedAt(Instant.now());
        projectRepository.save(project);
        cleanupAfterCommit(previousKey, stored.key());
        registerAutomaticActivity(project, currentUserId, "Capa atualizada.");
        return ProjectResponse.from(project);
    }

    @Transactional(readOnly = true)
    public ProjectCover getCover(UUID workspaceId, UUID projectId, UUID currentUserId) {
        workspaceAuthorizationService.requireProjectAccess(projectId, workspaceId, currentUserId);
        Project project = projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        if (project.getCoverImageKey() == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project cover not found");
        return new ProjectCover(fileStorageService.read(project.getCoverImageKey()), fileStorageService.contentType(project.getCoverImageKey()));
    }

    @Transactional
    public ProjectResponse deleteCover(UUID workspaceId, UUID projectId, UUID currentUserId) {
        workspaceAuthorizationService.requireProjectAccess(projectId, workspaceId, currentUserId);
        Project project = projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        String previousKey = project.getCoverImageKey();
        project.setCoverImageKey(null);
        project.setCoverImageUpdatedAt(null);
        projectRepository.save(project);
        cleanupAfterCommit(previousKey, null);
        registerAutomaticActivity(project, currentUserId, "Capa removida.");
        return ProjectResponse.from(project);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> listByWorkspace(UUID workspaceId, UUID currentUserId) {
        return listByWorkspace(workspaceId, currentUserId, false);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> listByWorkspace(UUID workspaceId, UUID currentUserId, boolean archived) {
        workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUserId);
        List<Project> projects = archived
            ? projectRepository.findByWorkspaceIdAndArchivedAtIsNotNullOrderByArchivedAtDesc(workspaceId)
            : projectRepository.findByWorkspaceIdAndArchivedAtIsNull(workspaceId);
        return projects
            .stream()
            .map(ProjectResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse getByWorkspace(UUID workspaceId, UUID projectId, UUID currentUserId) {
        workspaceAuthorizationService.requireProjectAccess(projectId, workspaceId, currentUserId);
        return projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)
            .map(ProjectResponse::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    @Transactional
    public ProjectResponse create(UUID workspaceId, UUID currentUserId, CreateProjectRequest request) {
        workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUserId);
        Project project = new Project(
            workspaceRepository.getReferenceById(workspaceId),
            normalizeRequiredName(request.name()),
            userRepository.getReferenceById(currentUserId)
        );
        project.setDescription(request.description());
        project.setCurrentState(request.currentState());
        project.setRepositoryUrl(normalizeRepositoryUrl(request.repositoryUrl()));
        if (request.status() != null) {
            project.setStatus(request.status());
        }
        Project savedProject = projectRepository.save(project);
        registerAutomaticActivity(savedProject, currentUserId, "Projeto criado.");
        return ProjectResponse.from(savedProject);
    }

    @Transactional
    public ProjectResponse update(UUID workspaceId, UUID projectId, UUID currentUserId, UpdateProjectRequest request) {
        workspaceAuthorizationService.requireProjectAccess(projectId, workspaceId, currentUserId);
        Project project = projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        String newName = normalizeRequiredName(request.name());
        String newDescription = request.description();
        String newCurrentState = request.currentState();
        String newRepositoryUrl = normalizeRepositoryUrl(request.repositoryUrl());
        ProjectStatus newStatus = request.status() == null ? project.getStatus() : request.status();
        Client newClient = resolveClient(workspaceId, request.clientId());
        List<String> changes = describeChanges(project, newName, newDescription, newCurrentState, newRepositoryUrl, newStatus, newClient);

        project.setName(newName);
        project.setDescription(newDescription);
        project.setStatus(newStatus);
        project.setCurrentState(newCurrentState);
        project.setRepositoryUrl(newRepositoryUrl);
        project.setClient(newClient);

        if (!changes.isEmpty()) {
            registerAutomaticActivity(project, currentUserId, "Projeto atualizado: " + String.join("; ", changes) + ".");
        }

        return ProjectResponse.from(project);
    }

    @Transactional
    public Project updateRepositoryUrl(UUID workspaceId, UUID projectId, UUID currentUserId, String repositoryUrl) {
        Project project = requireProject(workspaceId, projectId, currentUserId);
        String normalizedRepositoryUrl = normalizeRepositoryUrl(repositoryUrl);

        if (!Objects.equals(project.getRepositoryUrl(), normalizedRepositoryUrl)) {
            project.setRepositoryUrl(normalizedRepositoryUrl);
            registerAutomaticActivity(project, currentUserId, "Projeto atualizado: repositório alterado.");
        }

        return project;
    }

    @Transactional
    public ProjectResponse archive(UUID workspaceId, UUID projectId, UUID currentUserId) {
        Project project = requireProject(workspaceId, projectId, currentUserId);
        if (project.getArchivedAt() == null) {
            project.setArchivedAt(Instant.now());
            project.setArchivedBy(userRepository.getReferenceById(currentUserId));
            registerAutomaticActivity(project, currentUserId, "Projeto arquivado.");
        }
        return ProjectResponse.from(project);
    }

    @Transactional
    public ProjectResponse restore(UUID workspaceId, UUID projectId, UUID currentUserId) {
        Project project = requireProject(workspaceId, projectId, currentUserId);
        if (project.getArchivedAt() != null) {
            project.setArchivedAt(null);
            project.setArchivedBy(null);
            registerAutomaticActivity(project, currentUserId, "Projeto restaurado.");
        }
        return ProjectResponse.from(project);
    }

    @Transactional
    public void delete(UUID workspaceId, UUID projectId, UUID currentUserId) {
        Project project = requireProject(workspaceId, projectId, currentUserId);
        if (ideaRepository.findByConvertedProjectId(projectId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Project is linked to an idea");
        }
        String coverImageKey = project.getCoverImageKey();

        projectTechnicalInfoRepository.deleteByProjectId(projectId);
        projectWorkItemRepository.deleteByProjectId(projectId);
        projectCredentialRepository.deleteByProjectId(projectId);
        projectNoteRepository.deleteByProjectId(projectId);
        projectActivityRepository.deleteByProjectId(projectId);
        projectRepository.delete(project);
        cleanupAfterCommit(coverImageKey, null);
    }

    private String normalizeRequiredName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project name is required");
        }
        return name.trim();
    }

    private String normalizeRepositoryUrl(String repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.trim().isEmpty()) {
            return null;
        }

        String normalized = repositoryUrl.trim();
        if (normalized.length() > MAX_REPOSITORY_URL_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Repository URL is too long");
        }

        try {
            URI uri = new URI(normalized);
            if ((!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Repository URL must be an absolute HTTP URL");
            }
        } catch (URISyntaxException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Repository URL is invalid");
        }

        return normalized;
    }

    private List<String> describeChanges(
            Project project,
            String newName,
            String newDescription,
            String newCurrentState,
            String newRepositoryUrl,
            ProjectStatus newStatus,
            Client newClient
    ) {
        List<String> changes = new ArrayList<>();
        if (!Objects.equals(project.getName(), newName)) {
            changes.add("nome alterado");
        }
        if (!Objects.equals(project.getDescription(), newDescription)) {
            changes.add("descrição alterada");
        }
        if (!Objects.equals(project.getCurrentState(), newCurrentState)) {
            changes.add("estado atual alterado");
        }
        if (!Objects.equals(project.getRepositoryUrl(), newRepositoryUrl)) {
            changes.add("repositório alterado");
        }
        if (!Objects.equals(project.getStatus(), newStatus)) {
            changes.add("status alterado de " + project.getStatus() + " para " + newStatus);
        }
        UUID currentClientId = project.getClient() == null ? null : project.getClient().getId();
        UUID newClientId = newClient == null ? null : newClient.getId();
        if (!Objects.equals(currentClientId, newClientId)) {
            changes.add("cliente vinculado alterado");
        }
        return changes;
    }

    private Client resolveClient(UUID workspaceId, UUID clientId) {
        if (clientId == null) {
            return null;
        }
        return clientRepository.findByIdAndWorkspaceId(clientId, workspaceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
    }

    private Project requireProject(UUID workspaceId, UUID projectId, UUID currentUserId) {
        workspaceAuthorizationService.requireProjectAccess(projectId, workspaceId, currentUserId);
        return projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private void registerAutomaticActivity(Project project, UUID currentUserId, String content) {
        projectActivityRepository.save(new ProjectActivity(
            project,
            userRepository.getReferenceById(currentUserId),
            ProjectActivityType.UPDATE,
            content
        ));
    }

    private void cleanupAfterCommit(String previousKey, String newKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    fileStorageService.delete(previousKey);
                } else {
                    fileStorageService.delete(newKey);
                }
            }
        });
    }
}
