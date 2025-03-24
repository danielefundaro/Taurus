package com.fundaro.zodiac.taurus.rabbitmq;

public interface Sender {
    void send(byte[] message);
}
