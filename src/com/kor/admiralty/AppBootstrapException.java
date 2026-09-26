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
package com.kor.admiralty;

import java.io.Serial;

/**
 * Reports a checked failure while loading application state during startup.
 */
public class AppBootstrapException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Wraps the load failure that prevented startup from publishing application
     * state.
     *
     * @param message user-readable startup failure summary
     * @param cause   underlying GameData or Admirals load failure
     */
    public AppBootstrapException(String message, Throwable cause) {
        super(message, cause);
    }
}
