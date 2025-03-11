package org.idp.server.adapters.springboot.application.service.organization;

import org.idp.server.adapters.springboot.domain.model.organization.Organization;
import org.idp.server.adapters.springboot.domain.model.organization.OrganizationIdentifier;
import org.idp.server.adapters.springboot.domain.model.organization.OrganizationRepository;
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
