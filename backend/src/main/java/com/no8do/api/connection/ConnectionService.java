package com.no8do.api.connection;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.WorkspaceAuthorizationService;
import com.no8do.api.workspace.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConnectionService {

    private final ConnectionRepository connectionRepository;
    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final WorkspaceAuthorizationService workspaceAuthorizationService;
    private final ConnectionRegistryAuditService auditService;
    private final ConnectionMetadataValidator metadataValidator;
    private final Clock clock;

    public ConnectionService(ConnectionRepository connectionRepository, WorkspaceRepository workspaceRepository,
            UserRepository userRepository, WorkspaceAuthorizationService workspaceAuthorizationService,
            ConnectionRegistryAuditService auditService, ConnectionMetadataValidator metadataValidator, Clock clock) {
        this.connectionRepository = connectionRepository;
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
        this.workspaceAuthorizationService = workspaceAuthorizationService;
        this.auditService = auditService;
        this.metadataValidator = metadataValidator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ConnectionResponse> list(UUID workspaceId, UUID actorUserId) {
        workspaceAuthorizationService.requireWorkspaceMember(workspaceId, actorUserId);
        return connectionRepository.findByWorkspace_IdOrderByCreatedAtDescIdAsc(workspaceId).stream()
                .map(ConnectionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ConnectionResponse get(UUID workspaceId, UUID connectionId, UUID actorUserId) {
        workspaceAuthorizationService.requireWorkspaceMember(workspaceId, actorUserId);
        return ConnectionResponse.from(findScoped(workspaceId, connectionId));
    }

    @Transactional
    public ConnectionResponse create(UUID workspaceId, UUID actorUserId, CreateConnectionRequest request) {
        workspaceAuthorizationService.requireWorkspaceManager(workspaceId, actorUserId);
        validateCredentialReference(request.credentialReferenceType(), request.credentialReferenceId());
        String name = normalizeName(request.name());
        ObjectNode metadata = metadataValidator.normalize(request.metadata());
        Instant now = clock.instant();

        Connection connection = new Connection(UUID.randomUUID(), workspaceRepository.getReferenceById(workspaceId),
                request.provider(), name, request.credentialReferenceType(), request.credentialReferenceId(),
                metadata, userRepository.getReferenceById(actorUserId), now);
        connection = connectionRepository.saveAndFlush(connection);
        auditService.recordCreated(actorUserId, connection, now);
        return ConnectionResponse.from(connection);
    }

    @Transactional
    public ConnectionResponse update(UUID workspaceId, UUID connectionId, UUID actorUserId,
            UpdateConnectionRequest request) {
        workspaceAuthorizationService.requireWorkspaceManager(workspaceId, actorUserId);
        Connection connection = findScoped(workspaceId, connectionId);
        if (connection.getStatus() == ConnectionStatus.DISCONNECTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Disconnected Connections cannot be updated");
        }

        String name = request.name() == null ? null : normalizeName(request.name());
        ObjectNode metadata = request.metadata() == null ? null : metadataValidator.normalize(request.metadata());
        List<String> changedFields = new ArrayList<>();
        if (name != null && !connection.getName().equals(name)) changedFields.add("name");
        if (metadata != null && !connection.getMetadata().equals(metadata)) changedFields.add("metadata");
        if (!changedFields.isEmpty()) {
            connection.update(name, metadata, clock.instant());
            auditService.recordUpdated(actorUserId, connection, clock.instant(), changedFields);
        }
        return ConnectionResponse.from(connection);
    }

    @Transactional
    public void disconnect(UUID workspaceId, UUID connectionId, UUID actorUserId) {
        workspaceAuthorizationService.requireWorkspaceManager(workspaceId, actorUserId);
        Connection connection = findScoped(workspaceId, connectionId);
        Instant now = clock.instant();
        if (connection.disconnect(now)) auditService.recordDisconnected(actorUserId, connection, now);
    }

    private Connection findScoped(UUID workspaceId, UUID connectionId) {
        return connectionRepository.findByIdAndWorkspace_Id(connectionId, workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Connection not found"));
    }

    private void validateCredentialReference(ConnectionCredentialReferenceType type, UUID referenceId) {
        if (type == null || (type == ConnectionCredentialReferenceType.NONE) != (referenceId == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Credential reference does not match its declared type");
        }
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 160) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Connection name is invalid");
        }
        String normalized = name.trim();
        metadataValidator.validateDisplayValue(normalized);
        return normalized;
    }
}
