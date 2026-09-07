package com.example.cdcmaterializedview.service;

import com.example.cdcmaterializedview.entity.Order;
import com.example.cdcmaterializedview.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public List<Order> findAll() {
        return orderRepository.findAll();
    }

    public Optional<Order> findById(Long id) {
        return orderRepository.findById(id);
    }

    public Order create(Order order) {
        return orderRepository.save(order);
    }

    public Optional<Order> update(Long id, Order update) {
        return orderRepository.findById(id)
                .map(existing -> {
                    existing.setStatus(update.getStatus());
                    return orderRepository.save(existing);
                });
    }

    public boolean delete(Long id) {
        if (!orderRepository.existsById(id)) {
            return false;
        }
        orderRepository.deleteById(id);
        return true;
    }
}