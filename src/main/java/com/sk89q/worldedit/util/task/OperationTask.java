/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.util.task;

import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.TimedRunContext;
import com.sk89q.worldedit.util.scheduler.TickScheduler;
import com.sk89q.worldedit.util.task.progress.Progress;

import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An implementation of {@link Task} for operations.
 */
class OperationTask extends AbstractTask<Operation> implements Task<Operation>, Runnable {

    private static final Logger logger = Logger.getLogger(OperationTask.class.getCanonicalName());
    private final Operation initialOperation;
    private final TickScheduler scheduler;
    private Operation nextOperation;
    private State state = State.SCHEDULED;
    private long elapsedTime = 0;

    /**
     * Create a new task.
     *
     * @param operation the operation
     * @param scheduler the scheduler
     * @param name the name of the task
     * @param owner the owner of the taks
     */
    OperationTask(Operation operation, TickScheduler scheduler, @Nullable String name, @Nullable Object owner) {
        super(name, owner);
        checkNotNull(operation);
        checkNotNull(scheduler);
        this.initialOperation = operation;
        this.nextOperation = operation;
        this.scheduler = scheduler;
    }

    @Override
    public void run() {
        state = State.RUNNING;
        try {
            long now = System.nanoTime();
            nextOperation = nextOperation.resume(new TimedRunContext(20, TimeUnit.MILLISECONDS));
            elapsedTime += System.nanoTime() - now;

            if (nextOperation != null) {
                submitToScheduler();
            } else {
                state = State.SUCCEEDED;
                logger.log(Level.FINE, initialOperation + " took " + TimeUnit.MILLISECONDS.convert(elapsedTime, TimeUnit.NANOSECONDS) + "ms");
                set(initialOperation);
            }
        } catch (WorldEditException e) {
            state = State.FAILED;
            setException(e);
        }
    }

    @Override
    public State getState() {
        if (isCancelled()) {
            return State.CANCELLED;
        } else {
            return state;
        }
    }

    /**
     * Submit this task to run in the scheduler.
     */
    void submitToScheduler() {
        scheduler.schedule(this, 0);
    }

    @Override
    public Progress getProgress() {
        Operation next = nextOperation;
        if (next == null) {
            return Progress.completed();
        } else {
            return next.getProgress();
        }
    }
}