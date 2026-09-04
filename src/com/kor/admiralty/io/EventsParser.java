/*******************************************************************************
 * Copyright (C) 2015, 2019 Dave Kor
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/
package com.kor.admiralty.io;

import com.kor.admiralty.beans.Event;
import com.kor.admiralty.enums.EventReward;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.util.Locale;
import java.util.SortedMap;

public class EventsParser {

    /**
     * Parses Events into the supplied destination map.
     *
     * @param reader Event CSV source, closed when parsing completes
     * @param events destination map keyed by case-folded Event name
     * @throws IOException              if CSV parsing or reader closure fails
     * @throws IllegalArgumentException if an Event record contains invalid
     *                                  reference data
     */
    public static void loadEvents(Reader reader, SortedMap<String, Event> events) throws IOException {
        try (Reader source = reader) {
            for (CSVRecord record : CSVFormat.EXCEL.withHeader().parse(source)) {
                Event event = loadEvent(record);
                events.put(event.getName().toLowerCase(Locale.ROOT), event);
            }
        }
    }

    /**
     * Parses one Event record.
     *
     * @param record CSV record to parse
     * @return the parsed Event
     * @throws IllegalArgumentException if the record contains invalid reference
     *                                  data
     */
    private static Event loadEvent(CSVRecord record) {
        String name = record.get("Event").trim();
        int eng = Integer.parseInt(record.get("Eng").trim());
        int tac = Integer.parseInt(record.get("Tac").trim());
        int sci = Integer.parseInt(record.get("Sci").trim());
        int critRate = Integer.parseInt(record.get("Crit").trim());
        EventReward reward = EventReward.valueOf(record.get("Reward").trim());
        return new Event(name, eng, tac, sci, critRate, reward);
    }

}
