/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.protocol7.quincy.http3;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.util.CharsetUtil;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.Test;

public class Http2StreamFrameToHttpObjectCodecTest {

  @Test
  public void testUpgradeEmptyFullResponse() throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
    assertTrue(
        ch.writeOutbound(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)));

    final Http2HeadersFrame headersFrame = ch.readOutbound();
    assertThat(headersFrame.headers().status().toString(), is("200"));
    assertTrue(headersFrame.isEndStream());

    assertThat(ch.readOutbound(), is(nullValue()));
    assertFalse(ch.finish());
  }

  @Test
  public void encode100ContinueAsHttp2HeadersFrameThatIsNotEndStream() throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
    assertTrue(
        ch.writeOutbound(
            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE)));

    final Http2HeadersFrame headersFrame = ch.readOutbound();
    assertThat(headersFrame.headers().status().toString(), is("100"));
    assertFalse(headersFrame.isEndStream());

    assertThat(ch.readOutbound(), is(nullValue()));
    assertFalse(ch.finish());
  }

  @Test(expected = EncoderException.class)
  public void encodeNonFullHttpResponse100ContinueIsRejected() throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
    try {
      ch.writeOutbound(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE));
    } finally {
      ch.finishAndReleaseAll();
    }
  }

  @Test
  public void testUpgradeNonEmptyFullResponse() throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
    final ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
    assertTrue(
        ch.writeOutbound(
            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, hello)));

    final Http2HeadersFrame headersFrame = ch.readOutbound();
    assertThat(headersFrame.headers().status().toString(), is("200"));
    assertFalse(headersFrame.isEndStream());

    final Http2DataFrame dataFrame = ch.readOutbound();
    try {
      assertThat(dataFrame.content().toString(CharsetUtil.UTF_8), is("hello world"));
      assertTrue(dataFrame.isEndStream());
    } finally {
      dataFrame.release();
    }

    assertThat(ch.readOutbound(), is(nullValue()));
    assertFalse(ch.finish());
  }

  @Test
  public void testUpgradeEmptyFullResponseWithTrailers() throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
    final FullHttpResponse response =
        new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
    final HttpHeaders trailers = response.trailingHeaders();
    trailers.set("key", "value");
    assertTrue(ch.writeOutbound(response));

    final Http2HeadersFrame headersFrame = ch.readOutbound();
    assertThat(headersFrame.headers().status().toString(), is("200"));
    assertFalse(headersFrame.isEndStream());

    final Http2HeadersFrame trailersFrame = ch.readOutbound();
    assertThat(trailersFrame.headers().get("key").toString(), is("value"));
    assertTrue(trailersFrame.isEndStream());

    assertThat(ch.readOutbound(), is(nullValue()));
    assertFalse(ch.finish());
  }

  @Test
  public void testUpgradeNonEmptyFullResponseWithTrailers() throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
    final ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
    final FullHttpResponse response =
        new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, hello);
    final HttpHeaders trailers = response.trailingHeaders();
    trailers.set("key", "value");
    assertTrue(ch.writeOutbound(response));

    final Http2HeadersFrame headersFrame = ch.readOutbound();
    assertThat(headersFrame.headers().status().toString(), is("200"));
    assertFalse(headersFrame.isEndStream());

    final Http2DataFrame dataFrame = ch.readOutbound();
    try {
      assertThat(dataFrame.content().toString(CharsetUtil.UTF_8), is("hello world"));
      assertFalse(dataFrame.isEndStream());
    } finally {
      dataFrame.release();
    }

    final Http2HeadersFrame trailersFrame = ch.readOutbound();
    assertThat(trailersFrame.headers().get("key").toString(), is("value"));
    assertTrue(trailersFrame.isEndStream());

    assertThat(ch.readOutbound(), is(nullValue()));
    assertFalse(ch.finish());
  }

  @Test
  public void testUpgradeHeaders() throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
    final HttpResponse response =
        new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
    assertTrue(ch.writeOutbound(response));

    final Http2HeadersFrame headersFrame = ch.readOutbound();
    assertThat(headersFrame.headers().status().toString(), is("200"));
    assertFalse(headersFrame.isEndStream());

    assertThat(ch.readOutbound(), is(nullValue()));
    assertFalse(ch.finish());
  }

  @Test
  public void testUpgradeChunk() throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
    final ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
    final HttpContent content = new DefaultHttpContent(hello);
    assertTrue(ch.writeOutbound(content));

    final Http2DataFrame dataFrame = ch.readOutbound();
    try {
      assertThat(dataFrame.content().toString(CharsetUtil.UTF_8), is("hello world"));
      assertFalse(dataFrame.isEndStream());
    } finally {
      dataFrame.release();
    }

    assertThat(ch.readOutbound(), is(nullValue()));
    assertFalse(ch.finish());
  }

  @Test
  public void testUpgradeEmptyEnd() throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
    final LastHttpContent end = LastHttpContent.EMPTY_LAST_CONTENT;
    assertTrue(ch.writeOutbound(end));

    final Http2DataFrame emptyFrame = ch.readOutbound();
    try {
      assertThat(emptyFrame.content().readableBytes(), is(0));
      assertTrue(emptyFrame.isEndStream());
    } finally {
      emptyFrame.release();
    }

    assertThat(ch.readOutbound(), is(nullValue()));
    assertFalse(ch.finish());
  }

  @Test
  public void testUpgradeDataEnd() throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
    final ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
    final LastHttpContent end = new DefaultLastHttpContent(hello, true);
    assertTrue(ch.writeOutbound(end));

    final Http2DataFrame dataFrame = ch.readOutbound();
    try {
      assertThat(dataFrame.content().toString(CharsetUtil.UTF_8), is("hello world"));
      assertTrue(dataFrame.isEndStream());
    } finally {
      dataFrame.release();
    }

    assertThat(ch.readOutbound(), is(nullValue()));
    assertFalse(ch.finish());
  }

  @Test
  public void testUpgradeTrailers() throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
    final LastHttpContent trailers = new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER, true);
    final HttpHeaders headers = trailers.trailingHeaders();
    headers.set("key", "value");
    assertTrue(ch.writeOutbound(trailers));

    final Http2HeadersFrame headerFrame = ch.readOutbound();
    assertThat(headerFrame.headers().get("key").toString(), is("value"));
    assertTrue(headerFrame.isEndStream());

    assertThat(ch.readOutbound(), is(nullValue()));
    assertFalse(ch.finish());
  }

  @Test
  public void testUpgradeDataEndWithTrailers() throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
    final ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
    final LastHttpContent trailers = new DefaultLastHttpContent(hello, true);
    final HttpHeaders headers = trailers.trailingHeaders();
    headers.set("key", "value");
    assertTrue(ch.writeOutbound(trailers));

    final Http2DataFrame dataFrame = ch.readOutbound();
    try {
      assertThat(dataFrame.content().toString(CharsetUtil.UTF_8), is("hello world"));
      assertFalse(dataFrame.isEndStream());
    } finally {
      dataFrame.release();
    }

    final Http2HeadersFrame headerFrame = ch.readOutbound();
    assertThat(headerFrame.headers().get("key").toString(), is("value"));
    assertTrue(headerFrame.isEndStream());

    assertThat(ch.readOutbound(), is(nullValue()));
    assertFalse(ch.finish());
  }

  @Test
  public void testDowngradeHeaders() throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
    final Http2Headers headers = new DefaultHttp2Headers();
    headers.path("/");
    headers.method("GET");

    assertTrue(ch.writeInbound(new DefaultHttp2HeadersFrame(headers)));

    final HttpRequest request = ch.readInbound();
    assertThat(request.uri(), is("/"));
    assertThat(request.method(), is(HttpMethod.GET));
    assertThat(request.protocolVersion(), is(HttpVersion.HTTP_1_1));
    assertFalse(request instanceof FullHttpRequest);
    assertTrue(HttpUtil.isTransferEncodingChunked(request));

    assertThat(ch.readInbound(), is(nullValue()));
    assertFalse(ch.finish());
  }

  @Test
  public void testDowngradeHeadersWithContentLength() throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
    final Http2Headers headers = new DefaultHttp2Headers();
    headers.path("/");
    headers.method("GET");
    headers.setInt("content-length", 0);

    assertTrue(ch.writeInbound(new DefaultHttp2HeadersFrame(headers)));

    final HttpRequest request = ch.readInbound();
    assertThat(request.uri(), is("/"));
    assertThat(request.method(), is(HttpMethod.GET));
    assertThat(request.protocolVersion(), is(HttpVersion.HTTP_1_1));
    assertFalse(request instanceof FullHttpRequest);
    assertFalse(HttpUtil.isTransferEncodingChunked(request));

    assertThat(ch.readInbound(), is(nullValue()));
    assertFalse(ch.finish());
  }

  @Test
  public void testDowngradeFullHeaders() throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
    final Http2Headers headers = new DefaultHttp2Headers();
    headers.path("/");
    headers.method("GET");

    assertTrue(ch.writeInbound(new DefaultHttp2HeadersFrame(headers, true)));

    final FullHttpRequest request = ch.readInbound();
    try {
      assertThat(request.uri(), is("/"));
      assertThat(request.method(), is(HttpMethod.GET));
      assertThat(request.protocolVersion(), is(HttpVersion.HTTP_1_1));
      assertThat(request.content().readableBytes(), is(0));
      assertTrue(request.trailingHeaders().isEmpty());
      assertFalse(HttpUtil.isTransferEncodingChunked(request));
    } finally {
      request.release();
    }

    assertThat(ch.readInbound(), is(nullValue()));
    assertFalse(ch.finish());
  }

  @Test
  public void testDowngradeTrailers() throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
    final Http2Headers headers = new DefaultHttp2Headers();
    headers.set("key", "value");
    assertTrue(ch.writeInbound(new DefaultHttp2HeadersFrame(headers, true)));

    final LastHttpContent trailers = ch.readInbound();
    try {
      assertThat(trailers.content().readableBytes(), is(0));
      assertThat(trailers.trailingHeaders().get("key"), is("value"));
      assertFalse(trailers instanceof FullHttpRequest);
    } finally {
      trailers.release();
    }

    assertThat(ch.readInbound(), is(nullValue()));
    assertFalse(ch.finish());
  }

  @Test
  public void testDowngradeData() throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
    final ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
    assertTrue(ch.writeInbound(new DefaultHttp2DataFrame(hello)));

    final HttpContent content = ch.readInbound();
    try {
      assertThat(content.content().toString(CharsetUtil.UTF_8), is("hello world"));
      assertFalse(content instanceof LastHttpContent);
    } finally {
      content.release();
    }

    assertThat(ch.readInbound(), is(nullValue()));
    assertFalse(ch.finish());
  }

  @Test
  public void testDowngradeEndData() throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
    final ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
    assertTrue(ch.writeInbound(new DefaultHttp2DataFrame(hello, true)));

    final LastHttpContent content = ch.readInbound();
    try {
      assertThat(content.content().toString(CharsetUtil.UTF_8), is("hello world"));
      assertTrue(content.trailingHeaders().isEmpty());
    } finally {
      content.release();
    }

    assertThat(ch.readInbound(), is(nullValue()));
    assertFalse(ch.finish());
  }

  @Test
  public void testPassThroughOther() throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(true));
    final Http2ResetFrame reset = new DefaultHttp2ResetFrame(0);
    final Http2GoAwayFrame goaway = new DefaultHttp2GoAwayFrame(0);
    assertTrue(ch.writeInbound(reset));
    assertTrue(ch.writeInbound(goaway.retain()));

    assertEquals(reset, ch.readInbound());

    final Http2GoAwayFrame frame = ch.readInbound();
    try {
      assertEquals(goaway, frame);
      assertThat(ch.readInbound(), is(nullValue()));
      assertFalse(ch.finish());
    } finally {
      goaway.release();
      frame.release();
    }
  }

  // client-specific tests
  @Test
  public void testEncodeEmptyFullRequest() throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
    assertTrue(
        ch.writeOutbound(
            new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/hello/world")));

    final Http2HeadersFrame headersFrame = ch.readOutbound();
    final Http2Headers headers = headersFrame.headers();

    assertThat(headers.scheme().toString(), is("http"));
    assertThat(headers.method().toString(), is("GET"));
    assertThat(headers.path().toString(), is("/hello/world"));
    assertTrue(headersFrame.isEndStream());

    assertThat(ch.readOutbound(), is(nullValue()));
    assertFalse(ch.finish());
  }

  @Test
  public void testEncodeHttpsSchemeWhenSslHandlerExists() throws Exception {
    final Queue<Http2StreamFrame> frames = new ConcurrentLinkedQueue<Http2StreamFrame>();

    final SslContext ctx = SslContextBuilder.forClient().sslProvider(SslProvider.JDK).build();
    final EmbeddedChannel ch =
        new EmbeddedChannel(
            ctx.newHandler(ByteBufAllocator.DEFAULT),
            new ChannelOutboundHandlerAdapter() {
              @Override
              public void write(
                  final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise)
                  throws Exception {
                if (msg instanceof Http2StreamFrame) {
                  frames.add((Http2StreamFrame) msg);
                  ctx.write(Unpooled.EMPTY_BUFFER, promise);
                } else {
                  ctx.write(msg, promise);
                }
              }
            },
            new Http2StreamFrameToHttpObjectCodec(false));

    try {
      final FullHttpRequest req =
          new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/hello/world");
      assertTrue(ch.writeOutbound(req));

      ch.finishAndReleaseAll();

      final Http2HeadersFrame headersFrame = (Http2HeadersFrame) frames.poll();
      final Http2Headers headers = headersFrame.headers();

      assertThat(headers.scheme().toString(), is("https"));
      assertThat(headers.method().toString(), is("GET"));
      assertThat(headers.path().toString(), is("/hello/world"));
      assertTrue(headersFrame.isEndStream());
      assertNull(frames.poll());
    } finally {
      ch.finishAndReleaseAll();
    }
  }

  @Test
  public void testEncodeNonEmptyFullRequest() throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
    final ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
    assertTrue(
        ch.writeOutbound(
            new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.PUT, "/hello/world", hello)));

    final Http2HeadersFrame headersFrame = ch.readOutbound();
    final Http2Headers headers = headersFrame.headers();

    assertThat(headers.scheme().toString(), is("http"));
    assertThat(headers.method().toString(), is("PUT"));
    assertThat(headers.path().toString(), is("/hello/world"));
    assertFalse(headersFrame.isEndStream());

    final Http2DataFrame dataFrame = ch.readOutbound();
    try {
      assertThat(dataFrame.content().toString(CharsetUtil.UTF_8), is("hello world"));
      assertTrue(dataFrame.isEndStream());
    } finally {
      dataFrame.release();
    }

    assertThat(ch.readOutbound(), is(nullValue()));
    assertFalse(ch.finish());
  }

  @Test
  public void testEncodeEmptyFullRequestWithTrailers() throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
    final FullHttpRequest request =
        new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, "/hello/world");

    final HttpHeaders trailers = request.trailingHeaders();
    trailers.set("key", "value");
    assertTrue(ch.writeOutbound(request));

    final Http2HeadersFrame headersFrame = ch.readOutbound();
    final Http2Headers headers = headersFrame.headers();

    assertThat(headers.scheme().toString(), is("http"));
    assertThat(headers.method().toString(), is("PUT"));
    assertThat(headers.path().toString(), is("/hello/world"));
    assertFalse(headersFrame.isEndStream());

    final Http2HeadersFrame trailersFrame = ch.readOutbound();
    assertThat(trailersFrame.headers().get("key").toString(), is("value"));
    assertTrue(trailersFrame.isEndStream());

    assertThat(ch.readOutbound(), is(nullValue()));
    assertFalse(ch.finish());
  }

  @Test
  public void testEncodeNonEmptyFullRequestWithTrailers() throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
    final ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
    final FullHttpRequest request =
        new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, "/hello/world", hello);

    final HttpHeaders trailers = request.trailingHeaders();
    trailers.set("key", "value");
    assertTrue(ch.writeOutbound(request));

    final Http2HeadersFrame headersFrame = ch.readOutbound();
    final Http2Headers headers = headersFrame.headers();

    assertThat(headers.scheme().toString(), is("http"));
    assertThat(headers.method().toString(), is("PUT"));
    assertThat(headers.path().toString(), is("/hello/world"));
    assertFalse(headersFrame.isEndStream());

    final Http2DataFrame dataFrame = ch.readOutbound();
    try {
      assertThat(dataFrame.content().toString(CharsetUtil.UTF_8), is("hello world"));
      assertFalse(dataFrame.isEndStream());
    } finally {
      dataFrame.release();
    }

    final Http2HeadersFrame trailersFrame = ch.readOutbound();
    assertThat(trailersFrame.headers().get("key").toString(), is("value"));
    assertTrue(trailersFrame.isEndStream());

    assertThat(ch.readOutbound(), is(nullValue()));
    assertFalse(ch.finish());
  }

  @Test
  public void testEncodeRequestHeaders() throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
    final HttpRequest request =
        new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/hello/world");
    assertTrue(ch.writeOutbound(request));

    final Http2HeadersFrame headersFrame = ch.readOutbound();
    final Http2Headers headers = headersFrame.headers();

    assertThat(headers.scheme().toString(), is("http"));
    assertThat(headers.method().toString(), is("GET"));
    assertThat(headers.path().toString(), is("/hello/world"));
    assertFalse(headersFrame.isEndStream());

    assertThat(ch.readOutbound(), is(nullValue()));
    assertFalse(ch.finish());
  }

  @Test
  public void testEncodeChunkAsClient() throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
    final ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
    final HttpContent content = new DefaultHttpContent(hello);
    assertTrue(ch.writeOutbound(content));

    final Http2DataFrame dataFrame = ch.readOutbound();
    try {
      assertThat(dataFrame.content().toString(CharsetUtil.UTF_8), is("hello world"));
      assertFalse(dataFrame.isEndStream());
    } finally {
      dataFrame.release();
    }

    assertThat(ch.readOutbound(), is(nullValue()));
    assertFalse(ch.finish());
  }

  @Test
  public void testEncodeEmptyEndAsClient() throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
    final LastHttpContent end = LastHttpContent.EMPTY_LAST_CONTENT;
    assertTrue(ch.writeOutbound(end));

    final Http2DataFrame emptyFrame = ch.readOutbound();
    try {
      assertThat(emptyFrame.content().readableBytes(), is(0));
      assertTrue(emptyFrame.isEndStream());
    } finally {
      emptyFrame.release();
    }

    assertThat(ch.readOutbound(), is(nullValue()));
    assertFalse(ch.finish());
  }

  @Test
  public void testEncodeDataEndAsClient() throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
    final ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
    final LastHttpContent end = new DefaultLastHttpContent(hello, true);
    assertTrue(ch.writeOutbound(end));

    final Http2DataFrame dataFrame = ch.readOutbound();
    try {
      assertThat(dataFrame.content().toString(CharsetUtil.UTF_8), is("hello world"));
      assertTrue(dataFrame.isEndStream());
    } finally {
      dataFrame.release();
    }

    assertThat(ch.readOutbound(), is(nullValue()));
    assertFalse(ch.finish());
  }

  @Test
  public void testEncodeTrailersAsClient() throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
    final LastHttpContent trailers = new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER, true);
    final HttpHeaders headers = trailers.trailingHeaders();
    headers.set("key", "value");
    assertTrue(ch.writeOutbound(trailers));

    final Http2HeadersFrame headerFrame = ch.readOutbound();
    assertThat(headerFrame.headers().get("key").toString(), is("value"));
    assertTrue(headerFrame.isEndStream());

    assertThat(ch.readOutbound(), is(nullValue()));
    assertFalse(ch.finish());
  }

  @Test
  public void testEncodeDataEndWithTrailersAsClient() throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
    final ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
    final LastHttpContent trailers = new DefaultLastHttpContent(hello, true);
    final HttpHeaders headers = trailers.trailingHeaders();
    headers.set("key", "value");
    assertTrue(ch.writeOutbound(trailers));

    final Http2DataFrame dataFrame = ch.readOutbound();
    try {
      assertThat(dataFrame.content().toString(CharsetUtil.UTF_8), is("hello world"));
      assertFalse(dataFrame.isEndStream());
    } finally {
      dataFrame.release();
    }

    final Http2HeadersFrame headerFrame = ch.readOutbound();
    assertThat(headerFrame.headers().get("key").toString(), is("value"));
    assertTrue(headerFrame.isEndStream());

    assertThat(ch.readOutbound(), is(nullValue()));
    assertFalse(ch.finish());
  }

  @Test
  public void decode100ContinueHttp2HeadersAsFullHttpResponse() throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
    final Http2Headers headers = new DefaultHttp2Headers();
    headers.scheme(HttpScheme.HTTP.name());
    headers.status(HttpResponseStatus.CONTINUE.codeAsText());

    assertTrue(ch.writeInbound(new DefaultHttp2HeadersFrame(headers, false)));

    final FullHttpResponse response = ch.readInbound();
    try {
      assertThat(response.status(), is(HttpResponseStatus.CONTINUE));
      assertThat(response.protocolVersion(), is(HttpVersion.HTTP_1_1));
    } finally {
      response.release();
    }

    assertThat(ch.readInbound(), is(nullValue()));
    assertFalse(ch.finish());
  }

  @Test
  public void testDecodeResponseHeaders() throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
    final Http2Headers headers = new DefaultHttp2Headers();
    headers.scheme(HttpScheme.HTTP.name());
    headers.status(HttpResponseStatus.OK.codeAsText());

    assertTrue(ch.writeInbound(new DefaultHttp2HeadersFrame(headers)));

    final HttpResponse response = ch.readInbound();
    assertThat(response.status(), is(HttpResponseStatus.OK));
    assertThat(response.protocolVersion(), is(HttpVersion.HTTP_1_1));
    assertFalse(response instanceof FullHttpResponse);
    assertTrue(HttpUtil.isTransferEncodingChunked(response));

    assertThat(ch.readInbound(), is(nullValue()));
    assertFalse(ch.finish());
  }

  @Test
  public void testDecodeResponseHeadersWithContentLength() throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
    final Http2Headers headers = new DefaultHttp2Headers();
    headers.scheme(HttpScheme.HTTP.name());
    headers.status(HttpResponseStatus.OK.codeAsText());
    headers.setInt("content-length", 0);

    assertTrue(ch.writeInbound(new DefaultHttp2HeadersFrame(headers)));

    final HttpResponse response = ch.readInbound();
    assertThat(response.status(), is(HttpResponseStatus.OK));
    assertThat(response.protocolVersion(), is(HttpVersion.HTTP_1_1));
    assertFalse(response instanceof FullHttpResponse);
    assertFalse(HttpUtil.isTransferEncodingChunked(response));

    assertThat(ch.readInbound(), is(nullValue()));
    assertFalse(ch.finish());
  }

  @Test
  public void testDecodeFullResponseHeaders() throws Exception {
    testDecodeFullResponseHeaders(false);
  }

  @Test
  public void testDecodeFullResponseHeadersWithStreamID() throws Exception {
    testDecodeFullResponseHeaders(true);
  }

  private void testDecodeFullResponseHeaders(final boolean withStreamId) throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
    final Http2Headers headers = new DefaultHttp2Headers();
    headers.scheme(HttpScheme.HTTP.name());
    headers.status(HttpResponseStatus.OK.codeAsText());

    final Http2HeadersFrame frame = new DefaultHttp2HeadersFrame(headers, true);
    if (withStreamId) {
      frame.stream(
          new Http2FrameStream() {
            @Override
            public int id() {
              return 1;
            }

            @Override
            public Http2Stream.State state() {
              return Http2Stream.State.OPEN;
            }
          });
    }

    assertTrue(ch.writeInbound(frame));

    final FullHttpResponse response = ch.readInbound();
    try {
      assertThat(response.status(), is(HttpResponseStatus.OK));
      assertThat(response.protocolVersion(), is(HttpVersion.HTTP_1_1));
      assertThat(response.content().readableBytes(), is(0));
      assertTrue(response.trailingHeaders().isEmpty());
      assertFalse(HttpUtil.isTransferEncodingChunked(response));
      if (withStreamId) {
        assertEquals(
            1,
            (int)
                response
                    .headers()
                    .getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text()));
      }
    } finally {
      response.release();
    }

    assertThat(ch.readInbound(), is(nullValue()));
    assertFalse(ch.finish());
  }

  @Test
  public void testDecodeResponseTrailersAsClient() throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
    final Http2Headers headers = new DefaultHttp2Headers();
    headers.set("key", "value");
    assertTrue(ch.writeInbound(new DefaultHttp2HeadersFrame(headers, true)));

    final LastHttpContent trailers = ch.readInbound();
    try {
      assertThat(trailers.content().readableBytes(), is(0));
      assertThat(trailers.trailingHeaders().get("key"), is("value"));
      assertFalse(trailers instanceof FullHttpRequest);
    } finally {
      trailers.release();
    }

    assertThat(ch.readInbound(), is(nullValue()));
    assertFalse(ch.finish());
  }

  @Test
  public void testDecodeDataAsClient() throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
    final ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
    assertTrue(ch.writeInbound(new DefaultHttp2DataFrame(hello)));

    final HttpContent content = ch.readInbound();
    try {
      assertThat(content.content().toString(CharsetUtil.UTF_8), is("hello world"));
      assertFalse(content instanceof LastHttpContent);
    } finally {
      content.release();
    }

    assertThat(ch.readInbound(), is(nullValue()));
    assertFalse(ch.finish());
  }

  @Test
  public void testDecodeEndDataAsClient() throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
    final ByteBuf hello = Unpooled.copiedBuffer("hello world", CharsetUtil.UTF_8);
    assertTrue(ch.writeInbound(new DefaultHttp2DataFrame(hello, true)));

    final LastHttpContent content = ch.readInbound();
    try {
      assertThat(content.content().toString(CharsetUtil.UTF_8), is("hello world"));
      assertTrue(content.trailingHeaders().isEmpty());
    } finally {
      content.release();
    }

    assertThat(ch.readInbound(), is(nullValue()));
    assertFalse(ch.finish());
  }

  @Test
  public void testPassThroughOtherAsClient() throws Exception {
    final EmbeddedChannel ch = new EmbeddedChannel(new Http2StreamFrameToHttpObjectCodec(false));
    final Http2ResetFrame reset = new DefaultHttp2ResetFrame(0);
    final Http2GoAwayFrame goaway = new DefaultHttp2GoAwayFrame(0);
    assertTrue(ch.writeInbound(reset));
    assertTrue(ch.writeInbound(goaway.retain()));

    assertEquals(reset, ch.readInbound());

    final Http2GoAwayFrame frame = ch.readInbound();
    try {
      assertEquals(goaway, frame);
      assertThat(ch.readInbound(), is(nullValue()));
      assertFalse(ch.finish());
    } finally {
      goaway.release();
      frame.release();
    }
  }

  @Test
  public void testIsSharableBetweenChannels() throws Exception {
    final Queue<Http2StreamFrame> frames = new ConcurrentLinkedQueue<Http2StreamFrame>();
    final ChannelHandler sharedHandler = new Http2StreamFrameToHttpObjectCodec(false);

    final SslContext ctx = SslContextBuilder.forClient().sslProvider(SslProvider.JDK).build();
    final EmbeddedChannel tlsCh =
        new EmbeddedChannel(
            ctx.newHandler(ByteBufAllocator.DEFAULT),
            new ChannelOutboundHandlerAdapter() {
              @Override
              public void write(
                  final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) {
                if (msg instanceof Http2StreamFrame) {
                  frames.add((Http2StreamFrame) msg);
                  promise.setSuccess();
                } else {
                  ctx.write(msg, promise);
                }
              }
            },
            sharedHandler);

    final EmbeddedChannel plaintextCh =
        new EmbeddedChannel(
            new ChannelOutboundHandlerAdapter() {
              @Override
              public void write(
                  final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) {
                if (msg instanceof Http2StreamFrame) {
                  frames.add((Http2StreamFrame) msg);
                  promise.setSuccess();
                } else {
                  ctx.write(msg, promise);
                }
              }
            },
            sharedHandler);

    FullHttpRequest req =
        new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/hello/world");
    assertTrue(tlsCh.writeOutbound(req));
    assertTrue(tlsCh.finishAndReleaseAll());

    Http2HeadersFrame headersFrame = (Http2HeadersFrame) frames.poll();
    Http2Headers headers = headersFrame.headers();

    assertThat(headers.scheme().toString(), is("https"));
    assertThat(headers.method().toString(), is("GET"));
    assertThat(headers.path().toString(), is("/hello/world"));
    assertTrue(headersFrame.isEndStream());
    assertNull(frames.poll());

    // Run the plaintext channel
    req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/hello/world");
    assertFalse(plaintextCh.writeOutbound(req));
    assertFalse(plaintextCh.finishAndReleaseAll());

    headersFrame = (Http2HeadersFrame) frames.poll();
    headers = headersFrame.headers();

    assertThat(headers.scheme().toString(), is("http"));
    assertThat(headers.method().toString(), is("GET"));
    assertThat(headers.path().toString(), is("/hello/world"));
    assertTrue(headersFrame.isEndStream());
    assertNull(frames.poll());
  }
}
