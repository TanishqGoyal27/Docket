package com.tiet.docket.repository;

import com.tiet.docket.model.Service;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceRepository extends JpaRepository<Service, Long> {

    Optional<Service> findByName(String name);

    List<Service> findByActiveTrue();
}