package com.no8do.api.technicalinfo;

import com.no8do.api.project.Project;
import com.no8do.api.project.ProjectRepository;
import com.no8do.api.workspace.WorkspaceAuthorizationService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectTechnicalInfoService {

    private static final int MAX_URL_LENGTH = 1000;
    private static final int MAX_STACK_LENGTH = 3000;
    private static final int MAX_LOCAL_PATH_LENGTH = 2000;
    private static final int MAX_RUN_COMMAND_LENGTH = 1000;

    private final ProjectTechnicalInfoRepository projectTechnicalInfoRepository;
    private final ProjectRepository projectRepository;
    private final WorkspaceAuthorizationService workspaceAuthorizationService;

    public ProjectTechnicalInfoService(
            ProjectTechnicalInfoRepository projectTechnicalInfoRepository,
            ProjectRepository projectRepository,
            WorkspaceAuthorizationService workspaceAuthorizationService
    ) {
        this.projectTechnicalInfoRepository = projectTechnicalInfoRepository;
        this.projectRepository = projectRepository;
        this.workspaceAuthorizationService = workspaceAuthorizationService;
    }

    @Transactional(readOnly = true)
    public ProjectTechnicalInfoResponse get(UUID workspaceId, UUID projectId, UUID currentUserId) {
        workspaceAuthorizationService.requireProjectAccess(projectId, workspaceId, currentUserId);
        return projectTechnicalInfoRepository.findById(projectId)
            .map(ProjectTechnicalInfoResponse::from)
            .orElseGet(() -> ProjectTechnicalInfoResponse.empty(projectId));
    }

    @Transactional
    public ProjectTechnicalInfoResponse upsert(
            UUID workspaceId,
            UUID projectId,
            UUID currentUserId,
            ProjectTechnicalInfoRequest request
    ) {
        workspaceAuthorizationService.requireProjectAccess(projectId, workspaceId, currentUserId);
        ProjectTechnicalInfo info = projectTechnicalInfoRepository.findById(projectId)
            .orElseGet(() -> {
                Project project = projectRepository.getReferenceById(projectId);
                return new ProjectTechnicalInfo(project);
            });

        info.setRepositoryUrl(normalizeOptional(request.repositoryUrl(), MAX_URL_LENGTH, "Repository URL is too long"));
        info.setStack(normalizeOptional(request.stack(), MAX_STACK_LENGTH, "Stack is too long"));
        info.setProductionUrl(normalizeOptional(request.productionUrl(), MAX_URL_LENGTH, "Production URL is too long"));
        info.setDevelopmentUrl(normalizeOptional(request.developmentUrl(), MAX_URL_LENGTH, "Development URL is too long"));
        info.setLocalPath(normalizeOptional(request.localPath(), MAX_LOCAL_PATH_LENGTH, "Local path is too long"));
        info.setRunCommand(normalizeOptional(request.runCommand(), MAX_RUN_COMMAND_LENGTH, "Run command is too long"));

        return ProjectTechnicalInfoResponse.from(projectTechnicalInfoRepository.saveAndFlush(info));
    }

    private String normalizeOptional(String value, int maxLength, String errorMessage) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalizedValue = value.trim();
        if (normalizedValue.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
        }
        return normalizedValue;
    }
}
