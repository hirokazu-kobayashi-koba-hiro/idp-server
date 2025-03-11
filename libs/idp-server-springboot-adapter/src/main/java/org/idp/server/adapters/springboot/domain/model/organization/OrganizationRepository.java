package org.idp.server.adapters.springboot.domain.model.organization;

public interface OrganizationRepository {
  void register(Organization organization);

  void update(Organization organization);

  Organization get(OrganizationIdentifier identifier);
}
