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

import java.io.IOException;
import java.io.Reader;
import java.util.Locale;
import java.util.SortedMap;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import com.kor.admiralty.beans.AdmAssignment;
import com.kor.admiralty.enums.Rarity;

public class AssignmentsParser {

    /**
     * Parses Assignments into the supplied destination map.
     *
     * @param reader      Assignment CSV source, closed when parsing completes
     * @param assignments destination map keyed by case-folded Assignment name
     * @throws IOException              if CSV parsing or reader closure fails
     * @throws IllegalArgumentException if an Assignment record contains invalid
     *                                  reference data
     */
    public static void loadAssignments(Reader reader, SortedMap<String, AdmAssignment> assignments) throws IOException {
        try (Reader source = reader) {
            for (CSVRecord record : CSVFormat.EXCEL.withHeader().parse(source)) {
                AdmAssignment assignment = loadAssignment(record);
                assignments.put(assignment.getName().toLowerCase(Locale.ROOT), assignment);
            }
        }
    }

    /**
     * Parses one Assignment record.
     *
     * @param record CSV record to parse
     * @return the parsed Assignment
     * @throws IllegalArgumentException if the record contains invalid reference
     *                                  data
     */
    private static AdmAssignment loadAssignment(CSVRecord record) {
        // Just in case LibreOffice Calc replaced dashes '-' with '–'
        String name = record.get("Assignment").trim().replace('–', '-');
        Rarity rarity = Rarity.valueOf(record.get("Rarity").trim());
        int eng = Integer.parseInt(record.get("Eng").trim());
        int tac = Integer.parseInt(record.get("Tac").trim());
        int sci = Integer.parseInt(record.get("Sci").trim());
        int hours = Integer.parseInt(record.get("Hours").trim());
        int minutes = Integer.parseInt(record.get("Minutes").trim());
        return new AdmAssignment(name, rarity, eng, tac, sci, hours, minutes);
    }

}
