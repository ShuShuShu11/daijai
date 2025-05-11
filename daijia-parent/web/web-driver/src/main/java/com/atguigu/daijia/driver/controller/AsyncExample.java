package com.atguigu.daijia.driver.controller;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class AsyncExample {
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        // 模拟一个异步任务
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> fetchDataFromNetwork());

        // 对结果进行处理
        future
                .thenApply(data -> processData(data)) // 处理数据
                .thenAccept(result -> printResult(result)); // 打印结果

        // 主线程继续执行其他任务
        System.out.println("Main thread is doing other work...");

        // 等待异步任务完成（仅用于演示）
        Thread.sleep(2000);
    }

    // 模拟从网络获取数据
    private static String fetchDataFromNetwork() {
        try {
            System.out.println("Fetching data from network...");
            Thread.sleep(1000); // 模拟网络延迟
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return "Hello, CompletableFuture!";
    }

    // 模拟数据处理
    private static String processData(String data) {
        System.out.println("Processing data...");
        return data.toUpperCase(); // 转换为大写
    }

    // 模拟打印结果
    private static void printResult(String result) {
        System.out.println("Result: " + result);
    }
}