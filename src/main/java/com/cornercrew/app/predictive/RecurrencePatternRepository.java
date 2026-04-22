package com.cornercrew.app.predictive;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;

public interface RecurrencePatternRepository extends JpaRepository<RecurrencePattern, Long> {

    List<RecurrencePattern> findByIntersectionId(Long intersectionId);

    List<RecurrencePattern> findByIntersectionIdAndDayOfWeek(Long intersectionId, DayOfWeek dayOfWeek);
}
