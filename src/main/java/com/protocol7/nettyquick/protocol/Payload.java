package com.protocol7.nettyquick.protocol;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;
import com.protocol7.nettyquick.protocol.frames.Frame;
import io.netty.buffer.ByteBuf;

public class Payload {

  public static Payload EMPTY = new Payload(Collections.emptyList());

  public static Payload parse(ByteBuf bb) {
    List<Frame> frames = Lists.newArrayList();
    while (bb.isReadable()) {
      frames.add(Frame.parse(bb));
    }
    return new Payload(frames);
  }

  private final List<Frame> frames;

  public Payload(final List<Frame> frames) {
    this.frames = frames;
  }

  public Payload(final Frame... frames) {
    this.frames = Arrays.asList(frames);
  }

  public List<Frame> getFrames() {
    return frames;
  }

  public void write(ByteBuf bb) {
    for (Frame frame : frames) {
      frame.write(bb);
    }
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final Payload payload = (Payload) o;

    return frames != null ? frames.equals(payload.frames) : payload.frames == null;

  }

  @Override
  public int hashCode() {
    return frames != null ? frames.hashCode() : 0;
  }
}