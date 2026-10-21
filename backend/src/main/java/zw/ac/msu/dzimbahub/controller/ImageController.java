package zw.ac.msu.dzimbahub.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import zw.ac.msu.dzimbahub.model.PropertyImage;
import zw.ac.msu.dzimbahub.service.ImageService;
import java.util.concurrent.TimeUnit;

/** Public: serves uploaded listing photos. A photo never changes, so browsers may cache it for a long time. */
@RestController
public class ImageController {
    private final ImageService images;
    public ImageController(ImageService images) { this.images = images; }

    @GetMapping("/api/images/{id}")
    public ResponseEntity<byte[]> image(@PathVariable Long id) {
        PropertyImage img = images.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found."));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(img.getContentType()))
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(img.getData());
    }
}
