package com.no8do.api.agent;

import com.no8do.api.project.Project;
import com.no8do.api.project.ProjectRepository;
import com.no8do.api.workspace.WorkspaceAuthorizationService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentProjectAssignmentService {

    private final AgentRepository agentRepository;
    private final ProjectRepository projectRepository;
    private final AgentProjectAssignmentRepository assignmentRepository;
    private final WorkspaceAuthorizationService workspaceAuthorizationService;
    private final AgentRegistryAuditService auditService;
    private final Clock clock;

    public AgentProjectAssignmentService(AgentRepository agentRepository, ProjectRepository projectRepository,
            AgentProjectAssignmentRepository assignmentRepository,
            WorkspaceAuthorizationService workspaceAuthorizationService, AgentRegistryAuditService auditService,
            Clock clock) {
        this.agentRepository = agentRepository;
        this.projectRepository = projectRepository;
        this.assignmentRepository = assignmentRepository;
        this.workspaceAuthorizationService = workspaceAuthorizationService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AgentProjectAssignmentResponse> list(UUID workspaceId, UUID agentId, UUID actorUserId) {
        Agent agent = requireManagerScopedAgent(workspaceId, agentId, actorUserId);
        return assignmentRepository.findProjectSummaries(workspaceId, agent.getId());
    }

    @Transactional
    public void assign(UUID workspaceId, UUID agentId, UUID projectId, UUID actorUserId) {
        Agent agent = requireManagerScopedAgentForUpdate(workspaceId, agentId, actorUserId);
        Project project = findProject(workspaceId, projectId);
        if (agent.getLifecycleStatus() == AgentLifecycleStatus.ARCHIVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived Agents cannot receive Project assignments");
        }

        Instant assignedAt = clock.instant();
        int inserted = assignmentRepository.insertIfAbsent(UUID.randomUUID(), agent.getId(), project.getId(),
                assignedAt, actorUserId);
        if (inserted == 1) auditService.recordProjectAssigned(actorUserId, agent, project.getId(), assignedAt);
    }

    @Transactional
    public void unassign(UUID workspaceId, UUID agentId, UUID projectId, UUID actorUserId) {
        Agent agent = requireManagerScopedAgent(workspaceId, agentId, actorUserId);
        Project project = findProject(workspaceId, projectId);
        int deleted = assignmentRepository.deleteAssignment(agent.getId(), project.getId());
        if (deleted == 1) auditService.recordProjectUnassigned(actorUserId, agent, project.getId(), clock.instant());
    }

    private Agent requireManagerScopedAgent(UUID workspaceId, UUID agentId, UUID actorUserId) {
        Agent agent = agentRepository.findByIdAndWorkspaceId(agentId, workspaceId)
                .orElseThrow(AgentProjectAssignmentService::notFound);
        workspaceAuthorizationService.requireWorkspaceManager(workspaceId, actorUserId);
        return agent;
    }

    private Agent requireManagerScopedAgentForUpdate(UUID workspaceId, UUID agentId, UUID actorUserId) {
        Agent agent = agentRepository.findByIdAndWorkspaceIdForUpdate(agentId, workspaceId)
                .orElseThrow(AgentProjectAssignmentService::notFound);
        workspaceAuthorizationService.requireWorkspaceManager(workspaceId, actorUserId);
        return agent;
    }

    private Project findProject(UUID workspaceId, UUID projectId) {
        return projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(AgentProjectAssignmentService::notFound);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found");
    }
}
