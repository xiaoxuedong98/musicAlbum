package com.cs6650.album.client.csv.client1;

import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.*;
import java.io.FileWriter;
import java.io.IOException;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;

import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.*;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;

public class Client1 {

    private static final int INIT_THREADS = 10;
    private static final int INIT_REQUESTS_PER_THREAD = 100;
    private static final int REQUESTS_PER_THREAD = 1000;
    //    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    public static final PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
    private static final CloseableHttpClient POOLED_CLIENT;
    private static final Gson GSON = new Gson();
    private static AtomicInteger successRequests = new AtomicInteger(0);
    private static AtomicInteger failedRequests = new AtomicInteger(0);

    private static ConcurrentHashMap<Integer, AtomicInteger> throughputPerSecond = new ConcurrentHashMap<>();
    private static long globalStartTime;
    private static final byte[] IMAGE_CONTENT;

    static {
        connectionManager.setMaxTotal(100);
        connectionManager.setDefaultMaxPerRoute(100);
        connectionManager.setValidateAfterInactivity(60000);
        POOLED_CLIENT = HttpClients.custom().setConnectionManager(connectionManager).disableAutomaticRetries().build();

        byte[] tempContent;
        try {
            tempContent = Files.readAllBytes(Paths.get("./nmtb.png"));
        } catch (IOException e) {
            e.printStackTrace();
            tempContent = new byte[0];
        }
        IMAGE_CONTENT = tempContent;
    }

    private static List<RequestRecord> records = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) throws InterruptedException {
        if (args.length < 4) {
            System.out.println("Usage: Client <threadGroupSize> <numThreadGroups> <delay> <IPAddr>");
            return;
        }

        int threadGroupSize = Integer.parseInt(args[0]);
        int numThreadGroups = Integer.parseInt(args[1]);
        int delay = Integer.parseInt(args[2]);
        String serverURI = args[3];

        // Initialization phase
        CountDownLatch latch1 = new CountDownLatch(1);
        runThreads(INIT_THREADS, INIT_REQUESTS_PER_THREAD, serverURI, latch1);
        latch1.await();

        globalStartTime = System.currentTimeMillis();
        CountDownLatch latch2 = new CountDownLatch(numThreadGroups * threadGroupSize);
        for (int i = 0; i < numThreadGroups; i++) {
            runThreads(threadGroupSize, REQUESTS_PER_THREAD, serverURI, latch2);
            TimeUnit.SECONDS.sleep(delay);
        }
        latch2.await();

        long endTime = System.currentTimeMillis();

        long wallTime = (endTime - globalStartTime) / 1000;
        long totalRequests = (long) INIT_THREADS * INIT_REQUESTS_PER_THREAD +
            (long) numThreadGroups * threadGroupSize * REQUESTS_PER_THREAD * 2;

        double throughput = (double) totalRequests / wallTime;

        System.out.println("Wall Time: " + wallTime + " seconds");
        System.out.println("Throughput: " + throughput + " requests/second");
        System.out.println("Success Requests: " + successRequests.get());
        System.out.println("Failed Requests: " + failedRequests.get());
    }
    private static void runThreads(int numThreads, int requestsPerThread, String serverURI, CountDownLatch latch) {
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                for (int j = 0; j < requestsPerThread; j++) {
                    sendRequest(serverURI + "/albums", "POST", null);
                    sendRequest(serverURI + "/albums", "GET", "1");
                }
                latch.countDown();
            });
        }
        executor.shutdown();
    }
    private static void sendRequest(String urlString, String method, String albumID) {
        int retries = 0;
        boolean success = false;

        while (retries < 5 && !success) {
            long startTime = System.currentTimeMillis();
            try {
                int responseCode;
                if ("POST".equals(method)) {
                    responseCode = sendPostRequest(urlString);
                } else {  // For the GET method
                    responseCode = sendGetRequest(urlString + "/" + albumID);
                }

                long endTime = System.currentTimeMillis();
                long latency = endTime - startTime;
                records.add(new RequestRecord(startTime, method, latency, responseCode));

                // Record throughput
                int second = (int) ((endTime - globalStartTime) / 1000);
                throughputPerSecond.computeIfAbsent(second, k -> new AtomicInteger(0)).incrementAndGet();

                if (responseCode == 200) {
                    success = true;
                    successRequests.incrementAndGet();
//                    System.out.println("Success: " + method + " " + urlString + " " + successRequests.get() + " latency: " + latency);
                } else {
                    retries++;
                    System.out.println("Failed: " + method + " " + urlString + " " + responseCode + " " + retries + " latency: " + latency);
                    failedRequests.incrementAndGet();
                }

            } catch (Exception e) {
                retries++;
                failedRequests.incrementAndGet();
                System.err.println("Error sending " + method + "request: " + e.getMessage());
            }
        }
    }

    private static int sendGetRequest(String urlString) throws Exception {
        HttpGet httpGet = new HttpGet(urlString);
//        long getStartTime = System.currentTimeMillis();
        CloseableHttpResponse response = POOLED_CLIENT.execute(httpGet);
//        long getEndTime = System.currentTimeMillis();
//        long getLatency = getEndTime - getStartTime;
//        System.out.println("GET latency: " + getLatency);
        try {
            HttpEntity entity1 = response.getEntity();
            EntityUtils.consume(entity1);
            return response.getStatusLine().getStatusCode();
        } finally {
            response.close();
        }
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
//        Files.copy(Paths.get("./nmtb.png"), bytesOutput);
        bytesOutput.write(IMAGE_CONTENT);
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

        HttpPost httpPost = new HttpPost(urlString);
        httpPost.setHeader("Content-Type", "multipart/form-data; boundary=" + boundary);
        httpPost.setEntity(new ByteArrayEntity(bytesOutput.toByteArray()));

//        long postStartTime = System.currentTimeMillis();
        CloseableHttpResponse response = POOLED_CLIENT.execute(httpPost);
//        long postEndTime = System.currentTimeMillis();
//        long postLatency = postEndTime - postStartTime;
//        System.out.println("POST latency: " + postLatency);
        try {
            HttpEntity entity1 = response.getEntity();
            EntityUtils.consume(entity1);
            return response.getStatusLine().getStatusCode();
        } finally {
            response.close();
        }
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


