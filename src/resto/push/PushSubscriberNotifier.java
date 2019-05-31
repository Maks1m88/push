package resto.push;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import org.glassfish.jersey.client.ClientProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import resto.RestoEnvironment;
import resto.RestoProperties;
import resto.db.EntityManager;
import resto.db.hibernate.HibernateSession;
import resto.db.metadata.ClassResolver;
import resto.db.metadata.ClassesRegistry;
import resto.db.revisions.RevisionDao;
import resto.push.configuration.PushSubscriberConfiguration;
import resto.push.configuration.PushSubscriptionEntity;
import resto.push.configuration.SubscriptionStatus;
import resto.push.dto.ChangeStatisticDto;
import resto.push.statistic.ChangeStatistic;
import resto.utils.log4j.RestoLogger;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static resto.core.RestoServiceLocator.get;

/**
 * Отправитель. Подгатавливает пакет данных и отправляет подписчику.
 * Задача по отправке уведомления ставится в пул потоков для асинхронной обработки данных.
 *
 * @see <a href="https://wiki.iiko.ru/pages/viewpage.action?pageId=63414583">Отправитель push-уведосления</a>
 * @see "RMS-47410"
 */
public class PushSubscriberNotifier {

    private static final RestoLogger LOG = RestoLogger.getLogger(PushSubscriberNotifier.class);

    /**
     * Конфиг подписчика
     */
    @NotNull
    private PushSubscriberConfiguration configuration;

    /**
     * Статус уведомителя
     */
    @NotNull
    private volatile NotifierStatus status;

    /**
     * Последняя успешно переданная ревизия
     */
    private int lastRevisionTo;

    /**
     * Очередь изменений FIFO
     */
    @NotNull
    private ConcurrentLinkedQueue<ChangeStatistic> statistics;

    /**
     * Собранная из очереди статистика, готовая к отправке потребителю
     */
    @NotNull
    private ChangeStatistic currentStatistic;

    /**
     * Номер текущей попытки отправить сообщение
     */
    private int currentAttempt;

    /**
     * Текущий интервал между попытками (в минутах)
     */
    private int currentAttemptIntervalMinutes;

    /**
     * Предыдущий интервал между попытками (в минутах)
     */
    private int previousAttemptIntervalMinutes;

    /**
     * Признак что для nootifier'a уже существует задача
     */
    private AtomicBoolean busy;

    /**
     * Пул выполнения задач по отправке уведомления
     */
    @NotNull
    private ExecutorService immediatelyPool;

    /**
     * Пул для создания периодических уведомлений.
     */
    @NotNull
    private ScheduledExecutorService schedulePool;

    /**
     * Ссылка на периодически выполняемую задачу для уведомлений по расписанию.
     */
    @Nullable
    private ScheduledFuture<?> scheduledFuture;

    /**
     * Подписка на классы с учетом иерархии.
     * Если множество пустое - подписаны на все.
     */
    private Set<String> subscriptionClasses;

    public PushSubscriberNotifier(
        @NotNull PushSubscriberConfiguration configuration,
        @NotNull ScheduledExecutorService schedulePool,
        @NotNull ExecutorService immediatelyPool
    ) {
        this.configuration = configuration;
        this.schedulePool = schedulePool;
        this.immediatelyPool = immediatelyPool;
        this.statistics = new ConcurrentLinkedQueue<>();
        this.subscriptionClasses = getSubclasses(this.configuration.getSubscriptionEntities());
        this.status = NotifierStatus.RUNNING;
        this.busy = new AtomicBoolean(false);
        this.currentStatistic = new ChangeStatistic(configuration.getId(), get(RestoEnvironment.class).getInstanceId());
        this.currentAttemptIntervalMinutes = 1;
    }

    /**
     * Добавляем статистику в конец очереди FIFO
     *
     * @param statistic - статистика измененных данных
     */
    void addStatistic(ChangeStatistic statistic) {
        if (statistic != null) {
            statistics.offer(statistic);
        }
    }

    /**
     * Для PERIODICALLY_LISTENING запускаем периодически выполняемую задачу, которая по расписанию создает задачи
     * уведомления
     */
    public void onSchedule() {
        scheduledFuture = schedulePool.scheduleAtFixedRate(
            this::onRunAsyncProcessNotify,
            configuration.getNotificationPeriodSec(),
            configuration.getNotificationPeriodSec(),
            TimeUnit.SECONDS);
    }

