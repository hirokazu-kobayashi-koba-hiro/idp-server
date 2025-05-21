package org.idp.server.core.adapters.datasource.identity.verification.result.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.identity.verification.result.IdentityVerificationResult;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements IdentityVerificationResultCommandSqlExecutor {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Override
  public void insert(Tenant tenant, IdentityVerificationResult result) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                    INSERT INTO public.identity_verification_results
                    (id,
                    tenant_id,
                    user_id,
                    application_id,
                    verification_type,
                    external_application_id,
                    verified_claims,
                    verified_at,
                    valid_until,
                    source)
                    VALUES (
                    ?::uuid,
                    ?::uuid,
                    ?::uuid,
                    ?::uuid,
                    ?,
                    ?,
                    ?::jsonb,
                    ?,
                    ?,
                    ?
                    );
                """;

    List<Object> params = new ArrayList<>();
    params.add(result.identifier().value());
    params.add(tenant.identifierValue());
    params.add(result.userIdentifier().value());
    params.add(result.applicationId().value());
    params.add(result.identityVerificationType().name());
    params.add(result.externalApplicationId().value());
    params.add(jsonConverter.write(result.verifiedClaims().toMap()));
    params.add(result.verifiedAt().toString());
    if (result.hasVerifiedUntil()) {
      params.add(result.verifiedUntil().toString());
    } else {
      params.add(null);
    }
    params.add(result.source().name());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
