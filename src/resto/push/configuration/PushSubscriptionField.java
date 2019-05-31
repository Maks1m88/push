package resto.push.configuration;

import org.jetbrains.annotations.NotNull;
import resto.db.ConstructorWithoutArgs;
import resto.db.DataClass;

/**
 * Информация о поле сущности, на которую подписались.
 */
@DataClass
public class PushSubscriptionField {
    /**
     * Наименование поля сущности.
     * todo данное поле пока игнорируется и в логике не участвует.
     * todo когда определимся как вытягивать бизнес данные и какие метаданные от подписчика нужны, полностью переделаем
     */
    @NotNull
    private String fieldName;

    @SuppressWarnings("ConstantConditions")
    @ConstructorWithoutArgs
    protected PushSubscriptionField() {
        fieldName = null;
    }

    public PushSubscriptionField(@NotNull String fieldName) {
        this.fieldName = fieldName;
    }

    @NotNull
    public String getFieldName() {
        return fieldName;
    }
}