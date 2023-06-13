package io.github.linyimin0812.profiler.core.container;

import io.github.linyimin0812.profiler.common.logger.LogFactory;
import io.github.linyimin0812.profiler.common.ui.BeanInitResult;
import io.github.linyimin0812.profiler.common.ui.StartupVO;
import io.github.linyimin0812.profiler.common.ui.Statistics;
import io.github.linyimin0812.profiler.common.utils.NameUtil;
import io.github.linyimin0812.profiler.common.utils.OSUtil;
import io.github.linyimin0812.profiler.core.http.SimpleHttpServer;
import io.github.linyimin0812.profiler.core.monitor.StartupMonitor;
import io.github.linyimin0812.profiler.api.EventListener;
import io.github.linyimin0812.profiler.api.Lifecycle;
import io.github.linyimin0812.profiler.core.monitor.check.AppStatusCheckService;
import org.picocontainer.MutablePicoContainer;
import org.picocontainer.PicoBuilder;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author linyimin
 * @date 2023/04/13 11:45
 * @description 生命周期实例容器，由容器统一控制
 **/
public class IocContainer {

    private static final Logger startupLogger = LogFactory.getStartupLogger();

    private static final MutablePicoContainer container = new PicoBuilder().withSetterInjection().withCaching().withLifecycle().build();

    private static final AtomicBoolean started = new AtomicBoolean();

    private static final AtomicBoolean stopped = new AtomicBoolean();

    private static long startTimeMillis = 0;

    private static long numOfBean = 0;

    /**
     * 启动服务容器
     */
    public static void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        container.start();

        for (Lifecycle lifecycle : ServiceLoader.load(Lifecycle.class, IocContainer.class.getClassLoader())) {
            container.addComponent(lifecycle);
        }

        for (EventListener listener : ServiceLoader.load(EventListener.class, IocContainer.class.getClassLoader())) {
            container.addComponent(listener);
        }

        for (AppStatusCheckService service : ServiceLoader.load(AppStatusCheckService.class, StartupMonitor.class.getClassLoader())) {
            service.init();
            container.addComponent(service);
        }

        SimpleHttpServer.start();

        numOfBean = 0;
        startTimeMillis = System.currentTimeMillis();

    }

    /**
     * 停止容器
     */
    public static void stop() {

        if (!stopped.compareAndSet(false, true)) {
            startupLogger.warn("IocContainer has stopped.");
            return;
        }

        double startupDuration = (System.currentTimeMillis() - startTimeMillis) / 1000D;

        container.dispose();

        StartupVO.addStatistics(new Statistics(0, "Startup Time(s)", String.format("%.2f", startupDuration)));
        acquireNumOfBean(StartupVO.getBeanInitResultList());
        StartupVO.addStatistics(new Statistics(1, "Num of Bean", String.valueOf(numOfBean)));

        writeStartupVOToHtml();

        String prompt = String.format("======= spring-startup-analyzer finished, click %s to visit details. ======", SimpleHttpServer.endpoint());

        startupLogger.info(prompt);
        System.out.println(prompt);

    }

    private static void writeStartupVOToHtml() {
        try {
            Path analyzerPath = Paths.get(OSUtil.home() + "template" + File.separator + "startup-analysis.html");
            String content = new String(Files.readAllBytes(analyzerPath), StandardCharsets.UTF_8);
            content = content.replace("/*startupVO:*/{}", StartupVO.toJSONString());

            content = content.replace("/*flameGraphUrl*/''", "'./" + NameUtil.getFlameGraphHtmlName() + "'");

            String path = NameUtil.getOutputPath() + NameUtil.getAnalysisHtmlName();

            try (FileWriter writer = new FileWriter(path)) {
                writer.write(content);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T getComponent(Class<T> componentType) {
        return container.getComponent(componentType);
    }

    public static <T> List<T> getComponents(Class<T> componentType) {
        return container.getComponents(componentType);
    }

    private static void acquireNumOfBean(List<BeanInitResult> beanInitResultList) {
        numOfBean += beanInitResultList.size();
        for (BeanInitResult beanInitResult : beanInitResultList) {
            acquireNumOfBean(beanInitResult.getChildren());
        }
    }
}