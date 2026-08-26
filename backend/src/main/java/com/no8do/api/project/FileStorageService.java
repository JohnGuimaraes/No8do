package com.no8do.api.project;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FileStorageService {
    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024;
    private final Path uploadsDirectory;

    public FileStorageService(@Value("${NO8DO_UPLOADS_DIR:${user.home}/.no8do/uploads}") String uploadsDirectory) {
        this.uploadsDirectory = Path.of(uploadsDirectory).toAbsolutePath().normalize();
    }

    public StoredFile storeProjectCover(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_IMAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid project cover");
        }
        String extension = validateAndGetExtension(file);
        String key = UUID.randomUUID() + extension;
        try {
            Files.createDirectories(uploadsDirectory);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, resolve(key), StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredFile(key, extension.equals(".png") ? "image/png" : "image/jpeg");
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store project cover", exception);
        }
    }

    public byte[] read(String key) {
        try { return Files.readAllBytes(resolve(key)); }
        catch (IOException exception) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project cover not found"); }
    }

    public String contentType(String key) { return key.endsWith(".png") ? "image/png" : "image/jpeg"; }

    public void delete(String key) {
        if (key == null) return;
        try { Files.deleteIfExists(resolve(key)); } catch (IOException ignored) { }
    }

    private String validateAndGetExtension(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            BufferedImage image = ImageIO.read(input);
            if (image == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project cover must be a PNG or JPEG image");
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project cover must be a PNG or JPEG image");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if ("image/png".equals(contentType)) return ".png";
        if ("image/jpeg".equals(contentType)) return ".jpg";
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project cover must be a PNG or JPEG image");
    }

    private Path resolve(String key) {
        if (!key.matches("[0-9a-fA-F-]{36}\\.(png|jpg)")) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project cover not found");
        Path result = uploadsDirectory.resolve(key).normalize();
        if (!result.startsWith(uploadsDirectory)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project cover not found");
        return result;
    }

    public record StoredFile(String key, String contentType) { }
}
