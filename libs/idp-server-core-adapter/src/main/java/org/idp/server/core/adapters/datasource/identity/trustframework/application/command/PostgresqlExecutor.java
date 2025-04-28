package org.idp.server.core.adapters.datasource.identity.trustframework.application.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.basic.datasource.SqlExecutor;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplication;
import org.idp.server.core.tenant.Tenant;

public class PostgresqlExecutor implements IdentityVerificationApplicationCommandSqlExecutor {

  JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  @Override
  public void insert(Tenant tenant, IdentityVerificationApplication application) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                    INSERT INTO public.identity_verification_applications
                    (id, tenant_id, client_id, user_id, verification_type, application_details, trust_framework, trust_framework_details, status, requested_at, external_workflow_delegation, external_application_id, external_application_details)
                    VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?::jsonb, ?, ?, ?, ?, ?::jsonb);
                """;

    List<Object> params = new ArrayList<>();
    params.add(application.identifier().value());
    params.add(tenant.identifierValue());
    params.add(application.requestedClientId().value());
    params.add(application.sub());
    params.add(application.identityVerificationType().name());
    params.add(jsonConverter.write(application.applicationDetails().toMap()));

    if (application.hasTrustFramework()) {
      params.add(application.trustFramework().name());
    } else {
      params.add(null);
    }

    if (application.hasTrustFrameworkDetails()) {
      params.add(jsonConverter.write(application.trustFrameworkDetails().toMap()));
    } else {
      params.add(null);
    }

    params.add(application.status().name());
    params.add(application.requestedAt().toString());

    params.add(application.externalWorkflowDelegation().name());
    params.add(application.externalApplicationId().value());
    if (application.externalApplicationDetails() != null) {
      params.add(jsonConverter.write(application.externalApplicationDetails().toMap()));
    } else {
      params.add(null);
    }

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void update(Tenant tenant, IdentityVerificationApplication application) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            UPDATE identity_verification_applications
            SET application_details = ?::jsonb,
            trust_framework = ?,
            trust_framework_details = ?::jsonb,
            status = ?,
            external_application_details = ?::jsonb
            WHERE id = ?
            AND tenant_id = ?;
            """;
    List<Object> params = new ArrayList<>();
    params.add(jsonConverter.write(application.applicationDetails().toMap()));
    if (application.hasTrustFramework()) {
      params.add(application.trustFramework().name());
    } else {
      params.add(null);
    }
    if (application.hasTrustFrameworkDetails()) {
      params.add(jsonConverter.write(application.trustFrameworkDetails().toMap()));
    } else {
      params.add(null);
    }
    params.add(application.status().name());
    if (application.hasExternalApplicationDetails()) {
      params.add(jsonConverter.write(application.externalApplicationDetails().toMap()));
    }
    params.add(application.identifier().value());
    params.add(tenant.identifierValue());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
