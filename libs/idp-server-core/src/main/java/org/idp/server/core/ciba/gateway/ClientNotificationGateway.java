package org.idp.server.core.ciba.gateway;

import org.idp.server.core.ciba.clientnotification.ClientNotificationRequest;

public interface ClientNotificationGateway {

  void notify(ClientNotificationRequest clientNotificationRequest);
}
