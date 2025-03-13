package org.idp.server.core.organization;

public interface OrganizationRepository {
  void register(Organization organization);

  void update(Organization organization);

  Organization get(OrganizationIdentifier identifier);
}
