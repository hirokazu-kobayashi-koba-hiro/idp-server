package org.idp.server.core.adapters.datasource.organization;

import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.organization.Organization;
import org.idp.server.core.organization.OrganizationIdentifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MysqlExecutor implements OrganizationSqlExecutor {

  @Override
  public void insert(Organization organization) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlOrganizationTemplate =
        """
                INSERT INTO organization(id, name, description)
                VALUES (?, ?, ?);
                """;
    List<Object> organizationParams = new ArrayList<>();
    organizationParams.add(organization.identifier().value());
    organizationParams.add(organization.name().value());
    organizationParams.add(organization.description().value());

    sqlExecutor.execute(sqlOrganizationTemplate, organizationParams);

    StringBuilder sqlTemplateBuilder =
        new StringBuilder(
            """
                         INSERT INTO organization_tenants(organization_id, tenant_id)
                          VALUES
                        """);
    List<String> sqlValues = new ArrayList<>();
    List<Object> tenantParams = new ArrayList<>();

    organization
        .assignedTenants()
        .forEach(
            organizationTenant -> {
              sqlValues.add("(?, ?)");
              tenantParams.add(organization.identifier().value());
              tenantParams.add(organizationTenant.identifier().value());
            });
    sqlTemplateBuilder.append(String.join(",", sqlValues));
    sqlTemplateBuilder.append(";");

    sqlExecutor.execute(sqlTemplateBuilder.toString(), tenantParams);
  }

  @Override
  public void update(Organization organization) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                UPDATE organization
                SET name = ?
                description = ?
                WHERE
                id = ?
                """;

    List<Object> organizationParams = new ArrayList<>();
    organizationParams.add(organization.name().value());
    organizationParams.add(organization.description().value());
    organizationParams.add(organization.identifier().value());

    sqlExecutor.execute(sqlTemplate, organizationParams);
  }

  @Override
  public Map<String, String> selectOne(OrganizationIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                SELECT id, name, type, issuer FROM organization
                WHERE id = ?
                """;
    List<Object> params = new ArrayList<>();
    params.add(identifier.value());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }
}
