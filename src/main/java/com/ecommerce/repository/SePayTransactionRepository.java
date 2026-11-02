package com.ecommerce.repository;

import com.ecommerce.document.SePayTransaction;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SePayTransactionRepository extends MongoRepository<SePayTransaction, String> {

    boolean existsBySepayId(Long sepayId);

    Optional<SePayTransaction> findBySepayId(Long sepayId);
}
