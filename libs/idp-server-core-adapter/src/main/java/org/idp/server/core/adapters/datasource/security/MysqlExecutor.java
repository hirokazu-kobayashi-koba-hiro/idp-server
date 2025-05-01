package org.idp.server.core.adapters.datasource.security;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.basic.datasource.SqlExecutor;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.security.SecurityEvent;
import org.idp.server.core.security.SecurityEvents;
import org.idp.server.core.security.event.SecurityEventSearchCriteria;

public class MysqlExecutor implements SecurityEventSqlExecutor {

  JsonConverter converter = JsonConverter.createWithSnakeCaseStrategy();

  @Override
  public void insert(SecurityEvent securityEvent) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                INSERT IGNORE INTO security_event
               　(id, type, description, tenant_id, tenant_name, client_id, client_name, user_id, user_name, login_hint, ip_address, user_agent, detail)
               　VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
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
