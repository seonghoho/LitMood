package com.litmood.domain.repository;

import com.litmood.domain.model.Content;
import com.litmood.domain.model.ProviderType;
import java.util.Optional;

public interface ContentRepository {

    Content save(Content content);

    Optional<Content> findById(Long id);

    Optional<Content> findByProviderAndExternalId(ProviderType provider, String externalId);
}
