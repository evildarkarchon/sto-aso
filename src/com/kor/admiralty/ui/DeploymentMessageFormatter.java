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
package com.kor.admiralty.ui;

import java.util.List;

import com.kor.admiralty.beans.Deployment;
import com.kor.admiralty.beans.DeploymentOutcome;
import com.kor.admiralty.beans.DeploymentRejection;
import com.kor.admiralty.beans.RosterCard;

/**
 * Owns Swing-facing text for structured Admiral deployment outcomes.
 */
public final class DeploymentMessageFormatter {

    private DeploymentMessageFormatter() {
    }

    /**
     * Formats either one committed deployment or one expected rejection without
     * leaking presentation into Admiral.
     *
     * @param outcome structured outcome returned by Admiral
     * @return dialog-ready plain text or HTML
     * @throws NullPointerException if {@code outcome} is null
     */
    public static String format(DeploymentOutcome outcome) {
        if (outcome instanceof Deployment deployment) {
            String reusable = cardList(deployment.getReusableCards());
            String oneTime = cardList(deployment.getOneTimeCards());
            StringBuilder message = new StringBuilder("<html>");
            if (!reusable.isEmpty()) {
                message.append("Active ship(s) assigned:</br><ul class=\"info\">")
                        .append(reusable)
                        .append("</ul>");
            }
            if (!oneTime.isEmpty()) {
                message.append("One-time ship(s) assigned:</br><ul class=\"info\">")
                        .append(oneTime)
                        .append("</ul>");
            }
            return message.append("</html>").toString();
        }

        DeploymentRejection rejection = (DeploymentRejection) outcome;
        switch (rejection.getReason()) {
            case STALE_SOLUTION:
                return "The roster or assignments changed after this solution was planned. Please plan again.";
            case UNAVAILABLE_CARD:
                return String.format(
                        "%s is no longer available. Please plan again.",
                        rejection.getShip().getDisplayName());
            case DUPLICATE_CARD:
                return String.format(
                        "%s was selected more than once. Please plan again.",
                        rejection.getShip().getDisplayName());
            case INSUFFICIENT_ONE_TIME_QUANTITY:
                return String.format(
                        "Only %d of %d requested One-Time %s cards remain. Please plan again.",
                        rejection.getAvailableQuantity(),
                        rejection.getRequestedQuantity(),
                        rejection.getShip().getDisplayName());
            default:
                throw new IllegalArgumentException("Unknown deployment rejection: " + rejection.getReason());
        }
    }

    /**
     * Formats deployed card names using the historical Swing list markup.
     *
     * @param cards cards in deployment order
     * @return concatenated list-item markup
     */
    private static String cardList(List<RosterCard> cards) {
        StringBuilder items = new StringBuilder();
        for (RosterCard card : cards) {
            items.append("<li>").append(card.getShip().getName()).append("</li>");
        }
        return items.toString();
    }
}
