package org.idp.server.ciba.clientnotification;

public record ClientNotificationRequest(String endpoint, String body, String token) {}
