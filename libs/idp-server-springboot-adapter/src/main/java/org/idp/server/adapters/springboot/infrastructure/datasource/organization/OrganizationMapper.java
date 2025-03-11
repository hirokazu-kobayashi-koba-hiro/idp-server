package org.idp.server.adapters.springboot.infrastructure.datasource.organization;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.idp.server.adapters.springboot.domain.model.organization.Organization;
import org.idp.server.adapters.springboot.domain.model.organization.OrganizationIdentifier;

@Mapper
public interface OrganizationMapper {
  void insert(@Param("organization") Organization organization);

  void update(@Param("organization") Organization organization);

  void insertTenants(@Param("organization") Organization organization);

  Organization selectBy(
      @Param("organizationIdentifier") OrganizationIdentifier organizationIdentifier);
}
