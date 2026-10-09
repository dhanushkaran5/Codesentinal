package com.codesentinel.repository;

import com.codesentinel.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for Review entities and their findings.
 */
@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByRepositoryAndPrNumber(String repository, Integer prNumber);

    List<Review> findByStatus(String status);

    List<Review> findByRepository(String repository);

    List<Review> findAllByOrderByCreatedAtDesc();
}
