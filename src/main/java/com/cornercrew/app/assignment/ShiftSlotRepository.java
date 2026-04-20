package com.cornercrew.app.assignment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ShiftSlotRepository extends JpaRepository<ShiftSlot, Long> {

    boolean existsByIntersectionIdAndDateAndShiftType(Long intersectionId, LocalDate date, ShiftType shiftType);

    List<ShiftSlot> findByAssignmentId(Long assignmentId);
}
