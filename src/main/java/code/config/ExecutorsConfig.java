package code.config;

import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.internal.guava.ThreadFactoryBuilder;

import java.util.concurrent.*;

@Slf4j
public class ExecutorsConfig {

    private static ThreadFactory threadFactory = new ThreadFactoryBuilder().setDaemon(false).setNameFormat("handle-pool-%d").build();

    private static ExecutorService fixedThreadPool = new ThreadPoolExecutor(
            5,
            20,
            60,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(200),
            threadFactory,
            (Runnable r, ThreadPoolExecutor executor) -> {
                log.error(r.toString() + " is Rejected");
            }
    );

    public static void submit(Runnable task) {
        fixedThreadPool.submit(task);
    }

}
