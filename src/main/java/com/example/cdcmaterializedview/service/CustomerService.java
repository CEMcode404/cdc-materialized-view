package com.example.cdcmaterializedview.service;

import com.example.cdcmaterializedview.entity.Customer;
import com.example.cdcmaterializedview.repository.CustomerRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public List<Customer> findAll() {
        return customerRepository.findAll();
    }

    public Optional<Customer> findById(Long id) {
        return customerRepository.findById(id);
    }

    public Customer create(Customer customer) {
        return customerRepository.save(customer);
    }

    public Optional<Customer> update(Long id, Customer update) {
        return customerRepository.findById(id)
                .map(existing -> {
                    existing.setName(update.getName());
                    existing.setEmail(update.getEmail());
                    return customerRepository.save(existing);
                });
    }

    public boolean delete(Long id) {
        if (!customerRepository.existsById(id)) {
            return false;
        }
        customerRepository.deleteById(id);
        return true;
    }
}