    /**
     * Управляем запуском асинхронной задачи для отправки уведомления
     */
    public void onRunAsyncProcessNotify() {
        if (isRunning()) {
            // Если notifier еще не завершил предыдущую попытку, то не создаем уведомление
            if (busy.compareAndSet(false, true)) {
                CompletableFuture.runAsync(this::processing, immediatelyPool)
                    .exceptionally((throwable) -> {
                        onException(throwable);
                        return null;
                    });
            } else {
                LOG.debug.format("Notifier started but busy for subscriber: %s", configuration.getSubscriberAlias());
            }
        } else {
            // Если предыдущая попытка завершилась провалом, а notifier стал нерабочим, отменяем периодическую задачу
            if (scheduledFuture != null) {
                scheduledFuture.cancel(true);
                LOG.info.format("Notifications aborted for subscriber: %s, notifier status: %s",
                    configuration.getSubscriberAlias(), status);
            }
        }
    }

    /**
     * Процесс попытки уведомления подписчика
     */
    public void processing() {
        LOG.debug.format("Start process notifier for subscriber: %s", configuration.getSubscriberAlias());
        // Если нас отключили принудительно, то ничего не делаем и завершаем задачу
        if (configuration.isForcedDisabled()) {
            LOG.debug.format("Subscriber forced disabled for subscriber: %s", configuration.getSubscriberAlias());
            status = NotifierStatus.STOPPED;
            busy.set(false);
            return;
        }
        // Проверяем срок действия подписки
        if (checkSubscriptionExpired()) {
            LOG.warn.format("Subscription expired for subscriber: %s", configuration.getSubscriberAlias());
            status = NotifierStatus.STOPPED;
            busy.set(false);
            return;
        }
        // Если очередь уже пуста и нет статистики для повторной отправки, то ничего не делаем и завершаем задачу
        if (statistics.isEmpty() && currentStatistic.getClassStatistics().isEmpty()) {
            LOG.debug.format("No data to push message for subscriber: %s", configuration.getSubscriberAlias());
            busy.set(false);
            return;
        }

        // Собираем данные
        currentStatistic = collectStatistics();

        // Отправляем данные
        if (pushMessage(currentStatistic)) {
            // При успешном выполнении запоминаем переданную ревизию
            lastRevisionTo = currentStatistic.getRevisionTo();
            // Инитиализируем переменные
            init();
        } else {
            // Проверяем не исчерпали мы попыток или notifier'a не отключили пока ждали таймаут
            currentAttempt++;
            int maxTryAttempts = get(RestoProperties.class).getPushNotificationMaxTryAttempts();
            if (currentAttempt < maxTryAttempts && configuration.isCanWork()) {
                int period = getNextAttemptIntervalMinutes();
                LOG.info.format("Retry push message for subscriber: %s, attempt: %s, timeout minutes: %s",
                    configuration.getSubscriberAlias(), currentAttempt, period);
                // При неудачной попытки отправить статистику ставим задачу с новой задержкой
                schedulePool.schedule(this::processing, period, TimeUnit.MINUTES);
            } else {
                status = NotifierStatus.STOPPED;
            }
        }
    }

