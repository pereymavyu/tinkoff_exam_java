package org.example.task1;


import org.example.task1.model.ApplicationStatusResponse;
import org.example.task1.model.Response;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class HandlerImpl implements Handler {

    private static final long TIMEOUT_IN_MILLIS = 15000;
    private static final AtomicInteger FAILED_REQUESTS_NUMBER = new AtomicInteger();

    private static Map<Integer, Function<String, Response>> callByServiceId;

    private final ScheduledExecutorService executorService
            = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

    public HandlerImpl(Client client) {
        callByServiceId = Map.of(
                1, client::getApplicationStatus1,
                2, client::getApplicationStatus2
        );

    }

    @Override
    public ApplicationStatusResponse performOperation(String id) {
        long startTime = System.currentTimeMillis();

        Map<Integer, Future<Response>> futureResponseByServiceId = new HashMap<>(2);
        for (Map.Entry<Integer, Function<String, Response>> e : callByServiceId.entrySet()) {
            futureResponseByServiceId.put(
                    e.getKey(),
                    executorService.submit(() -> getApplicationStatus(id, e.getValue()))
            );
        }

        while (System.currentTimeMillis() < startTime + TIMEOUT_IN_MILLIS) {
            for (Map.Entry<Integer, Future<Response>> e : futureResponseByServiceId.entrySet()) {
                if (!e.getValue().isDone()) {
                    continue;
                }

                Response response = null;
                try {
                    response = e.getValue().get();
                } catch (InterruptedException | ExecutionException ex) {
                    throw new RuntimeException(ex);
                }

                if (response instanceof Response.RetryAfter retryAfterResponse) {
                    executorService.schedule(
                            () -> callByServiceId.get(e.getKey()),
                            retryAfterResponse.delay().toMillis(),
                            TimeUnit.MILLISECONDS
                    );
                } else if (response instanceof Response.Success successClientResponse) {
                    return new ApplicationStatusResponse.Success(
                            successClientResponse.applicationId(),
                            successClientResponse.applicationStatus()
                    );
                } else if (response instanceof Response.Failure) {
                    return new ApplicationStatusResponse.Failure(
                            Duration.ofMillis(System.currentTimeMillis() - startTime),
                            FAILED_REQUESTS_NUMBER.incrementAndGet()
                    );
                }
            }
        }

        return new ApplicationStatusResponse.Failure(
                Duration.ofMillis(System.currentTimeMillis() - startTime),
                FAILED_REQUESTS_NUMBER.incrementAndGet()
        );
    }

    private Response getApplicationStatus(String id, Function<String, Response> responseFromServiceFunction) {
        Response response = null;
        Duration delay = null;

        while (response == null || response instanceof Response.RetryAfter) {
            if (delay != null) {
                try {
                    wait(delay.toMillis());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            try {
                response = responseFromServiceFunction.apply(id);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            delay = response instanceof Response.RetryAfter ? ((Response.RetryAfter) response).delay() : null;
        }

        return response;
    }
}
