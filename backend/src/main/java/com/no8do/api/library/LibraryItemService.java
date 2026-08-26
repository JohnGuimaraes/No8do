package com.no8do.api.library;

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
public class LibraryItemService {

    private static final int MAX_TITLE_LENGTH = 180;
    private static final int MAX_DESCRIPTION_LENGTH = 2000;
    private static final int MAX_CONTENT_LENGTH = 20000;
    private static final int MAX_URL_LENGTH = 2000;

    private final LibraryItemRepository libraryItemRepository;
    private final UserRepository userRepository;
    private final WorkspaceAuthorizationService workspaceAuthorizationService;
    private final WorkspaceRepository workspaceRepository;

    public LibraryItemService(
            LibraryItemRepository libraryItemRepository,
            UserRepository userRepository,
            WorkspaceAuthorizationService workspaceAuthorizationService,
            WorkspaceRepository workspaceRepository
    ) {
        this.libraryItemRepository = libraryItemRepository;
        this.userRepository = userRepository;
        this.workspaceAuthorizationService = workspaceAuthorizationService;
        this.workspaceRepository = workspaceRepository;
    }

    @Transactional(readOnly = true)
    public List<LibraryItemResponse> list(UUID workspaceId, UUID currentUserId, boolean archived) {
        workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUserId);
        List<LibraryItem> items = archived
            ? libraryItemRepository.findByWorkspaceIdAndArchivedAtIsNotNullOrderByArchivedAtDesc(workspaceId)
            : libraryItemRepository.findByWorkspaceIdAndArchivedAtIsNullOrderByUpdatedAtDesc(workspaceId);
        return items
            .stream()
            .map(LibraryItemResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public LibraryItemResponse get(UUID workspaceId, UUID itemId, UUID currentUserId) {
        workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUserId);
        return libraryItemRepository.findByIdAndWorkspaceId(itemId, workspaceId)
            .map(LibraryItemResponse::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Library item not found"));
    }

    @Transactional
    public LibraryItemResponse create(UUID workspaceId, UUID currentUserId, LibraryItemRequest request) {
        workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUserId);
        LibraryItem item = new LibraryItem(
            workspaceRepository.getReferenceById(workspaceId),
            normalizeType(request.type()),
            normalizeRequiredTitle(request.title()),
            userRepository.getReferenceById(currentUserId)
        );
        apply(item, request);
        return LibraryItemResponse.from(libraryItemRepository.save(item));
    }

    @Transactional
    public LibraryItemResponse update(UUID workspaceId, UUID itemId, UUID currentUserId, LibraryItemRequest request) {
        workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUserId);
        LibraryItem item = libraryItemRepository.findByIdAndWorkspaceId(itemId, workspaceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Library item not found"));
        item.setType(normalizeType(request.type()));
        item.setTitle(normalizeRequiredTitle(request.title()));
        apply(item, request);
        return LibraryItemResponse.from(libraryItemRepository.saveAndFlush(item));
    }

    @Transactional
    public LibraryItemResponse archive(UUID workspaceId, UUID itemId, UUID currentUserId) {
        LibraryItem item = requireItem(workspaceId, itemId, currentUserId);
        if (item.getArchivedAt() == null) {
            item.setArchivedAt(java.time.Instant.now());
            item.setArchivedBy(userRepository.getReferenceById(currentUserId));
        }
        return LibraryItemResponse.from(item);
    }

    @Transactional
    public LibraryItemResponse restore(UUID workspaceId, UUID itemId, UUID currentUserId) {
        LibraryItem item = requireItem(workspaceId, itemId, currentUserId);
        if (item.getArchivedAt() != null) {
            item.setArchivedAt(null);
            item.setArchivedBy(null);
        }
        return LibraryItemResponse.from(item);
    }

    @Transactional
    public void delete(UUID workspaceId, UUID itemId, UUID currentUserId) {
        libraryItemRepository.delete(requireItem(workspaceId, itemId, currentUserId));
    }

    private LibraryItem requireItem(UUID workspaceId, UUID itemId, UUID currentUserId) {
        workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUserId);
        return libraryItemRepository.findByIdAndWorkspaceId(itemId, workspaceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Library item not found"));
    }

    private void apply(LibraryItem item, LibraryItemRequest request) {
        item.setDescription(normalizeOptional(request.description(), MAX_DESCRIPTION_LENGTH, "Description is too long"));
        item.setContent(normalizeOptional(request.content(), MAX_CONTENT_LENGTH, "Content is too long"));
        item.setUrl(normalizeOptional(request.url(), MAX_URL_LENGTH, "URL is too long"));
    }

    private LibraryItemType normalizeType(String type) {
        if (type == null || type.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Library item type is required");
        }
        try {
            return LibraryItemType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Library item type is invalid");
        }
    }

    private String normalizeRequiredTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Library item title is required");
        }
        String normalizedTitle = title.trim();
        if (normalizedTitle.length() > MAX_TITLE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Library item title is too long");
        }
        return normalizedTitle;
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
