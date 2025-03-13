package org.idp.server.adapters.springboot.infrastructure.datasource.organization;

import org.idp.server.core.organization.Organization;
import org.idp.server.core.organization.OrganizationIdentifier;
import org.idp.server.core.organization.OrganizationNotFoundException;
import org.idp.server.core.organization.OrganizationRepository;
import org.springframework.stereotype.Repository;

@Repository
public class OrganizationDataSource implements OrganizationRepository {

  OrganizationMapper mapper;

  public OrganizationDataSource(OrganizationMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void register(Organization organization) {
    mapper.insert(organization);
    mapper.insertTenants(organization);
  }

  @Override
  public void update(Organization organization) {
    mapper.update(organization);
  }

  @Override
  public Organization get(OrganizationIdentifier identifier) {
    Organization organization = mapper.selectBy(identifier);
    if (organization == null) {
      throw new OrganizationNotFoundException("Organization not found");
    }
    return organization;
  }
}
