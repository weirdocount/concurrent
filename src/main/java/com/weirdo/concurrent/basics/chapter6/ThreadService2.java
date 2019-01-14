package com.weirdo.concurrent.basics.chapter6;

public class ThreadService2 {

    private Thread thread;

    private boolean isFinished = false;

    public void excute(Thread t) {
        thread = new Thread() {
            @Override
            public void run() {
                Thread runner = new Thread(t);
                runner.setDaemon(true);
                try {
                    runner.join();
                    isFinished = true;
                } catch (InterruptedException e) {
//                    e.printStackTrace();
                }
            }
        };
        thread.start();
    }

    public void shutdown(long mills) {
        long currentTimeMillis = System.currentTimeMillis();
        while (!isFinished) {
            if (System.currentTimeMillis() - currentTimeMillis >= mills) {
                System.out.println("任务超时，需要结束他!");
                thread.interrupt();
                break;
            }
            try {
                thread.sleep(1);
            } catch (InterruptedException e) {
                System.out.println("执行线程被打断!");
                break;
            }
        }
        isFinished = false;
    }
}
