package zw.ac.msu.dzimbahub.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.msu.dzimbahub.auth.AppUser;
import zw.ac.msu.dzimbahub.dto.Dtos.PreferenceDto;
import zw.ac.msu.dzimbahub.model.Preference;
import zw.ac.msu.dzimbahub.repository.PreferenceRepository;

@Service
public class PreferenceService {
    private final PreferenceRepository repo;
    private final CurrentUsers current;
    public PreferenceService(PreferenceRepository repo, CurrentUsers current) { this.repo = repo; this.current = current; }

    public PreferenceDto get(String email) {
        AppUser u = current.require(email);
        return repo.findByUserId(u.getId()).map(p -> new PreferenceDto(p.getBudget(), p.getArea(), p.getRoomType(), p.isFurnished()))
                .orElse(new PreferenceDto(0, "", "", false));
    }

    @Transactional
    public PreferenceDto save(String email, PreferenceDto d) {
        AppUser u = current.require(email);
        Preference p = repo.findByUserId(u.getId()).orElseGet(() -> new Preference(u.getId()));
        p.set(d.budget(), d.area() == null ? "" : d.area().trim(), d.roomType() == null ? "" : d.roomType().trim(), d.furnished());
        repo.save(p);
        return get(email);
    }
}
