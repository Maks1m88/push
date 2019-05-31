package resto.push.configuration;

import org.jetbrains.annotations.NotNull;
import resto.db.ConstructorWithoutArgs;
import resto.db.DataClass;
import resto.db.Guid;
import resto.db.PersistedEntity;
import resto.db.RootCachedEntity;
import resto.db.loaders.PersistedEntityLoader;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Конфигурация подписчика. Создается на основе данных, переданных при подписке.
 * По своей логике неизменяемый пока действует подписка.
 *
 * @see resto.api.v2.push.PushNotificationApiService#subscribe
 */
@DataClass
@RootCachedEntity(loader = PersistedEntityLoader.class)
public class PushSubscriberConfiguration extends PersistedEntity {
    /**
     * Идентификатор подписчика
     */
    @NotNull
    private String subscriberAlias;

    /**
     * End-point подписчика (куда передаем данные)
     */
    @NotNull
    private String subscriberUrl;

    /**
     * Интервал уведомлений о событии (в секундах)
     */
    private int notificationPeriodSec;

    /**
     * Таймаут push-уведомления к подписчику (в миллисекундах)
     */
    private int connectTimeoutMillis;

    /**
     * Формат body запроса-уведомления (по умолчанию json)
     */
    @NotNull
    private String mediaType;

    /**
     * Принудительное отключение подписчика администратором
     */
    private boolean forcedDisabled = false;

    /**
     * Подписка на классы.
     * Если список пустой - подписаны на все.
     */
    @NotNull
    private List<PushSubscriptionEntity> subscriptionEntities;

    /**
     * Срок действия подписки
     */
    @NotNull
    private LocalDateTime expireDateTime;

    /**
     * Статус подписчика
     */
    @NotNull
    private SubscriptionStatus subscriptionStatus;

    @SuppressWarnings("ConstantConditions")
    @ConstructorWithoutArgs
    protected PushSubscriberConfiguration() {
        subscriberAlias = null;
        subscriberUrl = null;
        mediaType = null;
        subscriptionEntities = null;
        expireDateTime = null;
        subscriptionStatus = null;
    }

    public PushSubscriberConfiguration(
        @NotNull String subscriberAlias,
        @NotNull String subscriberUrl,
        int notificationPeriodSec,
        int connectTimeoutMillis,
        @NotNull String mediaType,
        @NotNull List<PushSubscriptionEntity> subscriptionEntities,
        @NotNull LocalDateTime expireDateTime
    ) {
        super(Guid.next());
        this.subscriberAlias = subscriberAlias;
        this.subscriberUrl = subscriberUrl;
        this.mediaType = mediaType;
        this.notificationPeriodSec = notificationPeriodSec;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.subscriptionStatus = notificationPeriodSec == 0
            ? SubscriptionStatus.IMMEDIATELY_LISTENING
            : SubscriptionStatus.PERIODICALLY_LISTENING;
        this.subscriptionEntities = new ArrayList<>(subscriptionEntities);
        this.expireDateTime = expireDateTime;
    }

    public boolean isCanWork() {
        return !forcedDisabled &&
               (subscriptionStatus == SubscriptionStatus.IMMEDIATELY_LISTENING ||
                subscriptionStatus == SubscriptionStatus.PERIODICALLY_LISTENING);
    }

    public boolean isPeriodical() {
        return !forcedDisabled && subscriptionStatus == SubscriptionStatus.PERIODICALLY_LISTENING;
    }

    public boolean isImmediately() {
        return !forcedDisabled && subscriptionStatus == SubscriptionStatus.IMMEDIATELY_LISTENING;
    }

    @NotNull
    public String getSubscriberAlias() {
        return subscriberAlias;
    }

    public void setSubscriberUrl(@NotNull String subscriberUrl) {
        this.subscriberUrl = subscriberUrl;
    }

    @NotNull
    public String getSubscriberUrl() {
        return subscriberUrl;
    }

    @NotNull
    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(@NotNull String mediaType) {
        this.mediaType = mediaType;
    }

    public boolean isForcedDisabled() {
        return forcedDisabled;
    }

    public void setForcedDisabled(boolean forcedDisabled) {
        this.forcedDisabled = forcedDisabled;
    }

    public void setNotificationPeriodSec(int notificationPeriodSec) {
        this.notificationPeriodSec = notificationPeriodSec;
    }

    public int getNotificationPeriodSec() {
        return notificationPeriodSec;
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setSubscriptionEntities(@NotNull List<PushSubscriptionEntity> subscriptionEntities) {
        this.subscriptionEntities.clear();
        this.subscriptionEntities.addAll(subscriptionEntities);
    }

    public List<PushSubscriptionEntity> getSubscriptionEntities() {
        return Collections.unmodifiableList(subscriptionEntities);
    }

    @NotNull
    public SubscriptionStatus getSubscriptionStatus() {
        return subscriptionStatus;
    }

    public void setSubscriptionStatus(@NotNull SubscriptionStatus subscriptionStatus) {
        this.subscriptionStatus = subscriptionStatus;
    }

    @NotNull
    public LocalDateTime getExpireDateTime() {
        return expireDateTime;
    }

    public void setExpireDateTime(@NotNull LocalDateTime expireDateTime) {
        this.expireDateTime = expireDateTime;
    }
}