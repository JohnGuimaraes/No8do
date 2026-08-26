package com.no8do.api.workitem;

import com.no8do.api.activity.ProjectActivity;
import com.no8do.api.activity.ProjectActivityRepository;
import com.no8do.api.activity.ProjectActivityType;
import com.no8do.api.project.ProjectRepository;
import com.no8do.api.user.UserRepository;
import com.no8do.api.user.User;
import com.no8do.api.workspace.WorkspaceAuthorizationService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import com.no8do.api.workspace.WorkspaceMemberRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectWorkItemService {

    private static final int MAX_TITLE_LENGTH = 180;
    private static final int MAX_DETAILS_LENGTH = 2_000;

    private final ProjectWorkItemRepository projectWorkItemRepository;
    private final ProjectActivityRepository projectActivityRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final WorkspaceAuthorizationService workspaceAuthorizationService;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    public ProjectWorkItemService(
            ProjectWorkItemRepository projectWorkItemRepository,
            ProjectActivityRepository projectActivityRepository,
            ProjectRepository projectRepository,
            UserRepository userRepository,
            WorkspaceAuthorizationService workspaceAuthorizationService, WorkspaceMemberRepository workspaceMemberRepository
    ) {
        this.projectWorkItemRepository = projectWorkItemRepository;
        this.projectActivityRepository = projectActivityRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.workspaceAuthorizationService = workspaceAuthorizationService;
        this.workspaceMemberRepository = workspaceMemberRepository;
    }

    @Transactional(readOnly = true)
    public List<ProjectWorkItemResponse> list(UUID workspaceId, UUID projectId, UUID currentUserId) {
        workspaceAuthorizationService.requireProjectAccess(projectId, workspaceId, currentUserId);
        return projectWorkItemRepository.findByProjectIdForProjectView(projectId)
            .stream()
            .map(ProjectWorkItemResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkspaceWorkItemResponse> listByWorkspace(
            UUID workspaceId,
            UUID currentUserId,
            ProjectWorkItemStatus status,
            ProjectWorkItemType type
    ) {
        workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUserId);
        return projectWorkItemRepository.findByWorkspaceIdForWorkspaceView(workspaceId, status, type)
            .stream()
            .map(WorkspaceWorkItemResponse::from)
            .toList();
    }

    @Transactional
    public ProjectWorkItemResponse create(
            UUID workspaceId,
            UUID projectId,
            UUID currentUserId,
            CreateProjectWorkItemRequest request
    ) {
        workspaceAuthorizationService.requireProjectAccess(projectId, workspaceId, currentUserId);
        ProjectWorkItem item = new ProjectWorkItem(
            projectRepository.getReferenceById(projectId),
            normalizeRequiredType(request.type()),
            normalizeRequiredTitle(request.title()),
            normalizeOptionalDetails(request.details()),
            userRepository.getReferenceById(currentUserId)
        );
        item.setAssignee(resolveAssignee(workspaceId, request.assigneeUserId())); item.setDueDate(request.dueDate());
        ProjectWorkItem savedItem = projectWorkItemRepository.save(item);
        registerActivity(savedItem, currentUserId, "%s criado: %s".formatted(workItemLabel(savedItem.getType()), savedItem.getTitle()));
        return ProjectWorkItemResponse.from(savedItem);
    }

    @Transactional public ProjectWorkItemResponse update(UUID workspaceId, UUID projectId, UUID workItemId, UUID currentUserId, UpdateProjectWorkItemRequest request) {
        workspaceAuthorizationService.requireProjectAccess(projectId, workspaceId, currentUserId);
        ProjectWorkItem item = projectWorkItemRepository.findByIdAndProjectId(workItemId, projectId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work item not found"));
        ProjectWorkItemType type=normalizeRequiredType(request.type()); String title=normalizeRequiredTitle(request.title()); String details=normalizeOptionalDetails(request.details()); User assignee=resolveAssignee(workspaceId, request.assigneeUserId()); LocalDate dueDate=request.dueDate();
        if(item.getType()==type && Objects.equals(item.getTitle(),title) && Objects.equals(item.getDetails(),details) && Objects.equals(item.getAssignee()==null?null:item.getAssignee().getId(),request.assigneeUserId()) && Objects.equals(item.getDueDate(),dueDate)) return ProjectWorkItemResponse.from(item);
        item.setType(type); item.setTitle(title); item.setDetails(details); item.setAssignee(assignee); item.setDueDate(dueDate); ProjectWorkItem saved=projectWorkItemRepository.saveAndFlush(item);
        registerActivity(saved,currentUserId,"%s atualizado: %s".formatted(workItemLabel(saved.getType()),saved.getTitle())); return ProjectWorkItemResponse.from(saved);
    }
    private User resolveAssignee(UUID workspaceId, UUID userId) { if(userId==null)return null; if(!workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId,userId)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Assignee must be a workspace member"); return userRepository.getReferenceById(userId); }

    @Transactional
    public ProjectWorkItemResponse updateStatus(
            UUID workspaceId,
            UUID projectId,
            UUID workItemId,
            UUID currentUserId,
            UpdateProjectWorkItemStatusRequest request
    ) {
        workspaceAuthorizationService.requireProjectAccess(projectId, workspaceId, currentUserId);
        ProjectWorkItem item = projectWorkItemRepository.findByIdAndProjectId(workItemId, projectId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work item not found"));
        ProjectWorkItemStatus status = normalizeRequiredStatus(request.status());
        if (status == item.getStatus()) {
            return ProjectWorkItemResponse.from(item);
        }
        item.setStatus(status);
        item.setCompletedAt(status == ProjectWorkItemStatus.DONE ? Instant.now() : null);
        ProjectWorkItem savedItem = projectWorkItemRepository.saveAndFlush(item);
        String action = status == ProjectWorkItemStatus.DONE
            ? (savedItem.getType() == ProjectWorkItemType.BLOCKER ? "resolvido" : "concluído")
            : "reaberto";
        registerActivity(savedItem, currentUserId, "%s %s: %s".formatted(workItemLabel(savedItem.getType()), action, savedItem.getTitle()));
        return ProjectWorkItemResponse.from(savedItem);
    }

    private ProjectWorkItemType normalizeRequiredType(ProjectWorkItemType type) {
        if (type == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Work item type is required");
        }
        return type;
    }

    private ProjectWorkItemStatus normalizeRequiredStatus(ProjectWorkItemStatus status) {
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Work item status is required");
        }
        return status;
    }

    private String normalizeRequiredTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Work item title is required");
        }
        String normalizedTitle = title.trim();
        if (normalizedTitle.length() > MAX_TITLE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Work item title is too long");
        }
        return normalizedTitle;
    }

    private String normalizeOptionalDetails(String details) {
        if (details == null || details.trim().isEmpty()) {
            return null;
        }
        String normalizedDetails = details.trim();
        if (normalizedDetails.length() > MAX_DETAILS_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Work item details are too long");
        }
        return normalizedDetails;
    }

    private void registerActivity(ProjectWorkItem item, UUID currentUserId, String content) {
        projectActivityRepository.save(new ProjectActivity(
            item.getProject(),
            userRepository.getReferenceById(currentUserId),
            activityType(item.getType()),
            content
        ));
    }

    private ProjectActivityType activityType(ProjectWorkItemType type) {
        return switch (type) {
            case NEXT_STEP -> ProjectActivityType.NEXT_STEP;
            case BLOCKER -> ProjectActivityType.BLOCKER;
            case PENDING -> ProjectActivityType.UPDATE;
        };
    }

    private String workItemLabel(ProjectWorkItemType type) {
        return switch (type) {
            case NEXT_STEP -> "Próximo passo";
            case PENDING -> "Pendência";
            case BLOCKER -> "Bloqueio";
        };
    }
}
