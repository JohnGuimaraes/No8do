package com.no8do.api.github;

import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.WorkspaceAuthorizationService;
import com.no8do.api.workspace.WorkspaceRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkspaceGithubLinkService {

    private final WorkspaceAuthorizationService workspaceAuthorizationService;
    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final UserGithubConnectionRepository userConnectionRepository;
    private final WorkspaceGithubLinkRepository linkRepository;

    public WorkspaceGithubLinkService(WorkspaceAuthorizationService workspaceAuthorizationService, WorkspaceRepository workspaceRepository,
            UserRepository userRepository, UserGithubConnectionRepository userConnectionRepository, WorkspaceGithubLinkRepository linkRepository) {
        this.workspaceAuthorizationService = workspaceAuthorizationService;
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
        this.userConnectionRepository = userConnectionRepository;
        this.linkRepository = linkRepository;
    }

    @Transactional(readOnly = true)
    public WorkspaceGithubLinkResponse get(UUID workspaceId, UUID currentUserId) {
        requireManager(workspaceId, currentUserId);
        return linkRepository.findByWorkspaceId(workspaceId).map(WorkspaceGithubLinkResponse::from).orElseGet(WorkspaceGithubLinkResponse::unlinked);
    }

    @Transactional
    public WorkspaceGithubLinkResponse linkMyGithub(UUID workspaceId, UUID currentUserId) {
        requireManager(workspaceId, currentUserId);
        UserGithubConnection connection = userConnectionRepository.findById(currentUserId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Connect GitHub in your profile first"));
        WorkspaceGithubLink existingLink = linkRepository.findByWorkspaceId(workspaceId).orElse(null);
        if (existingLink != null) {
            return WorkspaceGithubLinkResponse.from(existingLink);
        }
        WorkspaceGithubLink link = new WorkspaceGithubLink(
            workspaceRepository.getReferenceById(workspaceId), connection, userRepository.getReferenceById(currentUserId)
        );
        return WorkspaceGithubLinkResponse.from(linkRepository.save(link));
    }

    @Transactional
    public void unlink(UUID workspaceId, UUID currentUserId) {
        requireManager(workspaceId, currentUserId);
        linkRepository.deleteById(workspaceId);
    }

    private void requireManager(UUID workspaceId, UUID currentUserId) {
        workspaceAuthorizationService.requireWorkspaceManager(workspaceId, currentUserId);
    }
}
