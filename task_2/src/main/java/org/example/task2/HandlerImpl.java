package org.example.task2;

import org.example.task2.model.Address;
import org.example.task2.model.Event;
import org.example.task2.model.Result;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

public class HandlerImpl implements Handler {

    private final Client client;
    private final ScheduledExecutorService executorService
            = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

    public HandlerImpl(Client client) {
        this.client = client;
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(1);
    }

    @Override
    public void performOperation() {
        Event event = client.readData();

        Map<Address, Future<Result>> futureResults = new HashMap<>();
        for (Address address : event.recipients()) {
            Future<Result> resultFuture = executorService.submit(() -> client.sendData(address, event.payload()));
            futureResults.put(
                    address,
                    resultFuture
            );
        }

        while (!futureResults.isEmpty()) {
            for (Map.Entry<Address, Future<Result>> e : futureResults.entrySet()) {

                if (!e.getValue().isDone()) {
                    continue;
                }

                futureResults.remove(e.getKey());

                Result result;
                try {
                    result = e.getValue().get();
                } catch (InterruptedException | ExecutionException ex) {
                    throw new RuntimeException(ex);
                }

                if (result == Result.REJECTED) {
                    ScheduledFuture<Result> scheduledFuture = executorService.schedule(
                            () -> client.sendData(e.getKey(), event.payload()),
                            timeout().toMillis(),
                            TimeUnit.MILLISECONDS
                    );

                    futureResults.put(
                            e.getKey(),
                            scheduledFuture
                    );
                }
            }
        }
    }
}
