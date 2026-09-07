package com.example.cdcmaterializedview.repository;

import com.example.cdcmaterializedview.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
}