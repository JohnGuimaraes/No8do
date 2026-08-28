package com.no8do.api.credential;

import com.no8do.api.project.ProjectRepository;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.WorkspaceAuthorizationService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectCredentialService {

    private static final int MAX_LABEL_LENGTH = 180;
    private static final int MAX_USERNAME_LENGTH = 255;
    private static final int MAX_SECRET_LENGTH = 20_000;
    private static final int MAX_NOTES_LENGTH = 500;

    private final ProjectCredentialRepository projectCredentialRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final WorkspaceAuthorizationService workspaceAuthorizationService;
    private final CredentialCryptoService credentialCryptoService;

    public ProjectCredentialService(
            ProjectCredentialRepository projectCredentialRepository,
            ProjectRepository projectRepository,
            UserRepository userRepository,
            WorkspaceAuthorizationService workspaceAuthorizationService,
            CredentialCryptoService credentialCryptoService
    ) {
        this.projectCredentialRepository = projectCredentialRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.workspaceAuthorizationService = workspaceAuthorizationService;
        this.credentialCryptoService = credentialCryptoService;
    }

    @Transactional(readOnly = true)
    public List<ProjectCredentialResponse> list(UUID workspaceId, UUID projectId, UUID currentUserId) {
        workspaceAuthorizationService.requireProjectAccess(projectId, workspaceId, currentUserId);
        return projectCredentialRepository.findByProjectIdOrderByUpdatedAtDesc(projectId)
            .stream()
            .map(ProjectCredentialResponse::from)
            .toList();
    }

    @Transactional
    public ProjectCredentialResponse create(
            UUID workspaceId,
            UUID projectId,
            UUID currentUserId,
            CreateProjectCredentialRequest request
    ) {
        workspaceAuthorizationService.requireProjectWriteAccess(projectId, workspaceId, currentUserId);
        String normalizedLabel = normalizeRequiredLabel(request.label());
        ProjectCredentialType normalizedType = normalizeRequiredType(request.type());
        String normalizedUsername = normalizeOptional(
            request.username(),
            MAX_USERNAME_LENGTH,
            "Credential username is too long"
        );
        String normalizedSecret = normalizeRequiredSecret(request.secret());
        String normalizedNotes = normalizeOptional(request.notes(), MAX_NOTES_LENGTH, "Credential notes are too long");
        EncryptedCredentialSecret encryptedSecret = credentialCryptoService.encrypt(normalizedSecret);
        ProjectCredential credential = new ProjectCredential(
            projectRepository.getReferenceById(projectId),
            normalizedLabel,
            normalizedType,
            normalizedUsername,
            encryptedSecret.ciphertext(),
            encryptedSecret.iv(),
            encryptedSecret.keyVersion(),
            normalizedNotes,
            userRepository.getReferenceById(currentUserId)
        );
        return ProjectCredentialResponse.from(projectCredentialRepository.save(credential));
    }

    @Transactional(readOnly = true)
    public RevealProjectCredentialResponse reveal(
            UUID workspaceId,
            UUID projectId,
            UUID credentialId,
            UUID currentUserId
    ) {
        workspaceAuthorizationService.requireProjectWriteAccess(projectId, workspaceId, currentUserId);
        ProjectCredential credential = projectCredentialRepository.findByIdAndProjectId(credentialId, projectId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Credential not found"));
        String secret = credentialCryptoService.decrypt(credential.getSecretCiphertext(), credential.getSecretIv());
        return new RevealProjectCredentialResponse(credential.getId(), secret);
    }

    private String normalizeRequiredLabel(String label) {
        if (label == null || label.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Credential label is required");
        }
        String normalizedLabel = label.trim();
        if (normalizedLabel.length() > MAX_LABEL_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Credential label is too long");
        }
        return normalizedLabel;
    }

    private ProjectCredentialType normalizeRequiredType(ProjectCredentialType type) {
        if (type == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Credential type is required");
        }
        return type;
    }

    private String normalizeRequiredSecret(String secret) {
        if (secret == null || secret.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Credential secret is required");
        }
        if (secret.length() > MAX_SECRET_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Credential secret is too long");
        }
        return secret;
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
