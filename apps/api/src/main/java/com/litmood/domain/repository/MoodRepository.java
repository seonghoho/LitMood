package com.litmood.domain.repository;

import com.litmood.domain.model.Mood;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MoodRepository {

    Mood save(Mood mood);

    Optional<Mood> findByName(String normalizedName);

    List<Mood> findAllByNames(Collection<String> normalizedNames);

    /** 큐레이션 우선, 그다음 사용량순 (F-07-01). */
    List<Mood> findForPicker(int limit);
}
