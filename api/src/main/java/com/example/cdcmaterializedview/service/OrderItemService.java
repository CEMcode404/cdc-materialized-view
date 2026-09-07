package com.example.cdcmaterializedview.service;

import com.example.cdcmaterializedview.entity.OrderItem;
import com.example.cdcmaterializedview.repository.OrderItemRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class OrderItemService {

    private final OrderItemRepository orderItemRepository;

    public OrderItemService(OrderItemRepository orderItemRepository) {
        this.orderItemRepository = orderItemRepository;
    }

    public List<OrderItem> findAll() {
        return orderItemRepository.findAll();
    }

    public OrderItem create(OrderItem item) {
        return orderItemRepository.save(item);
    }

    public Optional<OrderItem> update(Long id, OrderItem update) {
        return orderItemRepository.findById(id)
                .map(existing -> {
                    existing.setProductName(update.getProductName());
                    existing.setQuantity(update.getQuantity());
                    existing.setUnitPrice(update.getUnitPrice());
                    return orderItemRepository.save(existing);
                });
    }

    public boolean delete(Long id) {
        if (!orderItemRepository.existsById(id)) {
            return false;
        }
        orderItemRepository.deleteById(id);
        return true;
    }
}