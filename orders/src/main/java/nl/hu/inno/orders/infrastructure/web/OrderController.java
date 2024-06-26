package nl.hu.inno.orders.infrastructure.web;

import nl.hu.inno.orders.core.application.OrderCommandHandler;
import nl.hu.inno.orders.core.application.OrderQueryHandler;
import nl.hu.inno.orders.core.application.command.PlaceOrder;
import nl.hu.inno.orders.core.application.query.GetOrderById;
import nl.hu.inno.orders.core.application.query.GetOrders;
import nl.hu.inno.orders.core.application.query.IsDelivered;
import nl.hu.inno.orders.infrastructure.dto.OrderDTO;
import nl.hu.inno.orders.infrastructure.web.request.PlaceOrderRequest;
import nl.hu.inno.common.security.User;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private final OrderCommandHandler commandHandler;
    private final OrderQueryHandler queryHandler;

    public OrderController(OrderCommandHandler commandHandler, OrderQueryHandler queryHandler) {
        this.commandHandler = commandHandler;
        this.queryHandler = queryHandler;
    }

    @GetMapping
    public List<OrderDTO> getOrders(User user) {
        return this.queryHandler.handle(new GetOrders(user));
    }

    @GetMapping("/{id}")
    public OrderDTO getOrderById(User user, @PathVariable UUID id) {
        return this.queryHandler.handle(new GetOrderById(user, id));
    }

    @GetMapping("/{id}/is-delivered")
    public boolean isDelivered(@PathVariable("id") UUID id) {
        return this.queryHandler.handle(new IsDelivered(id));
    }

    @Transactional
    @PostMapping
    public OrderDTO placeOrder(User user, @RequestBody PlaceOrderRequest body) {
        return this.commandHandler.handle(new PlaceOrder(user, body.dishes));
    }
}
