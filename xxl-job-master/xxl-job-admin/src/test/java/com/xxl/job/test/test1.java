package com.xxl.job.test;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;


public class test1 {

    @SneakyThrows
    public static void main(String[] args) {
        //动态获取服务器核数
        int processors = Runtime.getRuntime().availableProcessors();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                processors+1, // 核心线程个数 io:2n ,cpu: n+1  n:内核数据
                processors+1,
                0,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy()
        );

        CompletableFuture<String> future01 = CompletableFuture.supplyAsync(() -> "任务1", executor);
        CompletableFuture<String> future02 = CompletableFuture.supplyAsync(() -> "任务2", executor);
        CompletableFuture<String> future03 = CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return "任务3";
        }, executor);

        // 串联起若干个线程任务, 没有返回值
        CompletableFuture<Void> all = CompletableFuture.allOf(future01, future02, future03);
        // join() 和 get() 都会阻塞主线程，直到所有任务完成。
        System.out.println(all.join()); // 会抛出未经检查的异常，不会强制开发者处理异常
        System.out.println(all.get());  // 会抛出检查异常，需要开发者处理

        executor.shutdown();

    }

}
