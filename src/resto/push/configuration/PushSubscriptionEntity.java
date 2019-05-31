package resto.push.configuration;

import org.jetbrains.annotations.NotNull;
import resto.db.ConstructorWithoutArgs;
import resto.db.DataClass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Информация о подписанной сущности.
 */
@DataClass
public class PushSubscriptionEntity {
    /**
     * Наименование сущности
     */
    @NotNull
    private String entityClassName;

    /**
     * Список полей.
     */
    @NotNull
    private List<PushSubscriptionField> fields;

    @SuppressWarnings("ConstantConditions")
    @ConstructorWithoutArgs
    public PushSubscriptionEntity() {
        entityClassName = null;
        fields = null;
    }

    public PushSubscriptionEntity(
        @NotNull String entityClassName, @NotNull List<PushSubscriptionField> fields
    ) {
        this.entityClassName = entityClassName;
        this.fields = new ArrayList<>(fields);
    }

    @NotNull
    public String getEntityClassName() {
        return entityClassName;
    }

    @NotNull
    public List<PushSubscriptionField> getFields() {
        return Collections.unmodifiableList(fields);
    }
}