    /**
     * Отправка уведомления подписчику.
     *
     * @param statistic - данные изменений
     *
     * @return true - если успешно уведомил
     */
    private boolean pushMessage(ChangeStatistic statistic) {
        if (statistic.getClassStatistics().isEmpty()) {
            LOG.debug.format("No data to push message after filtering for subscriber: %s",
                configuration.getSubscriberAlias());
            return true;
        }
        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(configuration.getSubscriberUrl())
            .property(ClientProperties.CONNECT_TIMEOUT, configuration.getConnectTimeoutMillis())
            .property(ClientProperties.READ_TIMEOUT, get(RestoProperties.class).getPushNotificationReadTimeoutMillis());
        Entity<ChangeStatisticDto> requestBody = Entity.entity(ChangeStatisticDto.toDto(statistic),
            configuration.getMediaType());
        try {
            LOG.debug.format("Try push message for subscriber: %s", configuration.getSubscriberAlias());
            Response response = target.request().buildPost(requestBody).invoke();
            if (response.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL) {
                // Если пришел http-response, то читаем статус из стандартизированного body
                PushNotificationResponseDto responseDto = response.readEntity(PushNotificationResponseDto.class);
                switch (responseDto.result) {
                    // Если пришло сообщение об успешном уведомлении, логируем его
                    case "SUCCESS":
                        LOG.debug.format("Successful push message for subscriber %s",
                            configuration.getSubscriberAlias());
                        return true;
                    // Если пришло сообщение об ошибке, то логируем событие, но продолжаем уведомлять
                    case "ERROR":
                        LOG.info.format("Notifier received ERROR response from subscriber: %s, with message: %s",
                            configuration.getSubscriberAlias(), responseDto.message);
                        createEvent(responseDto.message, statistic);
                        return true;
                    // Если пришло сообщение об остановке подписки или неизвестный статус, то логируем событие и
                    // прекращаем подписку
                    case "STOP":
                    default:
                        LOG.info.format("Notifier received %s response from subscriber: %s, with message: %s",
                            responseDto.result, configuration.getSubscriberAlias(), responseDto.message);
                        createEvent(responseDto.message, statistic);
                        unsubscribe(true);
                        return true;
                }
            } else {
                LOG.error.format("Bad http response from subscriber %s, code: %s, reason: %s, body: %s",
                    configuration.getSubscriberAlias(),
                    response.getStatusInfo().getStatusCode(),
                    response.getStatusInfo().getReasonPhrase(),
                    response.readEntity(String.class));
                createEvent(response.getStatusInfo().getReasonPhrase(), statistic);
            }
        } catch (ProcessingException e) {
            LOG.error.format(e, "Error occurred while processing subscriber %s", configuration.getSubscriberAlias());
            createEvent(e, statistic);
        }
        return false;
    }

    /**
     * Формируем пакет данных. Выгребаем всю очередь и филтруем классы.
     */
    private ChangeStatistic collectStatistics() {
        // Устанавливаем ревизии изменений в пакете данных
        int revisionTo = HibernateSession.exec(() -> get(RevisionDao.class).getMaxExportableRevision());
        currentStatistic.setRevisionFrom(lastRevisionTo);
        currentStatistic.setRevisionTo(revisionTo);
        ChangeStatistic statisticOnStack;
        while ((statisticOnStack = statistics.poll()) != null) {
            statisticOnStack.getClassStatistics().entrySet().stream()
                .filter(entry -> subscriptionClasses.isEmpty() || subscriptionClasses.contains(entry.getKey()))
                .forEach(entry -> currentStatistic.append(entry.getValue()));
        }
        return currentStatistic;
    }

    /**
     * Вычиляет период между попыткам отправить уведомление. Элементы числовой последовательности Фибоначчи.
     * Но каждый период не должен превышать максимум (по умолчанию 20 минут), заданного в настройках сервера, т.к.
     * слишком большие периоды нам не нужны.
     * <p>
     * Номер попытки (таймаут в минутах):
     * <p>
     * 1(1) 2(1) 3(2) 4(3) 5(5) 6(8) 7(13)  8(20)  9(20) 10(20) 11(20) и т.д.
     *
     * @return таймаут ожидания в минутах
     */
    private int getNextAttemptIntervalMinutes() {
        int timeout = currentAttemptIntervalMinutes;
        currentAttemptIntervalMinutes += previousAttemptIntervalMinutes;
        previousAttemptIntervalMinutes = timeout;
        int maxAttemptPeriod = get(RestoProperties.class).getPushNotificationMaxAttemptPeriodMinutes();
        return currentAttemptIntervalMinutes > maxAttemptPeriod
            ? maxAttemptPeriod
            : currentAttemptIntervalMinutes;
    }

    /**
     * Метод получает всех наследников классов из конфига.
     *
     * @param classes - список классов из конфига
     *
     * @return - множество всех классов, включая иерархию
     */
    private Set<String> getSubclasses(
        List<PushSubscriptionEntity> classes
    ) {
        ClassesRegistry classesRegistry = get(ClassesRegistry.class);
        ClassResolver classResolver = get(ClassResolver.class);
        return classes.stream()
            .map(PushSubscriptionEntity::getEntityClassName)
            .map(classResolver::forName)
            .map(classesRegistry::getSubClasses)
            .flatMap(Collection::stream)
            .distinct()
            .filter(cls -> !Modifier.isAbstract(cls.getModifiers()))
            .map(classResolver::getName)
            .collect(Collectors.toSet());
    }

