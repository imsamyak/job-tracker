package org.example.respository;

import org.example.domain.entity.ExtractionComparison;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExtractionComparisonRepository extends JpaRepository<ExtractionComparison, UUID> {

    Optional<ExtractionComparison> findFirstByRunSourceOrderByCreatedAtDesc(String runSource);

    List<ExtractionComparison> findByRunIdAndFieldNameAndMatchedFalse(String runId, String fieldName);

    /** Rows of {fieldName, count, avgScore, matchedCount}. */
    @Query("select c.fieldName, count(c), avg(c.score), sum(case when c.matched = true then 1 else 0 end) "
            + "from ExtractionComparison c where c.runId = :runId group by c.fieldName order by c.fieldName")
    List<Object[]> summarizeRun(@Param("runId") String runId);

    @Query("select c.fieldName, count(c), avg(c.score), sum(case when c.matched = true then 1 else 0 end) "
            + "from ExtractionComparison c where c.runSource = :source group by c.fieldName order by c.fieldName")
    List<Object[]> summarizeSource(@Param("source") String source);
}
