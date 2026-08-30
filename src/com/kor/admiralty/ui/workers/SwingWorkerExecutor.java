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
package com.kor.admiralty.ui.workers;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.SwingWorker;

import com.kor.admiralty.App;
import com.kor.admiralty.AppBootstrap;
import com.kor.admiralty.beans.Ship;
import com.kor.admiralty.ui.resources.ActualShipIconFactory;

/**
 * Runs Swing background workers and adapts AppBootstrap job requests to the production executor.
 */
public class SwingWorkerExecutor implements AppBootstrap.BackgroundJobs {

    private static final int MAX_WORKER_THREAD = 3;
    private static final SwingWorkerExecutor EXECUTOR = new SwingWorkerExecutor();

    private final ExecutorService workerThreadPool = Executors.newFixedThreadPool(MAX_WORKER_THREAD);

    private SwingWorkerExecutor() {
    }

    public static SwingWorkerExecutor getInstance() {
        return EXECUTOR;
    }

    public static <T, V> void exec(SwingWorker<T, V> worker) {
        getInstance().execute(worker);
    }

    /**
     * Queues one file download rooted beneath the bootstrapped application data directory.
     *
     * @param dataDirectory directory receiving the downloaded file
     * @param filename destination filename beneath the data directory
     * @param remoteUrl absolute URL supplying the file contents
     */
    public static void downloadFile(Path dataDirectory, String filename, String remoteUrl) {
        exec(new FileDownloader(dataDirectory, filename, remoteUrl));
    }
    public static void downloadIcon(Ship ship) {
        // Don't download if we already have a ship icon either in the .jar or icons.zip file
        String iconName = ship.getIconName();
        if (ActualShipIconFactory.hasBundledIcon(iconName)) return;
        if (App.iconCache().contains(iconName)) return;
        exec(new ShipIconLoader(ship.getName(), iconName));
    }

    /**
     * Queues a download-only GameData refresh for a caller-supplied data directory.
     *
     * @param dataDirectory directory containing and receiving GameData files
     */
    public static void updateDataFiles(Path dataDirectory) {
        exec(new UpdateDataFiles(dataDirectory));
    }

    /**
     * Schedules a download-only GameData refresh beneath the bootstrapped directory.
     *
     * @param dataDirectory directory receiving refreshed files
     */
    @Override
    public void scheduleDataFileUpdate(Path dataDirectory) {
        updateDataFiles(dataDirectory);
    }

    /**
     * Schedules one current-Roster Ship icon download through the existing Swing pipeline.
     *
     * @param ship canonical current-Roster Ship whose icon may need downloading
     */
    @Override
    public void scheduleIconDownload(Ship ship) {
        downloadIcon(ship);
    }

    /**
     *
     * Adds the SwingWorker to the thread pool for execution.
     *
     * @param worker - The SwingWorker thread to execute.
     *
     */
    public <T, V> void execute(SwingWorker<T, V> worker) {
        workerThreadPool.submit(worker);
    }

}
