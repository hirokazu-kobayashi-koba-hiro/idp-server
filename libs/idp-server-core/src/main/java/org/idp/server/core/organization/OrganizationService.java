package org.idp.server.core.organization;

public class OrganizationService {

  OrganizationRepository organizationRepository;

  public OrganizationService(OrganizationRepository organizationRepository) {
    this.organizationRepository = organizationRepository;
  }

  public void register(Organization organization) {
    organizationRepository.register(organization);
  }

  public Organization get(OrganizationIdentifier identifier) {
    return organizationRepository.get(identifier);
  }

  public void update(Organization organization) {
    organizationRepository.update(organization);
  }
}
