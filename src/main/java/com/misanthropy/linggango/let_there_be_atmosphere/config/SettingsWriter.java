package com.misanthropy.linggango.let_there_be_atmosphere.config;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SettingsWriter {
    public interface Task {
        void run() throws Exception;
    }

    private static final Map<String, Task> PENDING = new ConcurrentHashMap<>();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "LTBA settings writer");
        thread.setDaemon(true);
        return thread;
    });

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(SettingsWriter::drain, "LTBA settings flush"));
    }

    private SettingsWriter() {
    }

    public static void submit(String key, Task task) {
        if (PENDING.put(key, task) == null) {
            EXECUTOR.execute(() -> run(key));
        }
    }

    public static void flush() {
        if (PENDING.isEmpty()) {
            return;
        }
        try {
            EXECUTOR.submit(() -> {
            }).get();
        } catch (Exception ignored) {
        }
        drain();
    }

    private static synchronized void run(String key) {
        Task task = PENDING.remove(key);
        if (task == null) {
            return;
        }
        try {
            task.run();
        } catch (Exception ignored) {
        }
    }

    private static void drain() {
        for (String key : List.copyOf(PENDING.keySet())) {
            run(key);
        }
    }
}
