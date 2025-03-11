package org.idp.server.application.service.organization;

import org.idp.server.domain.model.organization.Organization;
import org.idp.server.domain.model.organization.OrganizationIdentifier;
import org.idp.server.domain.model.organization.OrganizationRepository;
import org.springframework.stereotype.Service;

@Service
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
