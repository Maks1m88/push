package resto.push;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hibernate.annotations.AccessType;
import org.hibernate.annotations.Type;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import resto.db.ConstructorWithoutArgs;
import resto.db.DataClass;
import resto.db.DefaultNull;
import resto.db.Guid;
import resto.db.RootCachedEntity;
import resto.db.TableIndex;
import resto.db.TableIndexes;
import resto.db.hibernate.Property;
import resto.db.hibernate.type.ReferenceEntityType;
import resto.db.loaders.NonCachingHibernateEntityLoader;
import resto.event.RestoEvent;
import resto.push.configuration.PushSubscriberConfiguration;

import javax.persistence.Entity;
import java.util.Date;

/**
 * Эвент, содержащий информацию об ошибках push-уведомлений.
 */
@Entity
@DataClass(convert = false)
@AccessType("field")
@TableIndexes({
    @TableIndex(columnNames = {"date"}, clustered = true),
})
@RootCachedEntity(loader = NonCachingHibernateEntityLoader.class)
public class PushNotificationEvent extends RestoEvent {

    public static final Property<PushSubscriberConfiguration> CONFIGURATION_ID =
        new Property<>("configuration");

    @Type(type = ReferenceEntityType.TYPE)
    @NotNull
    private PushSubscriberConfiguration configuration;

    private int revisionFrom;

    private int revisionTo;

    /**
     * Сообщение о событии
     */
    @NotNull
    private String message;

    /**
     * stacktrace, если возникла ошибка
     */
    @DefaultNull
    @Type(type = "text")
    @Nullable
    private String stacktrace;

    @SuppressWarnings("ConstantConditions")
    @ConstructorWithoutArgs
    protected PushNotificationEvent() {
        message = null;
        configuration = null;
    }

    public PushNotificationEvent(
        @NotNull PushSubscriberConfiguration configuration,
        int revisionFrom,
        int revisionTo,
        @NotNull String message
    ) {
        super(Guid.next(), new Date());
        this.configuration = configuration;
        this.revisionFrom = revisionFrom;
        this.revisionTo = revisionTo;
        this.message = message;
    }

    public PushNotificationEvent(
        @NotNull PushSubscriberConfiguration configuration,
        int revisionFrom,
        int revisionTo,
        @NotNull Throwable t
    ) {
        super(Guid.next(), new Date());
        this.configuration = configuration;
        this.revisionFrom = revisionFrom;
        this.revisionTo = revisionTo;
        this.message = t.getMessage();
        this.stacktrace = ExceptionUtils.getStackTrace(t);
    }

    @Override
    public int getItemsCount() {
        return 0;
    }

    @NotNull
    public PushSubscriberConfiguration getConfiguration() {
        return configuration;
    }

    public int getRevisionFrom() {
        return revisionFrom;
    }

    public int getRevisionTo() {
        return revisionTo;
    }

    @NotNull
    public String getMessage() {
        return message;
    }

    public void setMessage(@NotNull String message) {
        this.message = message;
    }

    @Nullable
    public String getStacktrace() {
        return stacktrace;
    }

    public void setStacktrace(@Nullable String stacktrace) {
        this.stacktrace = stacktrace;
    }
}