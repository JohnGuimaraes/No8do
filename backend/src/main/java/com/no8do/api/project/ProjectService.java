package com.no8do.api.project;

import com.no8do.api.client.Client;
import com.no8do.api.client.ClientRepository;
import com.no8do.api.activity.ProjectActivity;
import com.no8do.api.activity.ProjectActivityRepository;
import com.no8do.api.activity.ProjectActivityType;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.WorkspaceAuthorizationService;
import com.no8do.api.workspace.WorkspaceRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectActivityRepository projectActivityRepository;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final WorkspaceAuthorizationService workspaceAuthorizationService;
    private final WorkspaceRepository workspaceRepository;

    public ProjectService(
            ProjectRepository projectRepository,
            ProjectActivityRepository projectActivityRepository,
            ClientRepository clientRepository,
            UserRepository userRepository,
            WorkspaceAuthorizationService workspaceAuthorizationService,
            WorkspaceRepository workspaceRepository
    ) {
        this.projectRepository = projectRepository;
        this.projectActivityRepository = projectActivityRepository;
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
        this.workspaceAuthorizationService = workspaceAuthorizationService;
        this.workspaceRepository = workspaceRepository;
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> listByWorkspace(UUID workspaceId, UUID currentUserId) {
        workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUserId);
        return projectRepository.findByWorkspaceId(workspaceId)
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
        ProjectStatus newStatus = request.status() == null ? project.getStatus() : request.status();
        Client newClient = resolveClient(workspaceId, request.clientId());
        List<String> changes = describeChanges(project, newName, newDescription, newCurrentState, newStatus, newClient);

        project.setName(newName);
        project.setDescription(newDescription);
        project.setStatus(newStatus);
        project.setCurrentState(newCurrentState);
        project.setClient(newClient);

        if (!changes.isEmpty()) {
            registerAutomaticActivity(project, currentUserId, "Projeto atualizado: " + String.join("; ", changes) + ".");
        }

        return ProjectResponse.from(project);
    }

    private String normalizeRequiredName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project name is required");
        }
        return name.trim();
    }

    private List<String> describeChanges(
            Project project,
            String newName,
            String newDescription,
            String newCurrentState,
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

    private void registerAutomaticActivity(Project project, UUID currentUserId, String content) {
        projectActivityRepository.save(new ProjectActivity(
            project,
            userRepository.getReferenceById(currentUserId),
            ProjectActivityType.UPDATE,
            content
        ));
    }
}
