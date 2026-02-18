package br.com.dms.service;

import java.util.Optional;

public interface BillingProviderClient {

    Optional<BillingProviderSubscriptionSnapshot> fetchSubscription(String externalSubscriptionId);
}