    private void createEvent(Throwable t, ChangeStatistic statistic) {
        HibernateSession.execAndUpdateRevision(() -> {
            PushNotificationEvent event =
                new PushNotificationEvent(configuration, statistic.getRevisionFrom(), statistic.getRevisionTo(), t);
            HibernateSession.get().save(event);
        });
    }

    private void createEvent(String message, ChangeStatistic statistic) {
        HibernateSession.execAndUpdateRevision(() -> {
            PushNotificationEvent event =
                new PushNotificationEvent(configuration, statistic.getRevisionFrom(), statistic.getRevisionTo(),
                    message);
            HibernateSession.get().save(event);
        });
    }

    public void onException(Throwable t) {
        status = NotifierStatus.STOPPED;
        busy.set(false);
        LOG.error.format(t, "Error occurred while processing subscriber %s", configuration.getSubscriberAlias());
        createEvent(t, currentStatistic);
    }

    /**
     * Обновляем статус, флаг и подписанные классы в случае переподписки
     */
    public void restart() {
        status = NotifierStatus.RUNNING;
        subscriptionClasses = getSubclasses(configuration.getSubscriptionEntities());
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
            scheduledFuture = null;
        }
        currentAttempt = 0;
        previousAttemptIntervalMinutes = 0;
        currentAttemptIntervalMinutes = 1;
        if (isPeriodical()) {
            onSchedule();
        }
    }

    /**
     * Остановка отправки уведомления
     *
     * @param mayInterruptIfRunning - принудительно прервать текущую попытку отправки уведомления
     */
    public void unsubscribe(boolean mayInterruptIfRunning) {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(mayInterruptIfRunning);
            scheduledFuture = null;
        }
        status = configuration.isForcedDisabled()
            ? NotifierStatus.FORCED_DISABLED
            : NotifierStatus.STOPPED;
        LOG.info.format("Subscription stopped for subscriber: %s, id: %s",
            configuration.getSubscriberAlias(), configuration.getId());
    }

    /**
     * Проверяем срок действия подписки.
     * Если истекла, завершаем работу.
     */
    private boolean checkSubscriptionExpired() {
        if (LocalDateTime.now().isAfter(configuration.getExpireDateTime())) {
            status = NotifierStatus.STOPPED;
            busy.set(false);
            get(EntityManager.class).runTransacted(() -> {
                configuration.updating();
                configuration.setSubscriptionStatus(SubscriptionStatus.SUBSCRIPTION_EXPIRED);
                configuration.update();
            });
            return true;
        }
        return false;
    }

    /**
     * Инициализируем переменные для отправки сообщения
     */
    private void init() {
        currentStatistic = new ChangeStatistic(configuration.getId(), get(RestoEnvironment.class).getInstanceId());
        currentAttempt = 0;
        previousAttemptIntervalMinutes = 0;
        currentAttemptIntervalMinutes = 1;
        busy.set(false);
    }

    public boolean isImmediately() {
        return configuration.isImmediately();
    }

    public boolean isPeriodical() {
        return configuration.isPeriodical();
    }

    public boolean isRunning() {
        return status == NotifierStatus.RUNNING;
    }

    @NotNull
    public PushSubscriberConfiguration getConfiguration() {
        return configuration;
    }

    public boolean isBusy() {
        return busy.get();
    }

    @NotNull
    public NotifierStatus getStatus() {
        return status;
    }

    public void setStatus(@NotNull NotifierStatus status) {
        this.status = status;
    }

    public int getLastRevisionTo() {
        return lastRevisionTo;
    }

    public int getCurrentAttempt() {
        return currentAttempt;
    }

    public int getCurrentAttemptIntervalMinutes() {
        return currentAttemptIntervalMinutes;
    }

    @XmlAccessorType(XmlAccessType.NONE)
    @JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        setterVisibility = JsonAutoDetect.Visibility.NONE
    )
    private static class PushNotificationResponseDto {

        @XmlElement
        private String result;

        @XmlElement
        private String message;

        public PushNotificationResponseDto() {
        }
    }
}