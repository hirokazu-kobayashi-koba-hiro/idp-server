package org.idp.server.core.adapters.datasource.sharedsignal;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.basic.sql.TransactionManager;
import org.idp.server.core.security.SecurityEvent;
import org.idp.server.core.security.SecurityEventRepository;
import org.idp.server.core.security.event.SecurityEventSearchCriteria;
import org.idp.server.core.security.SecurityEvents;

public class SecurityEventDataSource implements SecurityEventRepository {

  JsonConverter converter = JsonConverter.createWithSnakeCaseStrategy();

  @Override
  public void register(SecurityEvent securityEvent) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate =
        """
                INSERT INTO public.events (id, type, description, tenant_id, tenant_name, client_id, client_name, user_id, user_name, detail)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb) ON CONFLICT DO NOTHING;
                """;
    List<Object> params = new ArrayList<>();
    params.add(securityEvent.identifier().value());
    params.add(securityEvent.type().value());
    params.add(securityEvent.description().value());
    params.add(securityEvent.tenant().idAsString());
    params.add(securityEvent.tenant().name());
    params.add(securityEvent.client().id());
    params.add(securityEvent.client().name());

    if (securityEvent.hasUser()) {
      params.add(securityEvent.user().id());
      params.add(securityEvent.user().name());
    } else {
      params.add(null);
      params.add(null);
    }
    params.add(converter.write(securityEvent.detail().toMap()));

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public SecurityEvents findBy(String eventServerId, String userId) {
    return null;
  }

  @Override
  public SecurityEvents search(String eventServerId, SecurityEventSearchCriteria criteria) {
    return null;
  }
}
