package com.protocol7.nettyquick.protocol;

public class LongPacket implements Packet {

  private final LongPacketType packetType;
  private final ConnectionId connectionId;
  private final Version version;
  private final PacketNumber packetNumber;
  private final Payload payload;

  public LongPacket(final LongPacketType packetType, final ConnectionId connectionId, final Version version, final PacketNumber packetNumber, final Payload payload) {
    this.packetType = packetType;
    this.connectionId = connectionId;
    this.version = version;
    this.packetNumber = packetNumber;
    this.payload = payload;
  }

  public LongPacketType getPacketType() {
    return packetType;
  }

  public ConnectionId getConnectionId() {
    return connectionId;
  }

  public Version getVersion() {
    return version;
  }

  public PacketNumber getPacketNumber() {
    return packetNumber;
  }

  public Payload getPayload() {
    return payload;
  }
}