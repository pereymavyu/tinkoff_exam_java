package org.example.task1;


import org.example.task1.model.ApplicationStatusResponse;
import org.example.task1.model.Response;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class HandlerImpl implements Handler {

    private static final long TIMEOUT_IN_MILLIS = 15000;
    private static final AtomicInteger FAILED_REQUESTS_NUMBER = new AtomicInteger();

    private final Client client;

    private final ExecutorCompletionService<Response> executorService
            = new ExecutorCompletionService<>(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));

    public HandlerImpl(Client client) {
        this.client = client;
    }

    @Override
    public ApplicationStatusResponse performOperation(String id) {
        long startTime = System.currentTimeMillis();

        List<Future<Response>> futureResponses = new ArrayList<>(2);

        futureResponses.add(executorService.submit(() -> getApplicationStatus(() -> client.getApplicationStatus1(id))));
        futureResponses.add(executorService.submit(() -> getApplicationStatus(() -> client.getApplicationStatus2(id))));

        while (System.currentTimeMillis() < startTime + TIMEOUT_IN_MILLIS) {
            for (Future<Response> e : futureResponses) {
                if (e.isDone()) {
                    try {
                        return map(e.get());
                    } catch (InterruptedException | ExecutionException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        }

        return null;
    }

    private Response getApplicationStatus(Callable<Response> clientCall) {
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
                response = clientCall.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            delay = response instanceof Response.RetryAfter ? ((Response.RetryAfter) response).delay() : null;
        }

        return response;
    }

    private static ApplicationStatusResponse map(Response clientResponse) {

        if (clientResponse instanceof Response.Success successClientResponse) {
            return new ApplicationStatusResponse.Success(
                    successClientResponse.applicationId(),
                    successClientResponse.applicationStatus()
            );
        } else if (clientResponse instanceof Response.Failure) {

            return new ApplicationStatusResponse.Failure(
                    null,
                    FAILED_REQUESTS_NUMBER.incrementAndGet()
            );
        }

        throw new IllegalArgumentException();
    }
}
