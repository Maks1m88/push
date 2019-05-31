package resto.push.configuration;

import resto.db.DataClass;
import resto.localization.enums.LocalizableName;

/**
 * Статус подписчика
 *
 * @see PushSubscriberConfiguration#subscriptionStatus
 */
@DataClass
public enum SubscriptionStatus implements LocalizableName {
    // Подписчик отписался "Подписчик отписался"
    UNSUBSCRIBED,

    // Истек срок подписки "Подписки истекла"
    SUBSCRIPTION_EXPIRED,

    // Подписан на периодические уведомления "Периодические уведомления"
    PERIODICALLY_LISTENING,

    // Подписан на уведомления в реальном времени "Немедленные уведомления"
    IMMEDIATELY_LISTENING,

    //
    ;
}