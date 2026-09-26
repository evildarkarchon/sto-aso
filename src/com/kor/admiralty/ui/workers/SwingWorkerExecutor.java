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

import com.kor.admiralty.AppBootstrap;
import com.kor.admiralty.io.GameDataRefresh;

import javax.swing.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
     * Schedules the exact application-owned GameData Refresh already consulted by
     * bootstrap.
     *
     * @param refresh GameData Refresh instance due for background work
     */
    @Override
    public void scheduleGameDataRefresh(GameDataRefresh refresh) {
        exec(new UpdateDataFiles(refresh));
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
