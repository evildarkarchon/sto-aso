/**
 * Copyright (C) 2026 Dave Kor
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.kor.admiralty.beans;

import java.util.Objects;

/** Immutable display and edit projection of one Assignment revision. */
public record AssignmentView(
        int requiredEng,
        int requiredTac,
        int requiredSci,
        int eventEng,
        int eventTac,
        int eventSci,
        int eventCritRate,
        int targetCritChance,
        int duration) {

    /**
     * Returns an immutable projection of the supplied mutable Assignment.
     *
     * @param assignment mutable Assignment to snapshot
     * @return complete immutable field projection
     * @throws NullPointerException if {@code assignment} is {@code null}
     */
    public static AssignmentView from(Assignment assignment) {
        Objects.requireNonNull(assignment, "assignment");
        return new AssignmentView(
                assignment.getRequiredEng(),
                assignment.getRequiredTac(),
                assignment.getRequiredSci(),
                assignment.getEventEng(),
                assignment.getEventTac(),
                assignment.getEventSci(),
                assignment.getEventCritRate(),
                assignment.getTargetCritChance(),
                assignment.getDuration());
    }

    /** Returns the total Eng requirement including the Event. */
    public int eng() {
        return requiredEng + eventEng;
    }

    /** Returns the total Tac requirement including the Event. */
    public int tac() {
        return requiredTac + eventTac;
    }

    /** Returns the total Sci requirement including the Event. */
    public int sci() {
        return requiredSci + eventSci;
    }

    /** Returns the target critical rating derived from this complete projection. */
    public int critRate() {
        int total = eng() + tac() + sci();
        double chance = targetCritChance / 100d;
        double criticalRateMultiplier = (2 * chance) / (1 - chance);
        return (int) Math.floor(total * criticalRateMultiplier);
    }

    /** Returns a copy with all base requirements and duration replaced together. */
    public AssignmentView withRequirements(int eng, int tac, int sci, int newDuration) {
        return new AssignmentView(
                eng,
                tac,
                sci,
                eventEng,
                eventTac,
                eventSci,
                eventCritRate,
                targetCritChance,
                newDuration);
    }

    /** Returns a copy with all Event values replaced together. */
    public AssignmentView withEvent(int eng, int tac, int sci, int critRate) {
        return new AssignmentView(
                requiredEng,
                requiredTac,
                requiredSci,
                eng,
                tac,
                sci,
                critRate,
                targetCritChance,
                duration);
    }

    /** Returns a copy with the base Eng requirement replaced. */
    public AssignmentView withRequiredEng(int eng) {
        return new AssignmentView(
                eng, requiredTac, requiredSci, eventEng, eventTac, eventSci,
                eventCritRate, targetCritChance, duration);
    }

    /** Returns a copy with the base Tac requirement replaced. */
    public AssignmentView withRequiredTac(int tac) {
        return new AssignmentView(
                requiredEng, tac, requiredSci, eventEng, eventTac, eventSci,
                eventCritRate, targetCritChance, duration);
    }

    /** Returns a copy with the base Sci requirement replaced. */
    public AssignmentView withRequiredSci(int sci) {
        return new AssignmentView(
                requiredEng, requiredTac, sci, eventEng, eventTac, eventSci,
                eventCritRate, targetCritChance, duration);
    }

    /** Returns a copy with the Event Eng requirement replaced. */
    public AssignmentView withEventEng(int eng) {
        return new AssignmentView(
                requiredEng, requiredTac, requiredSci, eng, eventTac, eventSci,
                eventCritRate, targetCritChance, duration);
    }

    /** Returns a copy with the Event Tac requirement replaced. */
    public AssignmentView withEventTac(int tac) {
        return new AssignmentView(
                requiredEng, requiredTac, requiredSci, eventEng, tac, eventSci,
                eventCritRate, targetCritChance, duration);
    }

    /** Returns a copy with the Event Sci requirement replaced. */
    public AssignmentView withEventSci(int sci) {
        return new AssignmentView(
                requiredEng, requiredTac, requiredSci, eventEng, eventTac, sci,
                eventCritRate, targetCritChance, duration);
    }

    /** Returns a copy with the Event critical rating replaced. */
    public AssignmentView withEventCritRate(int critRate) {
        return new AssignmentView(
                requiredEng, requiredTac, requiredSci, eventEng, eventTac, eventSci,
                critRate, targetCritChance, duration);
    }

    /** Returns a copy with the target critical chance replaced. */
    public AssignmentView withTargetCritChance(int chance) {
        return new AssignmentView(
                requiredEng, requiredTac, requiredSci, eventEng, eventTac, eventSci,
                eventCritRate, chance, duration);
    }
}
