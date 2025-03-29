package com.bank.vwap;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

public class VWAPWebSocketHandler extends TextWebSocketHandler {
    private final VWAPCalculator calculator = new VWAPCalculator();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            CurrencyPriceData priceData = mapper.readValue(message.getPayload(), CurrencyPriceData.class);
            calculator.processPriceUpdate(
                priceData.getCurrencyPair(),
                priceData.getPrice(),
                priceData.getVolume()
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
} 