package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.DeleteWorkspaceRequest;
import com.no8do.api.workspace.Workspace;
import com.no8do.api.workspace.WorkspaceMember;
import com.no8do.api.workspace.WorkspaceMemberRepository;
import com.no8do.api.workspace.WorkspaceRepository;
import com.no8do.api.workspace.WorkspaceRole;
import com.no8do.api.workspace.WorkspaceService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AgentRegistryWorkspaceDeletionTests {

    @Autowired private AgentRegistryService agentRegistryService;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private AgentRepository agentRepository;
    @Autowired private AgentRegistryAuditEntryRepository auditRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
    @Test
    void workspaceDeletionRemovesAgentsButPreservesRegistryAuditHistory() {
        User owner = userRepository.save(new User("Delete workspace owner", "delete-workspace-"
                + UUID.randomUUID() + "@example.com", "hash"));
        Workspace workspace = workspaceRepository.save(new Workspace("Delete registry " + UUID.randomUUID()));
        workspaceMemberRepository.save(new WorkspaceMember(workspace, owner, WorkspaceRole.OWNER));
        Agent agent = agentRegistryService.createAgent(workspace.getId(), owner.getId(), "Contained", null, null);
        UUID agentId = agent.getId();
        UUID workspaceId = workspace.getId();
        UUID actorId = owner.getId();

        workspaceService.delete(workspaceId, actorId, new DeleteWorkspaceRequest(workspace.getName()));

        assertThat(workspaceRepository.findById(workspaceId)).isEmpty();
        assertThat(agentRepository.findById(agentId)).isEmpty();
        assertThat(auditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(agentId))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getActorUserId()).isEqualTo(actorId);
                    assertThat(entry.getWorkspaceId()).isEqualTo(workspaceId);
                    assertThat(entry.getAgentId()).isEqualTo(agentId);
                });
    }
}
