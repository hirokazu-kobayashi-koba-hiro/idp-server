/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.security.event;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.SecurityEvents;
import org.idp.server.platform.security.event.SecurityEventSearchCriteria;

public class PostgresqlExecutor implements SecurityEventSqlExecutor {

  JsonConverter converter = JsonConverter.snakeCaseInstance();

  @Override
  public void insert(SecurityEvent securityEvent) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                INSERT INTO security_event (
                id,
                type,
                description,
                tenant_id,
                tenant_name,
                client_id,
                client_name,
                user_id,
                user_name,
                login_hint,
                ip_address,
                user_agent,
                detail
                )
                VALUES (
                ?::uuid,
                ?,
                ?,
                ?::uuid,
                ?,
                ?,
                ?,
                ?::uuid,
                ?,
                ?,
                ?::INET,
                ?,
                ?::jsonb
                ) ON CONFLICT DO NOTHING;
                """;
    List<Object> params = new ArrayList<>();
    params.add(securityEvent.identifier().value());
    params.add(securityEvent.type().value());
    params.add(securityEvent.description().value());
    params.add(securityEvent.tenant().id());
    params.add(securityEvent.tenant().name());
    params.add(securityEvent.client().id());
    params.add(securityEvent.client().name());

    if (securityEvent.hasUser()) {
      params.add(securityEvent.user().id());
      params.add(securityEvent.user().name());
      // TODO login hint
      params.add(securityEvent.user().name());
    } else {
      params.add(null);
      params.add(null);
      params.add(null);
    }

    params.add(securityEvent.ipAddressValue());
    params.add(securityEvent.userAgentValue());

    params.add(converter.write(securityEvent.detail().toMap()));

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public SecurityEvents selectListByUser(String eventServerId, String userId) {
    return null;
  }

  @Override
  public SecurityEvents selectList(String eventServerId, SecurityEventSearchCriteria criteria) {
    return null;
  }
}
