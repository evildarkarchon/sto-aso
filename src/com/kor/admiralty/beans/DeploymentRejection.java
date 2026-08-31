/**
 * Copyright (C) 2026 Dave Kor
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.kor.admiralty.beans;

import java.util.Objects;

/**
 * Immutable typed description of an expected deployment conflict that committed no state.
 */
public final class DeploymentRejection implements DeploymentOutcome {

    private final DeploymentRejectionReason reason;
    private final long solutionPlanningRevision;
    private final long currentPlanningRevision;
    private final RosterCard card;
    private final Ship ship;
    private final int requestedQuantity;
    private final int availableQuantity;

    /**
     * Creates a rejection with planning revision details when applicable.
     *
     * @param reason                   typed conflict reason
     * @param solutionPlanningRevision revision captured by the rejected Solution, or {@code -1}
     * @param currentPlanningRevision  current Admiral revision, or {@code -1}
     * @param card                     exact offending card, or {@code null}
     * @param ship                     canonical Ship associated with the conflict, or {@code null}
     * @param requestedQuantity        requested One-Time quantity, or {@code -1}
     * @param availableQuantity        available One-Time quantity, or {@code -1}
     * @throws NullPointerException if {@code reason} is null
     */
    private DeploymentRejection(
            DeploymentRejectionReason reason,
            long solutionPlanningRevision,
            long currentPlanningRevision,
            RosterCard card,
            Ship ship,
            int requestedQuantity,
            int availableQuantity) {
        this.reason = Objects.requireNonNull(reason, "reason");
        this.solutionPlanningRevision = solutionPlanningRevision;
        this.currentPlanningRevision = currentPlanningRevision;
        this.card = card;
        this.ship = ship;
        this.requestedQuantity = requestedQuantity;
        this.availableQuantity = availableQuantity;
    }

    /**
     * Creates a stale-Solution rejection without mutating the Admiral.
     *
     * @param solutionPlanningRevision revision captured by the Solution
     * @param currentPlanningRevision  current Admiral planning revision
     * @return structured stale rejection
     */
    static DeploymentRejection stale(long solutionPlanningRevision, long currentPlanningRevision) {
        return new DeploymentRejection(
                DeploymentRejectionReason.STALE_SOLUTION,
                solutionPlanningRevision,
                currentPlanningRevision,
                null,
                null,
                -1,
                -1);
    }

    /**
     * Creates a duplicate-card rejection identifying the repeated selection.
     *
     * @param card card selected more than once
     * @return structured duplicate rejection
     * @throws NullPointerException if {@code card} is null
     */
    static DeploymentRejection duplicate(RosterCard card) {
        return cardConflict(DeploymentRejectionReason.DUPLICATE_CARD, card);
    }

    /**
     * Creates an unavailable-card rejection identifying the non-deployable selection.
     *
     * @param card selected card that is absent or not Active
     * @return structured unavailable rejection
     * @throws NullPointerException if {@code card} is null
     */
    static DeploymentRejection unavailable(RosterCard card) {
        return cardConflict(DeploymentRejectionReason.UNAVAILABLE_CARD, card);
    }

    /**
     * Creates a One-Time quantity rejection after counting the complete requested batch.
     *
     * @param ship              selected One-Time Ship type
     * @param requestedQuantity copies requested by the Solution
     * @param availableQuantity copies currently available
     * @return structured insufficient-quantity rejection
     * @throws NullPointerException if {@code ship} is null
     */
    static DeploymentRejection insufficientOneTimeQuantity(
            Ship ship,
            int requestedQuantity,
            int availableQuantity) {
        return new DeploymentRejection(
                DeploymentRejectionReason.INSUFFICIENT_ONE_TIME_QUANTITY,
                -1L,
                -1L,
                null,
                Objects.requireNonNull(ship, "ship"),
                requestedQuantity,
                availableQuantity);
    }

    /**
     * Creates a rejection associated with one exact card identity.
     *
     * @param reason duplicate or unavailable reason
     * @param card   offending selected card
     * @return structured card rejection
     * @throws NullPointerException if an argument is null
     */
    private static DeploymentRejection cardConflict(
            DeploymentRejectionReason reason,
            RosterCard card) {
        RosterCard offendingCard = Objects.requireNonNull(card, "card");
        return new DeploymentRejection(
                reason,
                -1L,
                -1L,
                offendingCard,
                offendingCard.getShip(),
                -1,
                -1);
    }

    /**
     * Returns the expected conflict category.
     *
     * @return typed rejection reason
     */
    public DeploymentRejectionReason getReason() {
        return reason;
    }

    /**
     * Returns the planning revision captured by a stale Solution.
     *
     * @return Solution revision, or {@code -1} when the rejection is not revision-related
     */
    public long getSolutionPlanningRevision() {
        return solutionPlanningRevision;
    }

    /**
     * Returns the Admiral planning revision observed during stale validation.
     *
     * @return current revision, or {@code -1} when the rejection is not revision-related
     */
    public long getCurrentPlanningRevision() {
        return currentPlanningRevision;
    }

    /**
     * Returns the exact card associated with a duplicate or unavailable rejection.
     *
     * @return offending card, or {@code null} for revision and quantity rejections
     */
    public RosterCard getCard() {
        return card;
    }

    /**
     * Returns the canonical Ship associated with a card or quantity rejection.
     *
     * @return associated Ship, or {@code null} for a stale rejection
     */
    public Ship getShip() {
        return ship;
    }

    /**
     * Returns the number of One-Time copies requested by an insufficient batch.
     *
     * @return requested quantity, or {@code -1} for other rejection reasons
     */
    public int getRequestedQuantity() {
        return requestedQuantity;
    }

    /**
     * Returns the One-Time copies available when an insufficient batch was rejected.
     *
     * @return available quantity, or {@code -1} for other rejection reasons
     */
    public int getAvailableQuantity() {
        return availableQuantity;
    }
}
