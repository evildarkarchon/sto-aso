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
import com.kor.admiralty.beans.RosterCard;
import com.kor.admiralty.beans.RosterState;
import com.kor.admiralty.io.GameData;
import com.kor.admiralty.ui.resources.ShipIconFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the reversible editor binding through immutable views and real Swing controls.
 */
class AssignmentPanelTest {
    private static final AssignmentView INITIAL = new AssignmentView(11, 22, 33, 4, 5, 6, 7, 20, 95);
    private static final ShipIconFactory ICONS = (iconName, faction, role, rarity, owned) ->
            new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));

    /** Creates a real unbound editor with isolated reference data and artwork. */
    private static AssignmentPanel editor() {
        return new AssignmentPanel(GameData.builder().build(), ICONS);
    }

    /** Construction rejects off-thread use without relying on a subclass hook. */
    @Test
    void constructionRejectsOffThreadCalls() {
        assertFalse(SwingUtilities.isEventDispatchThread());
        assertThrows(IllegalStateException.class,
                () -> new AssignmentPanel(GameData.builder().build(), ICONS));
    }

    /** Lists surviving mutations that must reject calls before changing editor state. */
    private static Stream<Arguments> offThreadMutations() {
        return Stream.of(
                Arguments.of("setAssignmentView", (Consumer<AssignmentPanel>) editor ->
                        editor.setAssignmentView(INITIAL.withRequiredEng(99), ignored -> fail("Wrong owner"))),
                Arguments.of("setAssignmentView(null)", (Consumer<AssignmentPanel>) editor ->
                        editor.setAssignmentView(null, null)),
                Arguments.of("setAssignmentSolution", (Consumer<AssignmentPanel>) editor ->
                        editor.setAssignmentSolution(null)));
    }

    /** Calculates a real Solution for the initial view using the supplied fixture Ships. */
    private static AssignmentSolution solution(GameData gameData) {
        Admiral admiral = new Admiral(gameData);
        admiral.addReusableShips(gameData.ships(), RosterState.ACTIVE);
        admiral.getAssignment(0).apply(INITIAL);
        return admiral.solveAssignments().getFirst().getSolution(0);
    }

    /** One real numeric-control mutation and its independently expected complete intent. */
    private record NumericControlEdit(
            String name,
            BiConsumer<AssignmentPanel.NumericControls, Integer> mutation,
            int value,
            AssignmentView expected) {

        /** Applies this case through the real formatted control. */
        private void apply(AssignmentPanel.NumericControls controls) {
            mutation.accept(controls, value);
        }

        /** Returns the control name used by the parameterized test display. */
        @Override
        public String toString() {
            return name;
        }
    }

    /** Lists each numeric-control edit and its complete intended Assignment state. */
    private static Stream<NumericControlEdit> numericControlEdits() {
        return Stream.of(
                new NumericControlEdit("Assignment ENG",
                        (BiConsumer<AssignmentPanel.NumericControls, Integer>)
                                AssignmentPanel.NumericControls::setAssignmentEng,
                        81, new AssignmentView(81, 22, 33, 4, 5, 6, 7, 20, 95)),
                new NumericControlEdit("Assignment TAC",
                        (BiConsumer<AssignmentPanel.NumericControls, Integer>)
                                AssignmentPanel.NumericControls::setAssignmentTac,
                        82, new AssignmentView(11, 82, 33, 4, 5, 6, 7, 20, 95)),
                new NumericControlEdit("Assignment SCI",
                        (BiConsumer<AssignmentPanel.NumericControls, Integer>)
                                AssignmentPanel.NumericControls::setAssignmentSci,
                        83, new AssignmentView(11, 22, 83, 4, 5, 6, 7, 20, 95)),
                new NumericControlEdit("Event ENG",
                        (BiConsumer<AssignmentPanel.NumericControls, Integer>)
                                AssignmentPanel.NumericControls::setEventEng,
                        14, new AssignmentView(11, 22, 33, 14, 5, 6, 7, 20, 95)),
                new NumericControlEdit("Event TAC",
                        (BiConsumer<AssignmentPanel.NumericControls, Integer>)
                                AssignmentPanel.NumericControls::setEventTac,
                        15, new AssignmentView(11, 22, 33, 4, 15, 6, 7, 20, 95)),
                new NumericControlEdit("Event SCI",
                        (BiConsumer<AssignmentPanel.NumericControls, Integer>)
                                AssignmentPanel.NumericControls::setEventSci,
                        16, new AssignmentView(11, 22, 33, 4, 5, 16, 7, 20, 95)),
                new NumericControlEdit("Event CRIT",
                        (BiConsumer<AssignmentPanel.NumericControls, Integer>)
                                AssignmentPanel.NumericControls::setEventCritRate,
                        17, new AssignmentView(11, 22, 33, 4, 5, 6, 17, 20, 95)));
    }

    /**
     * Every numeric seam mutation traverses its real control and reports complete intent.
     *
     * @throws Exception if Swing event-thread dispatch fails
     */
    @ParameterizedTest(name = "{0} reports complete intent")
    @MethodSource("numericControlEdits")
    void numericControlsReportCompleteAssignmentIntent(NumericControlEdit edit) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AssignmentPanel editor = editor();
            List<AssignmentView> edits = new ArrayList<>();
            editor.setAssignmentView(INITIAL, edits::add);

            edit.apply(editor.numericControls());

            assertEquals(List.of(edit.expected()), edits, edit.name());
        });
    }

    /**
     * Failed surviving mutations preserve visible presentation, controls, and callback ownership.
     *
     * @throws Exception if Swing event-thread dispatch fails
     */
    @ParameterizedTest(name = "{0} rejects off-thread mutation without partial changes")
    @MethodSource("offThreadMutations")
    void survivingMutationsRejectOffThreadCallsWithoutPartialChanges(
            String operation, Consumer<AssignmentPanel> mutation) throws Exception {
        GameData gameData = GameData.load(Path.of("test", "resources", "gamedata"));
        AtomicReference<AssignmentPanel> reference = new AtomicReference<>();
        List<AssignmentView> edits = new ArrayList<>();
        List<Object> displayed = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            AssignmentPanel editor = new AssignmentPanel(gameData, ICONS);
            editor.setAssignmentView(INITIAL, edits::add);
            editor.setAssignmentSolution(solution(gameData));
            displayed.addAll(presentation(editor));
            reference.set(editor);
        });

        assertFalse(SwingUtilities.isEventDispatchThread());
        assertThrows(IllegalStateException.class, () -> mutation.accept(reference.get()), operation);
        SwingUtilities.invokeAndWait(() -> {
            AssignmentPanel editor = reference.get();
            assertAll(
                    () -> assertTrue(editor.hasAssignmentView()),
                    () -> assertEquals(displayed, presentation(editor)),
                    () -> assertTrue(edits.isEmpty()));
            editor.numericControls().setAssignmentTac(88);
            assertEquals(List.of(new AssignmentView(11, 88, 33, 4, 5, 6, 7, 20, 95)), edits);
        });
    }

    /**
     * Complete Solution projection and clearing are observable through the visible component tree.
     *
     * @throws Exception if Swing event-thread dispatch fails
     */
    @Test
    void completeSolutionProjectionAndClearingUpdateVisiblePresentation() throws Exception {
        GameData gameData = GameData.load(Path.of("test", "resources", "gamedata"));
        SwingUtilities.invokeAndWait(() -> {
            AssignmentPanel editor = new AssignmentPanel(gameData, ICONS);
            List<AssignmentView> edits = new ArrayList<>();
            editor.setAssignmentView(INITIAL, edits::add);
            editor.setAssignmentSolution(null);
            List<Object> cleared = presentation(editor);

            AssignmentSolution solution = solution(gameData);
            editor.setAssignmentSolution(solution);
            List<Object> projected = presentation(editor);
            assertNotEquals(cleared, projected);
            for (RosterCard card : solution.getRosterCards()) {
                if (card != null) {
                    assertTrue(projected.contains(card.getShip().getDisplayName()));
                }
            }

            editor.setAssignmentSolution(null);
            assertAll(
                    () -> assertEquals(cleared, presentation(editor)),
                    () -> assertTrue(edits.isEmpty()),
                    () -> assertTrue(editor.hasAssignmentView()));
        });
    }

    /**
     * A new Assignment projection refreshes its visible requirement summary after
     * the owning workspace has cleared the previous Solution.
     *
     * @throws Exception if Swing event-thread dispatch fails
     */
    @Test
    void projectedRequirementUpdatesUnslottedSummaryImmediately() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AssignmentPanel editor = editor();
            editor.setAssignmentView(INITIAL, ignored -> fail("Projection must not report an edit"));
            editor.setAssignmentSolution(null);

            editor.setAssignmentView(INITIAL.withRequiredEng(50),
                    ignored -> fail("Projection must not report an edit"));

            assertTrue(presentation(editor).contains(
                    "<html>ENG: <font color=\"black\"><b>0</b></font> / <b>54</b></html>"));
        });
    }

    /** Records displayed values without relying on component positions or layout. */
    private static List<Object> presentation(Container container) {
        List<Object> values = new ArrayList<>();
        for (Component component : container.getComponents()) {
            if (!component.isVisible()) {
                continue;
            }
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

    /**
     * Waits briefly for weakly reachable former owners to be collected.
     * Explicit collection is limited to this lifetime assertion because the
     * editor intentionally exposes no callback or retained-Solution inspection.
     *
     * @param references former owner objects that must no longer be retained
     * @throws InterruptedException if the wait is interrupted
     */
    private static void assertEventuallyReleased(List<WeakReference<?>> references) throws InterruptedException {
        for (int attempt = 0; attempt < 40; attempt++) {
            System.gc();
            if (references.stream().allMatch(reference -> reference.get() == null)) {
                return;
            }
            Thread.sleep(25);
        }
        fail("Unbinding must release the former callback owner and retained Solution");
    }

    /**
     * Projection changes all seven numeric controls silently.
     *
     * @throws Exception if Swing event-thread dispatch fails
     */
    @Test
    void projectionDoesNotEmitEdits() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AssignmentPanel editor = editor();
            List<AssignmentView> edits = new ArrayList<>();
            editor.setAssignmentView(INITIAL, edits::add);
            editor.setAssignmentView(new AssignmentView(71, 72, 73, 14, 15, 16, 17, 60, 120), edits::add);

            assertAll(
                    () -> assertTrue(edits.isEmpty()),
                    () -> assertEquals(
                            new AssignmentPanel.NumericControlValues(71, 72, 73, 14, 15, 16, 17),
                            editor.numericControls().values()));
        });
    }

    /**
     * Edits arrive before control mutation returns and use the owner's latest projection.
     *
     * @throws Exception if Swing event-thread dispatch fails
     */
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
            editor.numericControls().setAssignmentEng(81);
            assertEquals(List.of(new AssignmentView(81, 22, 33, 4, 5, 6, 7, 20, 95)), edits);
            editor.numericControls().setEventTac(19);
            assertEquals(List.of(
                    new AssignmentView(81, 22, 33, 4, 5, 6, 7, 20, 95),
                    new AssignmentView(80, 22, 33, 4, 19, 6, 7, 20, 95)), edits);
        });
    }

    /**
     * Reporting intent alone never commits optimistic editor-owned state.
     *
     * @throws Exception if Swing event-thread dispatch fails
     */
    @Test
    void editsWithoutReprojectionStillUseTheLastAuthoritativeView() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AssignmentPanel editor = editor();
            List<AssignmentView> edits = new ArrayList<>();
            editor.setAssignmentView(INITIAL, edits::add);
            editor.numericControls().setAssignmentEng(81);
            editor.numericControls().setEventTac(19);
            assertEquals(new AssignmentView(11, 22, 33, 4, 19, 6, 7, 20, 95), edits.getLast());
        });
    }

    /**
     * Unbinding freezes controls, releases old owners, and supports an exclusive later owner.
     *
     * @throws Exception if Swing dispatch or the bounded release wait fails
     */
    @Test
    void reversibleUnbindingFreezesControlsAndTransfersOwnership() throws Exception {
        GameData gameData = GameData.load(Path.of("test", "resources", "gamedata"));
        AtomicReference<AssignmentPanel> reference = new AtomicReference<>();
        AtomicReference<AssignmentPanel.NumericControlValues> controls = new AtomicReference<>();
        List<Object> displayed = new ArrayList<>();
        List<AssignmentView> oldEdits = new ArrayList<>();
        List<AssignmentView> newEdits = new ArrayList<>();
        List<WeakReference<?>> formerOwners = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            AssignmentPanel editor = new AssignmentPanel(gameData, ICONS);
            Consumer<AssignmentView> oldOwner = oldEdits::add;
            AssignmentSolution oldSolution = solution(gameData);
            formerOwners.add(new WeakReference<>(oldOwner));
            formerOwners.add(new WeakReference<>(oldSolution));
            editor.setAssignmentView(INITIAL, oldOwner);
            editor.setAssignmentSolution(oldSolution);
            displayed.addAll(presentation(editor));
            controls.set(editor.numericControls().values());

            editor.setAssignmentView(null, ignored -> fail("Unbinding must ignore the supplied callback"));
            assertAll(
                    () -> assertFalse(editor.hasAssignmentView()),
                    () -> assertEquals(displayed, presentation(editor)),
                    () -> assertEquals(controls.get(), editor.numericControls().values()),
                    () -> assertThrows(IllegalStateException.class, () -> editor.setAssignmentSolution(null)));
            editor.numericControls().setAssignmentEng(99);
            assertTrue(oldEdits.isEmpty());
            reference.set(editor);
        });

        assertEventuallyReleased(formerOwners);

        SwingUtilities.invokeAndWait(() -> {
            AssignmentPanel editor = reference.get();
            AssignmentView rebound = new AssignmentView(31, 32, 33, 1, 2, 3, 4, 40, 65);
            editor.setAssignmentView(rebound, newEdits::add);
            editor.setAssignmentSolution(null);
            editor.numericControls().setAssignmentTac(88);

            assertAll(
                    () -> assertTrue(oldEdits.isEmpty()),
                    () -> assertEquals(
                            List.of(new AssignmentView(31, 88, 33, 1, 2, 3, 4, 40, 65)),
                            newEdits));
        });
    }

    /**
     * Failed binding leaves prior controls, visible presentation, and owner intact.
     *
     * @throws Exception if Swing event-thread dispatch fails
     */
    @Test
    void nullCallbackFailurePreservesPreviousViewAndOwner() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AssignmentPanel editor = editor();
            List<AssignmentView> edits = new ArrayList<>();
            editor.setAssignmentView(INITIAL, edits::add);
            List<Object> displayed = presentation(editor);
            AssignmentPanel.NumericControlValues controls = editor.numericControls().values();
            assertThrows(NullPointerException.class, () -> editor.setAssignmentView(
                    new AssignmentView(91, 92, 93, 14, 15, 16, 17, 60, 120), null));
            assertAll(
                    () -> assertEquals(displayed, presentation(editor)),
                    () -> assertEquals(controls, editor.numericControls().values()),
                    () -> assertTrue(editor.hasAssignmentView()));
            editor.numericControls().setAssignmentTac(88);
            assertEquals(List.of(new AssignmentView(11, 88, 33, 4, 5, 6, 7, 20, 95)), edits);
        });
    }

    /**
     * Constructor dependencies and unbound complete-Solution projection remain enforced.
     *
     * @throws Exception if Swing event-thread dispatch fails
     */
    @Test
    void dependenciesAndUnboundSolutionRequirementRemainEnforced() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertThrows(NullPointerException.class, () -> new AssignmentPanel(null, ICONS));
            assertThrows(NullPointerException.class, () -> new AssignmentPanel(GameData.builder().build(), null));
            AssignmentPanel editor = editor();
            assertThrows(IllegalStateException.class, () -> editor.setAssignmentSolution(null));
        });
    }
}
