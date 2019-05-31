package resto.push.configuration;

import com.google.common.collect.Ordering;
import org.jetbrains.annotations.Nullable;
import resto.db.AbstractMemoryEntityManager;
import resto.db.Entity;
import resto.db.Guid;
import resto.index.DefaultIndexedView;
import resto.index.IndexHashingStrategy;

/**
 * Индекс для конфигураций подписчиков по subscriberAlias. Должен быть уникальным.
 */
public class PushSubscriberConfigurationDao {

    private final DefaultIndexedView<PushSubscriberConfiguration, String, Guid, Guid> indexBySubscriberAlias =
        new DefaultIndexedView<>(
            "PushSubscriberConfiguration_indexBySubscriberAlias",
            PushSubscriberConfiguration::getSubscriberAlias,
            IndexHashingStrategy.EQUALITY,
            configuration -> true,
            Entity.FUNCTION_ID,
            Ordering.natural(),
            Entity.FUNCTION_ID,
            Ordering.natural(),
            true);

    public PushSubscriberConfigurationDao(AbstractMemoryEntityManager entityManager) {
        entityManager.addIndexedView(PushSubscriberConfiguration.class, indexBySubscriberAlias);
    }

    @Nullable
    public PushSubscriberConfiguration getConfigurationByAlias(String subscriberAlias) {
        return indexBySubscriberAlias.getUniqueElement(subscriberAlias);
    }
}