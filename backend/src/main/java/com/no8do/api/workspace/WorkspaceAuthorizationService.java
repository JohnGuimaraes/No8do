package com.no8do.api.workspace;

import com.no8do.api.project.ProjectRepository;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkspaceAuthorizationService {

    private final ProjectRepository projectRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    public WorkspaceAuthorizationService(
            ProjectRepository projectRepository,
            WorkspaceMemberRepository workspaceMemberRepository
    ) {
        this.projectRepository = projectRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
    }

    public WorkspaceMember requireWorkspaceMember(UUID workspaceId, UUID userId) {
        return workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Workspace access denied"));
    }

    public WorkspaceMember requireWorkspaceRole(UUID workspaceId, UUID userId, WorkspaceRole... roles) {
        WorkspaceMember member = requireWorkspaceMember(workspaceId, userId);
        boolean allowed = Arrays.asList(roles).contains(member.getRole());
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Workspace role denied");
        }
        return member;
    }

    public WorkspaceMember requireWorkspaceManager(UUID workspaceId, UUID userId) {
        return requireWorkspaceRole(workspaceId, userId, WorkspaceRole.OWNER, WorkspaceRole.ADMIN);
    }

    public WorkspaceMember requireWorkspaceWrite(UUID workspaceId, UUID userId) {
        WorkspaceMember member = requireWorkspaceMember(workspaceId, userId);
        if (member.getRole() == WorkspaceRole.VIEWER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Workspace is read-only");
        }
        return member;
    }

    public void requireProjectAccess(UUID projectId, UUID workspaceId, UUID userId) {
        requireWorkspaceMember(workspaceId, userId);
        if (!projectRepository.existsByIdAndWorkspaceId(projectId, workspaceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
    }

    public void requireProjectWriteAccess(UUID projectId, UUID workspaceId, UUID userId) {
        requireWorkspaceWrite(workspaceId, userId);
        if (!projectRepository.existsByIdAndWorkspaceId(projectId, workspaceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
    }
}
