package com.protocol7.nettyquic.streams;

public enum StreamType {
  Receiving,
  Sending,
  Bidirectional;

  public boolean canSend() {
    return this == Sending || this == Bidirectional;
  }

  public boolean canReceive() {
    return this == Receiving || this == Bidirectional;
  }
}
