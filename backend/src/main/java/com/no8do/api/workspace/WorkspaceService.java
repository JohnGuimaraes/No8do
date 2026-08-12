package com.no8do.api.workspace;

import com.no8do.api.user.UserRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkspaceService {

    private final UserRepository userRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceRepository workspaceRepository;

    public WorkspaceService(
            UserRepository userRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            WorkspaceRepository workspaceRepository
    ) {
        this.userRepository = userRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceRepository = workspaceRepository;
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

    private String normalizeRequiredName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workspace name is required");
        }
        return name.trim();
    }
}
