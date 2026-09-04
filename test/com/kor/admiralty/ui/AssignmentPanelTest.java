/**
 * Copyright (C) 2026 Dave Kor
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.kor.admiralty.ui;

import com.kor.admiralty.beans.Admiral;
import com.kor.admiralty.beans.AssignmentSolution;
import com.kor.admiralty.beans.AssignmentView;
import com.kor.admiralty.beans.RosterState;
import com.kor.admiralty.io.GameData;
import com.kor.admiralty.ui.resources.ShipIconFactory;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the reversible editor binding through immutable views and Swing controls.
 */
class AssignmentPanelTest {
    private static final AssignmentView INITIAL = new AssignmentView(11, 22, 33, 4, 5, 6, 7, 20, 95);
    private static final ShipIconFactory ICONS = (iconName, faction, role, rarity, owned) ->
            new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));

    /** Creates a real unbound editor with isolated reference data and artwork. */
    private static AssignmentPanel editor() {
        return new AssignmentPanel(GameData.builder().build(), ICONS);
    }

    /** Records displayed values without relying on component positions or layout. */
    private static List<Object> presentation(Container container) {
        List<Object> values = new ArrayList<>();
        for (Component component : container.getComponents()) {
            if (component instanceof JLabel label) {
                values.add(label.getText());
                values.add(label.getIcon());
            } else if (component instanceof JFormattedTextField field) {
                values.add(field.getValue());
            } else if (component instanceof JSlider slider) {
                values.add(slider.getValue());
            } else if (component instanceof JComboBox<?> combo) {
                values.add(combo.getSelectedItem());
            }
            if (component instanceof Container child) {
                values.addAll(presentation(child));
            }
        }
        return values;
    }

    /** Projection changes controls silently, including repeated authoritative updates. */
    @Test
    void projectionDoesNotEmitEdits() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AssignmentPanel editor = editor();
            List<AssignmentView> edits = new ArrayList<>();
            editor.setAssignmentView(INITIAL, edits::add);
            editor.setAssignmentView(new AssignmentView(71, 72, 73, 14, 15, 16, 17, 60, 120), edits::add);

            assertAll(
                    () -> assertTrue(edits.isEmpty()),
                    () -> assertEquals(71, editor.txtAssignmentEng.getValue()),
                    () -> assertEquals(73, editor.txtAssignmentSci.getValue()),
                    () -> assertEquals(17, editor.txtEventCritRating.getValue()));
        });
    }

    /** Edits arrive before control mutation returns and use the owner's latest projection. */
    @Test
    void editsEmitCompleteStateSynchronouslyAndUseAuthoritativeReprojection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AssignmentPanel editor = editor();
            List<AssignmentView> edits = new ArrayList<>();
            Consumer<AssignmentView> owner = new Consumer<>() {
                /** Reprojects normalized owner state before the control edit returns. */
                @Override
                public void accept(AssignmentView intended) {
                    assertTrue(SwingUtilities.isEventDispatchThread());
                    edits.add(intended);
                    // The owner may normalize an edit; the next edit must use that projection.
                    editor.setAssignmentView(intended.withRequiredEng(80), this);
                }
            };
            editor.setAssignmentView(INITIAL, owner);
            editor.txtAssignmentEng.setValue(81);
            assertEquals(List.of(new AssignmentView(81, 22, 33, 4, 5, 6, 7, 20, 95)), edits);
            editor.txtEventTac.setValue(19);
            assertEquals(List.of(
                    new AssignmentView(81, 22, 33, 4, 5, 6, 7, 20, 95),
                    new AssignmentView(80, 22, 33, 4, 19, 6, 7, 20, 95)), edits);
        });
    }

    /** Reporting intent alone never commits an optimistic editor-owned state. */
    @Test
    void editsWithoutReprojectionStillUseTheLastAuthoritativeView() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AssignmentPanel editor = editor();
            List<AssignmentView> edits = new ArrayList<>();
            editor.setAssignmentView(INITIAL, edits::add);
            editor.txtAssignmentEng.setValue(81);
            editor.txtEventTac.setValue(19);
            assertEquals(new AssignmentView(11, 22, 33, 4, 19, 6, 7, 20, 95), edits.getLast());
        });
    }

    /** Unbinding releases ownership and the retained Solution without changing presentation. */
    @Test
    void unbindingReleasesViewCallbackAndSolutionWhileFreezingControls() throws Exception {
        GameData gameData = GameData.load(Path.of("test", "resources", "gamedata"));
        SwingUtilities.invokeAndWait(() -> {
            AssignmentPanel editor = editor();
            List<AssignmentView> edits = new ArrayList<>();
            editor.setAssignmentView(INITIAL, edits::add);
            Admiral admiral = new Admiral(gameData);
            admiral.addReusableShips(gameData.ships(), RosterState.ACTIVE);
            admiral.getAssignment(0).apply(INITIAL);
            AssignmentSolution solution = admiral.solveAssignments().getFirst().getSolution(0);
            editor.setAssignmentSolution(solution);
            assertSame(solution, editor.solution);
            List<Object> displayed = presentation(editor);

            editor.setAssignmentView(null, ignored -> fail("Unbinding must ignore the supplied callback"));
            assertAll(
                    () -> assertFalse(editor.hasAssignmentView()),
                    () -> assertNull(editor.solution),
                    () -> assertEquals(displayed, presentation(editor)),
                    () -> assertThrows(IllegalStateException.class, editor::clearAssignment),
                    () -> assertThrows(IllegalStateException.class, () -> editor.setAssignmentSolution(null)));
            editor.setAssignmentView(null, null);
            editor.txtAssignmentEng.setValue(99);
            editor.txtEventCritRating.setEnabled(false);
            assertTrue(edits.isEmpty());
        });
    }

    /** A reversible unbind transfers all later edits exclusively to the new owner. */
    @Test
    void rebindingDeliversEditsOnlyToTheNewOwner() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AssignmentPanel editor = editor();
            List<AssignmentView> oldEdits = new ArrayList<>();
            List<AssignmentView> newEdits = new ArrayList<>();
            editor.setAssignmentView(INITIAL, oldEdits::add);
            editor.setAssignmentView(null, null);
            editor.txtAssignmentEng.setValue(99);
            editor.setAssignmentView(new AssignmentView(31, 32, 33, 1, 2, 3, 4, 40, 65), newEdits::add);
            editor.txtAssignmentTac.setValue(88);

            assertAll(
                    () -> assertTrue(oldEdits.isEmpty()),
                    () -> assertEquals(List.of(new AssignmentView(31, 88, 33, 1, 2, 3, 4, 40, 65)), newEdits));
        });
    }

    /** Failed binding leaves both prior controls and the complete state used by its owner intact. */
    @Test
    void nullCallbackFailurePreservesPreviousViewAndOwner() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AssignmentPanel editor = editor();
            List<AssignmentView> edits = new ArrayList<>();
            editor.setAssignmentView(INITIAL, edits::add);
            List<Object> displayed = presentation(editor);
            assertThrows(NullPointerException.class, () -> editor.setAssignmentView(
                    new AssignmentView(91, 92, 93, 14, 15, 16, 17, 60, 120), null));
            assertEquals(displayed, presentation(editor));
            assertTrue(editor.hasAssignmentView());
            editor.txtAssignmentTac.setValue(88);
            assertEquals(List.of(new AssignmentView(11, 88, 33, 4, 5, 6, 7, 20, 95)), edits);
        });
    }

    /** The surviving constructor validates dependencies and clear retains its bound-view contract. */
    @Test
    void dependenciesAndBoundViewRequirementsRemainEnforced() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertThrows(NullPointerException.class, () -> new AssignmentPanel(null, ICONS));
            assertThrows(NullPointerException.class, () -> new AssignmentPanel(GameData.builder().build(), null));
            AssignmentPanel editor = editor();
            assertThrows(IllegalStateException.class, editor::clearAssignment);
            assertThrows(IllegalStateException.class, () -> editor.setAssignmentSolution(null));
            List<AssignmentView> edits = new ArrayList<>();
            editor.setAssignmentView(INITIAL, edits::add);
            editor.clearAssignment();
            assertEquals(List.of(new AssignmentView(0, 0, 0, 0, 0, 0, 0, 0, 0)), edits);
        });
    }
}
