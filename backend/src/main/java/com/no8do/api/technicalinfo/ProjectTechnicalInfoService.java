package com.no8do.api.technicalinfo;

import com.no8do.api.activity.ProjectActivity;
import com.no8do.api.activity.ProjectActivityRepository;
import com.no8do.api.activity.ProjectActivityType;
import com.no8do.api.project.Project;
import com.no8do.api.project.ProjectRepository;
import com.no8do.api.project.ProjectService;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.WorkspaceAuthorizationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
    private final ProjectActivityRepository projectActivityRepository;
    private final ProjectRepository projectRepository;
    private final ProjectService projectService;
    private final UserRepository userRepository;
    private final WorkspaceAuthorizationService workspaceAuthorizationService;

    public ProjectTechnicalInfoService(
            ProjectTechnicalInfoRepository projectTechnicalInfoRepository,
            ProjectActivityRepository projectActivityRepository,
            ProjectRepository projectRepository,
            ProjectService projectService,
            UserRepository userRepository,
            WorkspaceAuthorizationService workspaceAuthorizationService
    ) {
        this.projectTechnicalInfoRepository = projectTechnicalInfoRepository;
        this.projectActivityRepository = projectActivityRepository;
        this.projectRepository = projectRepository;
        this.projectService = projectService;
        this.userRepository = userRepository;
        this.workspaceAuthorizationService = workspaceAuthorizationService;
    }

    @Transactional(readOnly = true)
    public ProjectTechnicalInfoResponse get(UUID workspaceId, UUID projectId, UUID currentUserId) {
        workspaceAuthorizationService.requireProjectAccess(projectId, workspaceId, currentUserId);
        Project project = projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        return projectTechnicalInfoRepository.findById(projectId)
            .map(info -> ProjectTechnicalInfoResponse.from(info, project.getRepositoryUrl()))
            .orElseGet(() -> ProjectTechnicalInfoResponse.empty(projectId, project.getRepositoryUrl()));
    }

    @Transactional
    public ProjectTechnicalInfoResponse upsert(
            UUID workspaceId,
            UUID projectId,
            UUID currentUserId,
            ProjectTechnicalInfoRequest request
    ) {
        validateRepositoryUrlLength(request.repositoryUrl());
        Project project = projectService.updateRepositoryUrl(
            workspaceId,
            projectId,
            currentUserId,
            request.repositoryUrl()
        );
        ProjectTechnicalInfo info = projectTechnicalInfoRepository.findById(projectId)
            .orElseGet(() -> {
                return new ProjectTechnicalInfo(project);
            });

        String stack = normalizeOptional(request.stack(), MAX_STACK_LENGTH, "Stack is too long");
        String productionUrl = normalizeOptional(request.productionUrl(), MAX_URL_LENGTH, "Production URL is too long");
        String developmentUrl = normalizeOptional(request.developmentUrl(), MAX_URL_LENGTH, "Development URL is too long");
        String localPath = normalizeOptional(request.localPath(), MAX_LOCAL_PATH_LENGTH, "Local path is too long");
        String runCommand = normalizeOptional(request.runCommand(), MAX_RUN_COMMAND_LENGTH, "Run command is too long");
        List<String> changedFields = changedFields(info, stack, productionUrl, developmentUrl, localPath, runCommand);

        info.setStack(stack);
        info.setProductionUrl(productionUrl);
        info.setDevelopmentUrl(developmentUrl);
        info.setLocalPath(localPath);
        info.setRunCommand(runCommand);

        ProjectTechnicalInfo savedInfo = projectTechnicalInfoRepository.saveAndFlush(info);
        if (!changedFields.isEmpty()) {
            projectActivityRepository.save(new ProjectActivity(
                project,
                userRepository.getReferenceById(currentUserId),
                ProjectActivityType.UPDATE,
                "Informações técnicas atualizadas: " + String.join(", ", changedFields) + "."
            ));
        }
        return ProjectTechnicalInfoResponse.from(savedInfo, project.getRepositoryUrl());
    }

    private void validateRepositoryUrlLength(String repositoryUrl) {
        if (repositoryUrl != null && repositoryUrl.trim().length() > MAX_URL_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Repository URL is too long");
        }
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

    private List<String> changedFields(
            ProjectTechnicalInfo info,
            String stack,
            String productionUrl,
            String developmentUrl,
            String localPath,
            String runCommand
    ) {
        List<String> fields = new ArrayList<>();
        if (!Objects.equals(info.getStack(), stack)) fields.add("stack");
        if (!Objects.equals(info.getProductionUrl(), productionUrl)) fields.add("produção");
        if (!Objects.equals(info.getDevelopmentUrl(), developmentUrl)) fields.add("desenvolvimento");
        if (!Objects.equals(info.getLocalPath(), localPath)) fields.add("diretório local");
        if (!Objects.equals(info.getRunCommand(), runCommand)) fields.add("comando");
        return fields;
    }
}
