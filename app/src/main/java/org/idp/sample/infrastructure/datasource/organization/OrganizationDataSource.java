package org.idp.sample.infrastructure.datasource.organization;

import org.idp.sample.domain.model.organization.Organization;
import org.idp.sample.domain.model.organization.OrganizationIdentifier;
import org.idp.sample.domain.model.organization.OrganizationRepository;
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
      throw new RuntimeException("Organization not found");
    }
    return organization;
  }
}
