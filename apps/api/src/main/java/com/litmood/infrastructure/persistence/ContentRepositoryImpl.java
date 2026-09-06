package com.litmood.infrastructure.persistence;

import com.litmood.domain.model.Content;
import com.litmood.domain.model.ProviderType;
import com.litmood.domain.repository.ContentRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

interface ContentJpaRepository extends JpaRepository<Content, Long> {
    Optional<Content> findByProviderAndExternalId(ProviderType provider, String externalId);
}

@Repository
class ContentRepositoryImpl implements ContentRepository {

    private final ContentJpaRepository jpa;

    ContentRepositoryImpl(ContentJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Content save(Content content) {
        return jpa.save(content);
    }

    @Override
    public Optional<Content> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<Content> findByProviderAndExternalId(ProviderType provider, String externalId) {
        return jpa.findByProviderAndExternalId(provider, externalId);
    }

    @Override
    public List<Content> findAllById(List<Long> ids) {
        return ids.isEmpty() ? List.of() : jpa.findAllById(ids);
    }
}
