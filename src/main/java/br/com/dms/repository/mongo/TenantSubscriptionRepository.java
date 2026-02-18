package br.com.dms.repository.mongo;

import br.com.dms.domain.mongodb.TenantSubscription;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface TenantSubscriptionRepository extends MongoRepository<TenantSubscription, String> {

    Optional<TenantSubscription> findByTenantId(String tenantId);
}
