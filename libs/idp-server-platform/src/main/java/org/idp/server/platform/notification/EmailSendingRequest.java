/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.idp.server.platform.notification;

public class EmailSendingRequest {
  String from;
  String to;
  String subject;
  String body;

  public EmailSendingRequest(String from, String to, String subject, String body) {
    this.from = from;
    this.to = to;
    this.subject = subject;
    this.body = body;
  }

  public String from() {
    return from;
  }

  public String to() {
    return to;
  }

  public String subject() {
    return subject;
  }

  public String body() {
    return body;
  }
}
