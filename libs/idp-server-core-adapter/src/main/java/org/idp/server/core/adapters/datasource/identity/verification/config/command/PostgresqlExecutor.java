package org.idp.server.core.adapters.datasource.identity.verification.config.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.identity.verification.IdentityVerificationType;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements IdentityVerificationConfigCommandSqlExecutor {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Override
  public void insert(
      Tenant tenant,
      IdentityVerificationType type,
      IdentityVerificationConfiguration configuration) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            INSERT INTO identity_verification_configurations (
            id,
            tenant_id,
            type,
            payload
            )
            VALUES (
            ?::uuid,
            ?::uuid,
            ?,
            ?::jsonb
            );
            """;

    List<Object> params = new ArrayList<>();
    params.add(configuration.id());
    params.add(tenant.identifierValue());
    params.add(type.name());
    params.add(jsonConverter.write(configuration));

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void update(
      Tenant tenant,
      IdentityVerificationType type,
      IdentityVerificationConfiguration configuration) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            UPDATE identity_verification_configurations
            SET payload = ?::jsonb
            WHERE id = ?::uuid
            AND type = ?
            AND tenant_id = ?::uuid;
            """;

    List<Object> params = new ArrayList<>();
    params.add(jsonConverter.write(configuration));
    params.add(configuration.id());
    params.add(type.name());
    params.add(tenant.identifierValue());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void delete(
      Tenant tenant,
      IdentityVerificationType type,
      IdentityVerificationConfiguration configuration) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            DELETE FROM identity_verification_configurations
            WHERE tenant_id = ?::uuid
            AND type = ?
            AND id = ?::uuid;
            """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(type.name());
    params.add(configuration.id());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
