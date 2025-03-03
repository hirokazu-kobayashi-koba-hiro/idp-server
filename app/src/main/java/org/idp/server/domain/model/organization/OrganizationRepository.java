package org.idp.server.domain.model.organization;

public interface OrganizationRepository {
  void register(Organization organization);

  void update(Organization organization);

  Organization get(OrganizationIdentifier identifier);
}
