package org.idp.server.core.federation;

public interface FederatableIdProviderConfigurationRepository {

  FederatableIdProviderConfiguration get(String identifier);
}
