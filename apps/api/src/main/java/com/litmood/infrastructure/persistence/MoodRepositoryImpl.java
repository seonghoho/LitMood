package com.litmood.infrastructure.persistence;

import com.litmood.domain.model.Mood;
import com.litmood.domain.repository.MoodRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

interface MoodJpaRepository extends JpaRepository<Mood, Long> {

    Optional<Mood> findByName(String name);

    List<Mood> findByNameIn(Collection<String> names);

    @Query("SELECT m FROM Mood m ORDER BY m.curated DESC, m.usageCount DESC, m.id ASC")
    List<Mood> findForPicker(Limit limit);
}

@Repository
class MoodRepositoryImpl implements MoodRepository {

    private final MoodJpaRepository jpa;

    MoodRepositoryImpl(MoodJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Mood save(Mood mood) {
        return jpa.save(mood);
    }

    @Override
    public Optional<Mood> findByName(String normalizedName) {
        return jpa.findByName(normalizedName);
    }

    @Override
    public List<Mood> findAllByNames(Collection<String> normalizedNames) {
        return normalizedNames.isEmpty() ? List.of() : jpa.findByNameIn(normalizedNames);
    }

    @Override
    public List<Mood> findForPicker(int limit) {
        return jpa.findForPicker(Limit.of(limit));
    }
}
