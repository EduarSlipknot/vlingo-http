// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.http.resource.sse;

import static io.vlingo.http.Response.Status.Ok;

import java.util.Arrays;
import java.util.Collection;

import io.vlingo.http.Response;
import io.vlingo.http.ResponseHeader;
import io.vlingo.http.Header.Headers;
import io.vlingo.http.resource.Configuration;
import io.vlingo.wire.channel.RequestResponseContext;
import io.vlingo.wire.message.BasicConsumerByteBuffer;
import io.vlingo.wire.message.ConsumerByteBuffer;

public class SseClient {
  private static final ResponseHeader Connection;
  private static final ResponseHeader ContentType;
  private static final ResponseHeader TransferEncoding;
  private static final Headers<ResponseHeader> headers;

  static {
    Connection = ResponseHeader.of(ResponseHeader.Connection, "keep-alive");
    ContentType = ResponseHeader.of(ResponseHeader.ContentType, "text/event-stream");
    TransferEncoding = ResponseHeader.of(ResponseHeader.TransferEncoding, "chunked");

    headers = Headers.empty();
    headers.and(Connection).and(ContentType).and(TransferEncoding);
  }

  private final StringBuilder builder;
  private final RequestResponseContext<?> context;
  private final int maxMessageSize;
  private final ConsumerByteBuffer buffer;

  public SseClient(final RequestResponseContext<?> context) {
    this.context = context;
    this.builder = new StringBuilder(256);
    this.maxMessageSize = Configuration.instance.sizing().maxMessageSize;
    this.buffer = BasicConsumerByteBuffer.allocate(1, maxMessageSize);

    setUpConnection();
  }

  public void close() {
    context.abandon();
  }

  public String id() {
    return context.id();
  }

  public void send(final SseEvent event) {
    final String entity = event.sendable();
    final Headers<ResponseHeader> withContentLength = headers.copy().and(ResponseHeader.contentLength(entity));
    final Response response = Response.of(Ok, withContentLength, entity);
    context.respondWith(response.into(buffer));
    buffer.flip().clear();
  }

  public void send(final SseEvent... events) {
    send(Arrays.asList(events));
  }

  public void send(final Collection<SseEvent> events) {
    final String entity = flatten(events);
    send(entity, headers.copy().and(ResponseHeader.contentLength(entity)));
  }

  public void send(final Collection<SseEvent> events, final String correlationId) {
    final String entity = flatten(events);
    send(entity, headers.copy().and(ResponseHeader.contentLength(entity)).and(ResponseHeader.correlationId(correlationId)));
  }

  private void send(final String entity, Headers<ResponseHeader> headers) {
    final ConsumerByteBuffer buffer = BasicConsumerByteBuffer.allocate(1, maxMessageSize);
    final Response response = Response.of(Ok, headers, entity);
    context.respondWith(response.into(buffer));
    buffer.flip().clear();
  }

  private String flatten(final Collection<SseEvent> events) {
    builder.setLength(0);

    for (final SseEvent event : events) {
      builder.append(event.sendable());
    }

    return builder.toString();
  }

  private void setUpConnection() {
    final Response response = Response.of(Ok, headers, "xxxxxxxxxx");
    context.respondWith(response.into(buffer));
    buffer.flip().clear();
  }
}
