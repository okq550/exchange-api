package org.okq550.exchange_api.order;

import jakarta.transaction.Transactional;
import org.okq550.exchange_api.account.AccountService;
import org.okq550.exchange_api.domain.Order;
import org.okq550.exchange_api.domain.Side;
import org.okq550.exchange_api.outbox.OutboxMessage;
import org.okq550.exchange_api.outbox.OutboxMessageRepository;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.BlockingQueue;

@Service
public class ExchangeService {
    private final AccountService accountService;
    private final OrderRecordRepository orderRecordRepository;
    private final OutboxMessageRepository outboxRepository;     // injected now, not created here
    private final KafkaTemplate<String, String> kafkaTemplate;

    public ExchangeService(AccountService accountService, OrderRecordRepository orderRecordRepository, OutboxMessageRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate) {
        this.accountService = accountService;
        this.orderRecordRepository = orderRecordRepository;
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        // no exchange.start() here: ExchangeConfig already started it
    }

    @Transactional
    public OrderAccepted place(long accountId, String symbol, Side side, int quantity, long priceInCents) {
        // 1. Reserve cash or shares
        boolean reserved;
        if (side == Side.BUY) {
            reserved = accountService.tryReserveCash(accountId, (long) quantity * priceInCents);
        } else {
            reserved = accountService.tryReserveShares(accountId, quantity);
        }
        if (!reserved) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient funds or shares");
        }

        // 2. save the order; the database assigns the id
        OrderRecord saved = orderRecordRepository.save(new OrderRecord(accountId, symbol, side, quantity, priceInCents));
        Order order = new Order(saved.getId(), accountId, symbol, side, quantity, priceInCents);
        // 3. queue the Kafka message in the SAME transaction (no kafkaTemplate here anymore)
        outboxRepository.save(new OutboxMessage("orders.in", symbol, "PLACE|" + order.toWire()));
        return new OrderAccepted(order.id());
    }

    @Transactional
    public CancelAccepted cancel(long orderId) {
        OrderRecord record = orderRecordRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        outboxRepository.save(new OutboxMessage("orders.in", record.getSymbol(), "CANCEL|" + orderId));
        return new CancelAccepted(orderId);
    }
}
