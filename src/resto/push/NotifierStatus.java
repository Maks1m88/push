package resto.push;

import resto.localization.enums.LocalizableName;

/**
 * Статус уведомителя
 *
 * @see resto.push.PushSubscriberNotifier#status
 */
public enum NotifierStatus implements LocalizableName {
    // Подписка запущена "Уведомления запущены"
    RUNNING,

    // Подписка остановлена подписчиком "Уведомления остановлены"
    STOPPED,

    // Подписка принудительно остановлена администратором "Уведомления принудительно остановлены"
    FORCED_DISABLED,

    //
    ;
}