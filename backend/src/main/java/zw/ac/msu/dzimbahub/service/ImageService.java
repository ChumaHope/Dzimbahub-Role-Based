package zw.ac.msu.dzimbahub.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import zw.ac.msu.dzimbahub.auth.AppUser;
import zw.ac.msu.dzimbahub.model.PropertyImage;
import zw.ac.msu.dzimbahub.repository.PropertyImageRepository;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Stores landlord photos and serves them back at /api/images/{id}. */
@Service
public class ImageService {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    public static final String URL_PREFIX = "/api/images/";
    private static final Pattern OWN_URL = Pattern.compile("^/api/images/(\\d+)$");

    public record Uploaded(Long id, String url) {}

    private final PropertyImageRepository images;
    private final CurrentUsers current;
    private final AuditService audit;

    public ImageService(PropertyImageRepository images, CurrentUsers current, AuditService audit) {
        this.images = images; this.current = current; this.audit = audit;
    }

    /** Works out the real file type from the first bytes, so a renamed file cannot pretend to be a photo. */
    static String detectType(byte[] b) {
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        return null;
    }

    @Transactional
    public Uploaded upload(String landlordEmail, MultipartFile file) {
        AppUser owner = current.require(landlordEmail);
        if (file == null || file.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a photo to upload.");
        if (file.getSize() > MAX_BYTES)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Each photo must be smaller than 5 MB.");
        byte[] bytes;
        try { bytes = file.getBytes(); }
        catch (IOException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The photo could not be read."); }
        String type = detectType(bytes);
        if (type == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only JPG, PNG or WebP photos can be uploaded.");
        PropertyImage saved = images.save(new PropertyImage(owner.getId(), type, bytes));
        audit.record(landlordEmail, "PHOTO_UPLOADED", "PropertyImage", saved.getId(), bytes.length / 1024 + " KB");
        return new Uploaded(saved.getId(), URL_PREFIX + saved.getId());
    }

    public Optional<PropertyImage> find(Long id) { return images.findById(id); }

    /** Id of one of our own uploaded photos, or null for an external link. */
    static Long idOf(String url) {
        if (url == null) return null;
        Matcher m = OWN_URL.matcher(url.trim());
        return m.matches() ? Long.valueOf(m.group(1)) : null;
    }

    /** A landlord may only attach their own uploads to a listing. */
    public void checkOwnership(Long ownerId, Collection<String> urls) {
        for (String url : urls) {
            Long id = idOf(url);
            if (id == null) continue;
            PropertyImage img = images.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "A photo was not found. Please upload it again."));
            if (!img.getOwnerId().equals(ownerId))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only use photos you uploaded.");
        }
    }

    /** Removes our stored photos that a listing no longer uses. */
    @Transactional
    public void deleteUnused(List<String> before, List<String> after) {
        for (String url : before) {
            Long id = idOf(url);
            if (id != null && !after.contains(url)) images.deleteById(id);
        }
    }
}
