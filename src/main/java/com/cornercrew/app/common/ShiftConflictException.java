package com.cornercrew.app.common;

import java.util.List;

public class ShiftConflictException extends RuntimeException {

    private final List<String> conflictingSlots;

    public ShiftConflictException(List<String> conflictingSlots) {
        super("Shift conflict detected for slots: " + String.join(", ", conflictingSlots));
        this.conflictingSlots = List.copyOf(conflictingSlots);
    }

    public List<String> getConflictingSlots() {
        return conflictingSlots;
    }
}
