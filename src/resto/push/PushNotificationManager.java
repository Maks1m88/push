package resto.push;

import org.jetbrains.annotations.Nullable;
import resto.NamedThreadFactory;
import resto.RestoProperties;
import resto.config.ServerComponent;
import resto.core.RuntimeManager;
import resto.core.threadpool.ActivityExecutors;
import resto.db.EntitiesDatabaseSynchronizer;
import resto.db.EntityManager;
import resto.db.FlushTaskListener;
import resto.db.Guid;
import resto.db.metadata.ClassResolver;
import resto.push.configuration.PushSubscriberConfiguration;
import resto.push.statistic.ChangeStatistic;
import resto.push.statistic.ChangeStatisticItem;
import resto.utils.log4j.RestoLogger;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static resto.core.RestoServiceLocator.get;

/**
 * @see <a href="https://wiki.iiko.ru/pages/viewpage.action?pageId=63414583">Менеджер управления подписками на
 * push-уведосления</a>
 * @see "RMS-47410"
 */
public class PushNotificationManager implements ServerComponent {

    private static final RestoLogger LOG = RestoLogger.getLogger(PushNotificationManager.class);

    private static final ExecutorService IMMEDIATELY_POOL =
        ActivityExecutors.newThreadPoolExecutor(2, 10, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(10),
            NamedThreadFactory.multiDaemon("PushImmediate"));

    private static final ScheduledExecutorService SCHEDULED_POOL =
        ActivityExecutors.newScheduledThreadPoolExecutor(2, NamedThreadFactory.multiDaemon("PushScheduled"));

    /**
     * Список notifier'ов
     */
    private final Map<Guid, PushSubscriberNotifier> notifiers = new ConcurrentHashMap<>();

    private EntityManager em;

    private ClassResolver resolver;

    public PushNotificationManager(EntityManager em, ClassResolver resolver) {
        this.em = em;
        this.resolver = resolver;
    }

    @Override
    public void initialize() {
        if (get(RestoProperties.class).isPushManagerEnabled()) {
            new Thread(this::delayedInitialization, "PushNotificationManager#delayedInitialization").start();
        } else {
            LOG.warn("Push notification service is disabled by property 'push-manager-enabled=false'");
        }
    }

    private void delayedInitialization() {
        if (!get(RuntimeManager.class).isStarted()) {
            LOG.info("Waiting for server to start successfully for push notification service");
            boolean isServerStartedSuccessfully = get(RuntimeManager.class).waitStarted();
            if (!isServerStartedSuccessfully) {
                LOG.info("Do not initialize push notification service because the server did not start successfully");
                return;
            }
        }

        em.getAllNotDeletedCopy(PushSubscriberConfiguration.class).forEach(this::subscribe);

        // Подписываемся к процессу сброса данных в БД
        get(EntitiesDatabaseSynchronizer.class).subscribe(getFlushTaskListener());
        LOG.info("Push notification service started successfully");
    }

    private FlushTaskListener getFlushTaskListener() {
        return task -> {
            Map<String, ChangeStatisticItem> statisticHashMap = new HashMap<>();
            // Собираем статистику флаша в разрезе по классам
            task.getEntitiesToCreateIds().forEach(id -> {
                statisticHashMap.computeIfAbsent(
                    resolver.getName(task.getEntity(id).getClass()),
                    ChangeStatisticItem::new
                ).incCreated();
            });
            task.getEntitiesToDeleteIds().forEach(id -> {
                statisticHashMap.computeIfAbsent(
                    resolver.getName(task.getEntity(id).getClass()),
                    ChangeStatisticItem::new
                ).incDeleted();
            });
            task.getEntitiesToUpdateIds().forEach(id -> {
                statisticHashMap.computeIfAbsent(
                    resolver.getName(task.getEntity(id).getClass()),
                    ChangeStatisticItem::new
                ).incUpdated();
            });
            ChangeStatistic statistic = new ChangeStatistic(task.getRevision(), statisticHashMap);
            onCreateStatistic(statistic);
        };
    }

    /**
     * Менеджер предоставляет статистику всем активным notifier'ам.
     * Для пермоментных notifier'ов создаем асинхронную задачу, если они не в процессе уведомления.
     *
     * @param statistic - атомарная статистика одного флаша
     */
    private void onCreateStatistic(ChangeStatistic statistic) {
        notifiers.values().stream()
            .filter(PushSubscriberNotifier::isRunning)
            .forEach(notifier -> {
                // Добавляем статистику в очередь всем уведомителям
                notifier.addStatistic(statistic);
                // Задачи для периодических уведомлений ставятся на этапе создания notifier'a
                if (notifier.isImmediately()) {
                    notifier.onRunAsyncProcessNotify();
                }
            });
    }

    /**
     * Создаем или перезапускаем notifier'a.
     * Создаем задачу для периодического режима уведомления (PERIODICALLY_LISTENING).
     */
    public void subscribe(PushSubscriberConfiguration configuration) {
        if (configuration.isCanWork()) {

            // Создаем notifier'a, если его не существовало или он упал
            PushSubscriberNotifier notifier = notifiers.get(configuration.getId());
            if (notifier == null || !notifier.isRunning()) {
                notifier = new PushSubscriberNotifier(configuration, SCHEDULED_POOL, IMMEDIATELY_POOL);
                notifiers.put(configuration.getId(), notifier);
                if (configuration.isPeriodical()) {
                    notifier.onSchedule();
                }
                LOG.info.format("Subscription created and started for subscriber: %s, id: %s",
                    configuration.getSubscriberAlias(), configuration.getId());
            } else {
                // Если notifier уже работает, перезапускаем с новыми параметрами
                notifier.restart();
                LOG.info.format("Subscription restarted for subscriber: %s, id: %s",
                    configuration.getSubscriberAlias(), configuration.getId());
            }
        } else {
            LOG.warn.format("Notifier can't be started for %s status %s forced disabled %s",
                configuration.getSubscriberAlias(),
                configuration.getSubscriptionStatus(),
                configuration.isForcedDisabled());
        }
    }

    public void unsubscribe(PushSubscriberConfiguration configuration, boolean mayInterruptIfRunning) {
        PushSubscriberNotifier notifier = notifiers.remove(configuration.getId());
        if (notifier != null) {
            notifier.unsubscribe(mayInterruptIfRunning);
        }
    }

    @Nullable
    public Map<Guid, PushSubscriberNotifier> getNotifiers() {
        return Collections.unmodifiableMap(notifiers);
    }
}