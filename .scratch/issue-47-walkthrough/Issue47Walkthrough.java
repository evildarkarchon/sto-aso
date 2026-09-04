package com.kor.admiralty;

import com.kor.admiralty.beans.*;
import com.kor.admiralty.io.*;
import com.kor.admiralty.ui.panels.AdmiralPanel;
import com.kor.admiralty.ui.resources.*;
import java.awt.event.*;
import java.nio.file.*;
import javax.swing.*;

/** Launches an isolated, visible production workspace for agent-operated UI verification. */
public final class Issue47Walkthrough {
    /** Creates in-memory test Admiral data; never loads or saves the user's Admirals. */
    public static void main(String[] args) throws Exception {
        Path isolated = Path.of(args[0]);
        Files.createDirectories(isolated);
        GameData data = GameData.load(Path.of(args[1]));
        Admirals admirals = new Admirals(data);
        AdmiralsStore store = new AdmiralsStore();
        App.initialize(data, admirals, isolated, store, new IconCache(isolated));
        SwingUtilities.invokeAndWait(() -> {
            Admiral admiral = new Admiral(data);
            admiral.setName("Issue 47 isolated walkthrough");
            admiral.addReusableShips(data.ships().stream().limit(6).toList(), RosterState.ACTIVE);
            AdmiralPanel root = new AdmiralPanel(admiral, data, store, isolated,
                    (iconName, faction, role, rarity, owned) -> Images.ICON_BLANK);
            JFrame frame = new JFrame("Issue 47 - isolated production Admiral workspace");
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() {
                /** Disposes the production root before closing the temporary frame. */
                @Override public void windowClosing(WindowEvent event) {
                    root.dispose();
                    System.out.println("WORKSPACE_DISPOSE_RETURNED_ON_EDT=" + SwingUtilities.isEventDispatchThread());
                    frame.dispose();
                    System.out.println("FRAME_CLOSED");
                    System.exit(0);
                }
            });
            frame.setContentPane(root);
            frame.setSize(1380, 1000);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            System.out.println("WORKSPACE_VISIBLE");
        });
    }
}
