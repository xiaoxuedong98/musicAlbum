package com.cs6650.album.client.csv;


import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class Client3 {

    private static final int INIT_THREADS = 10;
    private static final int INIT_REQUESTS_PER_THREAD = 100;
    private static final int REQUESTS_PER_THREAD = 1000;
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final Gson GSON = new Gson();
    private static AtomicInteger TOTAL_REQ = new AtomicInteger(0);
    private static AtomicInteger SUCCESS_REQ = new AtomicInteger(0);
    private static AtomicInteger FAILED_REQ = new AtomicInteger(0);

    private static ConcurrentHashMap<Integer, AtomicInteger> throughputPerSecond = new ConcurrentHashMap<>();
    private static long globalStartTime;

    private static List<RequestRecord> records = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) throws InterruptedException, IOException {
        if (args.length < 4) {
            System.out.println("Usage: Client <threadGroupSize> <numThreadGroups> <delay> <IPAddr>");
            return;
        }

        int threadGroupSize = Integer.parseInt(args[0]);
        int numThreadGroups = Integer.parseInt(args[1]);
        int delay = Integer.parseInt(args[2]);
        String serverURI = args[3];

        // Initialization phase
        runThreads(INIT_THREADS, INIT_REQUESTS_PER_THREAD, serverURI);

        globalStartTime = System.currentTimeMillis();

        for (int i = 0; i < numThreadGroups; i++) {
            runThreads(threadGroupSize, REQUESTS_PER_THREAD, serverURI);
            TimeUnit.SECONDS.sleep(delay);
        }

        long endTime = System.currentTimeMillis();

        long wallTime = (endTime - globalStartTime) / 1000;
        long totalRequests = ((long) numThreadGroups * threadGroupSize * REQUESTS_PER_THREAD) * 2;

        double throughput = (double) totalRequests / wallTime;

        System.out.println("Wall Time: " + wallTime + " seconds");
        System.out.println("Throughput: " + throughput + " requests/second");
//        System.out.println("Expected Total request: " + totalRequests);
        System.out.println("Number of Total requests: "+ TOTAL_REQ);
        System.out.println("Number of Success requests: "+ SUCCESS_REQ);
        System.out.println("Number of Failure requests: "+ FAILED_REQ);

        calculateAndDisplayStatistics();
        writeToCSV("results.csv");
        writeToThroughputCSV("throughput.csv");
    }

    private static void calculateAndDisplayStatistics() {
        List<Long> postLatencies = new ArrayList<>();
        List<Long> getLatencies = new ArrayList<>();

        for (RequestRecord record : records) {
            if ("POST".equals(record.method)) {
                postLatencies.add(record.latency);
            } else {
                getLatencies.add(record.latency);
            }
        }

        System.out.println("POST Statistics:");
        displayStatistics(postLatencies);

        System.out.println("GET Statistics:");
        displayStatistics(getLatencies);
    }

    private static void runThreads(int numThreads, int requestsPerThread, String serverURI) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        sendRequest(serverURI + "/albums", "POST", null);
                        sendRequest(serverURI + "/albums", "GET", "1");
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
    }

    private static void displayStatistics(List<Long> latencies) {
        Collections.sort(latencies);
        long sum = 0;
        for (long latency : latencies) {
            sum += latency;
        }
        double mean = sum / (double) latencies.size();
        double median = latencies.size() % 2 == 0 ?
                (latencies.get(latencies.size() / 2 - 1) + latencies.get(latencies.size() / 2)) / 2.0 :
                latencies.get(latencies.size() / 2);
        long p99 = latencies.get((int) (latencies.size() * 0.99));
        long min = latencies.get(0);
        long max = latencies.get(latencies.size() - 1);

        System.out.println("Mean: " + mean);
        System.out.println("Median: " + median);
        System.out.println("P99: " + p99);
        System.out.println("Min: " + min);
        System.out.println("Max: " + max);
    }

    private static void writeToCSV(String filename) throws IOException {
        FileWriter csvWriter = new FileWriter(filename);
        csvWriter.append("StartTime,Method,Latency,ResponseCode\n");
        for (RequestRecord record : records) {
            csvWriter.append(String.format("%d,%s,%d,%d\n", record.startTime, record.method, record.latency, record.responseCode));
        }
        csvWriter.flush();
        csvWriter.close();
    }

    private static void writeToThroughputCSV(String filename) throws IOException {
        FileWriter csvWriter = new FileWriter(filename);
        csvWriter.append("Second,Throughput\n");
        for (Map.Entry<Integer, AtomicInteger> entry : throughputPerSecond.entrySet()) {
            csvWriter.append(String.format("%d,%d\n", entry.getKey(), entry.getValue().get()));
        }
        csvWriter.flush();
        csvWriter.close();
    }

    private static void sendRequest(String urlString, String method, String albumID) {
        int retries = 0;
        boolean success = false;

        while (retries < 5 && !success) {
            long startTime = System.currentTimeMillis();
            try {
                int responseCode;
                int count = TOTAL_REQ.getAndIncrement();
//                System.out.println(count);
                if ("POST".equals(method)) {
                    responseCode = sendPostRequest(urlString);
                } else {  // For the GET method
                    responseCode = sendGetRequest(urlString + "/" + albumID);
                }

                long endTime = System.currentTimeMillis();
                records.add(new RequestRecord(startTime, method, endTime - startTime, responseCode));

                // Record throughput
                int second = (int) ((endTime - globalStartTime) / 1000);
                throughputPerSecond.computeIfAbsent(second, k -> new AtomicInteger(0)).incrementAndGet();

                if (responseCode == 200 || responseCode == 201) {
                    success = true;
                    SUCCESS_REQ.getAndIncrement();
                } else {
                    retries++;
                    System.out.println("Retry..." + retries);
                    FAILED_REQ.getAndIncrement();
                }

            } catch (Exception e) {
                retries++;
                System.err.println("Error sending request: " + e.getMessage());
            }
        }
    }

    private static int sendGetRequest(String urlString) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(urlString))
                .GET()
                .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        return response.statusCode();
    }

    private static int sendPostRequest(String urlString) throws Exception {
        String boundary = Long.toHexString(System.currentTimeMillis()); // Just generate some unique random value.

        var bytesOutput = new ByteArrayOutputStream();
        var writer = new PrintWriter(bytesOutput, true, StandardCharsets.UTF_8);

        // Add image part
        writer.append("--").append(boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"image\"; filename=\"nmtb.png\"").append("\r\n");
        writer.append("Content-Type: image/png").append("\r\n");
        writer.append("\r\n").flush();
        Files.copy(Paths.get("./nmtb.png"), bytesOutput);
        bytesOutput.write("\r\n".getBytes(StandardCharsets.UTF_8));

        // Add JSON profile part using Gson
        writer.append("--").append(boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"profile\"").append("\r\n");
        writer.append("Content-Type: application/json; charset=UTF-8").append("\r\n");
        writer.append("\r\n");
        Map<String, String> profile = new HashMap<>();
        profile.put("artist", "Some Artist");
        profile.put("title", "Some Title");
        profile.put("year", "2023");
        writer.append(GSON.toJson(profile));
        writer.append("\r\n").flush();

        writer.append("--").append(boundary).append("--").append("\r\n").flush();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(urlString))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytesOutput.toByteArray()))
                .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        return response.statusCode();
    }

    private static class RequestRecord {
        long startTime;
        String method;
        long latency;
        int responseCode;

        RequestRecord(long startTime, String method, long latency, int responseCode) {
            this.startTime = startTime;
            this.method = method;
            this.latency = latency;
            this.responseCode = responseCode;
        }
    }
}

