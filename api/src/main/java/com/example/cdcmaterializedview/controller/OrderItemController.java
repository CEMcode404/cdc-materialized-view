package com.example.cdcmaterializedview.controller;

import com.example.cdcmaterializedview.entity.OrderItem;
import com.example.cdcmaterializedview.service.OrderItemService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/order-items")
public class OrderItemController {

    private final OrderItemService orderItemService;

    public OrderItemController(OrderItemService orderItemService) {
        this.orderItemService = orderItemService;
    }

    @GetMapping
    public List<OrderItem> list() {
        return orderItemService.findAll();
    }

    @PostMapping
    public OrderItem create(@RequestBody OrderItem item) {
        return orderItemService.create(item);
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrderItem> update(@PathVariable Long id, @RequestBody OrderItem update) {
        return orderItemService.update(id, update)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return orderItemService.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}