package org.idp.sample.infrastructure.datasource.organization;

import org.idp.sample.domain.model.organization.Organization;
import org.idp.sample.domain.model.organization.OrganizationIdentifier;
import org.idp.sample.domain.model.organization.OrganizationRepository;
import org.springframework.stereotype.Repository;

@Repository
public class OrganizationDataSource implements OrganizationRepository {

  @Override
  public void register(Organization organization) {}

  @Override
  public void update(Organization organization) {}

  @Override
  public Organization get(OrganizationIdentifier identifier) {
    return null;
  }
}
