package com.ecommerce.repository;

import com.ecommerce.document.Brand;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BrandRepository extends MongoRepository<Brand, String> {
    Optional<Brand> findBySlug(String slug);
    boolean existsByName(String name);
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, String id);
    boolean existsBySlug(String slug);
    boolean existsBySlugAndIdNot(String slug, String id);
}
