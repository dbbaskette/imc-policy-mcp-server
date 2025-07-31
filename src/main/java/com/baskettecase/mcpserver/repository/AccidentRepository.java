package com.baskettecase.mcpserver.repository;

import com.baskettecase.mcpserver.model.Accident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccidentRepository extends JpaRepository<Accident, Integer> {
    
    @Query("SELECT a FROM Accident a WHERE a.driverId = :customerId")
    List<Accident> findAccidentsByCustomerId(@Param("customerId") Integer customerId);
}