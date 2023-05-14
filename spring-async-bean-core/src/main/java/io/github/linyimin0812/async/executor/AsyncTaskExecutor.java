package io.github.linyimin0812.async.executor;

import io.github.linyimin0812.async.config.AsyncConfig;
import io.github.linyimin0812.profiler.common.logger.LogFactory;
import org.slf4j.Logger;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * @author yiminlin
 * @date 2023/05/14 00:56
 **/
public class AsyncTaskExecutor {

    private static final Logger logger = LogFactory.getStartupLogger();

    private static ThreadPoolExecutor threadPool;

    private static boolean finished = false;

    private static final List<Future<?>> futureList = new ArrayList<>();


    public static void submitTask(Runnable runnable) {
        if (threadPool == null) {
            threadPool = createThreadPoolExecutor();
        }

        futureList.add(threadPool.submit(runnable));
    }

    public static void ensureAsyncTasksFinish() {

        if (futureList.isEmpty()) {
            return;
        }

        for (Future<?> future : futureList) {
            try {
                future.get();
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        finished = true;

        futureList.clear();

        threadPool.shutdown();

    }

    public static boolean isFinished() {
        return finished;
    }

    private static ThreadPoolExecutor createThreadPoolExecutor() {
        int threadPoolCoreSize = AsyncConfig.getInstance().getAsyncBeanProperties().getAsyncInitBeanThreadPoolCoreSize();

        int threadPollMaxSize = AsyncConfig.getInstance().getAsyncBeanProperties().getAsyncInitBeanThreadPoolMaxSize();

        logger.info("create async-init-bean thread pool, corePoolSize: {}, maxPoolSize: {}.", threadPoolCoreSize, threadPoolCoreSize);

        NamedThreadFactory threadFactory = new NamedThreadFactory("async-init-bean");

        return new ThreadPoolExecutor(threadPoolCoreSize, threadPollMaxSize, 30, TimeUnit.SECONDS, new SynchronousQueue<>(),  threadFactory, new ThreadPoolExecutor.CallerRunsPolicy());

    }
}