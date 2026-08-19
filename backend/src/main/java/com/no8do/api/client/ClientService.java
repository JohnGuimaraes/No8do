package com.no8do.api.client;

import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.WorkspaceAuthorizationService;
import com.no8do.api.workspace.WorkspaceRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClientService {

    private static final int MAX_NAME_LENGTH = 180;
    private static final int MAX_COMPANY_NAME_LENGTH = 255;
    private static final int MAX_EMAIL_LENGTH = 320;
    private static final int MAX_PHONE_LENGTH = 50;
    private static final int MAX_NOTES_LENGTH = 5000;

    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final WorkspaceAuthorizationService workspaceAuthorizationService;
    private final WorkspaceRepository workspaceRepository;

    public ClientService(
            ClientRepository clientRepository,
            UserRepository userRepository,
            WorkspaceAuthorizationService workspaceAuthorizationService,
            WorkspaceRepository workspaceRepository
    ) {
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
        this.workspaceAuthorizationService = workspaceAuthorizationService;
        this.workspaceRepository = workspaceRepository;
    }

    @Transactional(readOnly = true)
    public List<ClientResponse> list(UUID workspaceId, UUID currentUserId) {
        workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUserId);
        return clientRepository.findByWorkspaceIdOrderByUpdatedAtDesc(workspaceId)
            .stream()
            .map(ClientResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public ClientResponse get(UUID workspaceId, UUID clientId, UUID currentUserId) {
        workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUserId);
        return clientRepository.findByIdAndWorkspaceId(clientId, workspaceId)
            .map(ClientResponse::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
    }

    @Transactional
    public ClientResponse create(UUID workspaceId, UUID currentUserId, ClientRequest request) {
        workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUserId);
        Client client = new Client(
            workspaceRepository.getReferenceById(workspaceId),
            normalizeRequiredName(request.name()),
            userRepository.getReferenceById(currentUserId)
        );
        apply(client, request);
        return ClientResponse.from(clientRepository.save(client));
    }

    @Transactional
    public ClientResponse update(UUID workspaceId, UUID clientId, UUID currentUserId, ClientRequest request) {
        workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUserId);
        Client client = clientRepository.findByIdAndWorkspaceId(clientId, workspaceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
        client.setName(normalizeRequiredName(request.name()));
        apply(client, request);
        return ClientResponse.from(clientRepository.saveAndFlush(client));
    }

    private void apply(Client client, ClientRequest request) {
        client.setCompanyName(normalizeOptional(request.companyName(), MAX_COMPANY_NAME_LENGTH, "Company name is too long"));
        client.setEmail(normalizeOptional(request.email(), MAX_EMAIL_LENGTH, "Email is too long"));
        client.setPhone(normalizeOptional(request.phone(), MAX_PHONE_LENGTH, "Phone is too long"));
        client.setNotes(normalizeOptional(request.notes(), MAX_NOTES_LENGTH, "Notes are too long"));
    }

    private String normalizeRequiredName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Client name is required");
        }
        String normalizedName = name.trim();
        if (normalizedName.length() > MAX_NAME_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Client name is too long");
        }
        return normalizedName;
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
