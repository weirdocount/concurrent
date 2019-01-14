package com.weirdo.concurrent.basics.chapter13;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class MyThreadPool extends Thread {

    private final int INIT_SIZE = 5;

    private static volatile int seq = 0;

    private int size;

    private final int queueSize;

    private final static String THREAD_PREFIX = "SIMPLE_THREAD_POOL-";

    private final static ThreadGroup GROUP = new ThreadGroup("Pool_Group");

    private final static LinkedList<Runnable> TASK_QUEUE = new LinkedList<>();

    private final static List<MyThreadPool.WorkerTask> THREAD_QUEUE = new ArrayList<>();

    private volatile boolean destroy = false;

    private final DiscardPolicy discardPolicy;


    public MyThreadPool(int queueSize, DiscardPolicy discardPolicy) {
        this.queueSize = queueSize;
        this.discardPolicy = discardPolicy;
    }

    public final static SimpleThreadPool.DiscardPolicy DEFAULT_DISCARD_POLICY = () -> {
        throw new SimpleThreadPool.DiscardException("Discard This Task.");
    };

    public interface DiscardPolicy {

        void discard() throws SimpleThreadPool.DiscardException;
    }

    private enum TaskState {
        FREE, RUNNING, BLOCKED, DEAD
    }

    private static class WorkerTask extends Thread {

        private volatile MyThreadPool.TaskState taskState = MyThreadPool.TaskState.FREE;

        public WorkerTask(ThreadGroup group, String name) {
            super(group, name);
        }

        public MyThreadPool.TaskState getTaskState() {
            return this.taskState;
        }

        @Override
        public void run() {
            OUTER:
            while (this.taskState != MyThreadPool.TaskState.DEAD) {
                Runnable runnable;
                synchronized (TASK_QUEUE) {
                    while (TASK_QUEUE.isEmpty()) {
                        try {
                            taskState = MyThreadPool.TaskState.BLOCKED;
                            TASK_QUEUE.wait();
                        } catch (InterruptedException e) {
                            System.out.println("Closed.");
                            break OUTER;
                        }
                    }
                    runnable = TASK_QUEUE.removeFirst();
                }

                if (runnable != null) {
                    taskState = MyThreadPool.TaskState.RUNNING;
                    runnable.run();
                    taskState = MyThreadPool.TaskState.FREE;
                }
            }
        }

        public void close() {
            this.taskState = MyThreadPool.TaskState.DEAD;
        }
    }

    private void createWorkTask() {
        MyThreadPool.WorkerTask task = new MyThreadPool.WorkerTask(GROUP, THREAD_PREFIX + (seq++));
        task.start();
        THREAD_QUEUE.add(task);
    }

    private void init() {
        for (int i = 0; i < INIT_SIZE; i++) {
            createWorkTask();
        }
        this.size = INIT_SIZE;
        this.start();
    }

    public void submit(Runnable runnable) {
        if (destroy)
            throw new IllegalStateException("The thread pool already destroy and not allow submit task.");

        synchronized (TASK_QUEUE) {
            if (TASK_QUEUE.size() > queueSize)
                discardPolicy.discard();
            TASK_QUEUE.addLast(runnable);
            TASK_QUEUE.notifyAll();
        }
    }

    public void shutdown() throws InterruptedException {
        while (!TASK_QUEUE.isEmpty()) {
            Thread.sleep(50);
        }

        synchronized (THREAD_QUEUE) {
            int initVal = THREAD_QUEUE.size();
            while (initVal > 0) {
                for (MyThreadPool.WorkerTask task : THREAD_QUEUE) {
                    if (task.getTaskState() == MyThreadPool.TaskState.BLOCKED) {
                        task.interrupt();
                        task.close();
                        initVal--;
                    } else {
                        Thread.sleep(10);
                    }
                }
            }
        }

        System.out.println(GROUP.activeCount());

        this.destroy = true;
        System.out.println("The thread pool disposed.");
    }
}
