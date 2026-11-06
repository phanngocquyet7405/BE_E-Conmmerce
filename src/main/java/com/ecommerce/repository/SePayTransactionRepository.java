package com.ecommerce.repository;

import com.ecommerce.document.SePayTransaction;
import com.ecommerce.document.SePayTransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SePayTransactionRepository extends MongoRepository<SePayTransaction, String> {

    boolean existsBySepayId(Long sepayId);

    Optional<SePayTransaction> findBySepayId(Long sepayId);

    Page<SePayTransaction> findByStatusOrderByCreatedAtDesc(SePayTransactionStatus status, Pageable pageable);

    Page<SePayTransaction> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<SePayTransaction> findByOrderCodeOrderByCreatedAtDesc(String orderCode);
}
