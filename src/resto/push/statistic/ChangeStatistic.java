package resto.push.statistic;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import resto.db.Guid;
import resto.push.configuration.PushSubscriberConfiguration;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Статистика изменений между ревизиями
 */
@XmlAccessorType(XmlAccessType.NONE)
public class ChangeStatistic {
    /**
     * Номер ревизии
     */
    @XmlElement
    private int revisionFrom;

    /**
     * Номер ревизии
     */
    @XmlElement
    private int revisionTo;

    /**
     * id конфигурации, по которой была составлена статистика
     *
     * @see PushSubscriberConfiguration
     */
    @XmlElement
    @Nullable
    private UUID configurationId;

    /**
     * Id запуска сервера
     *
     * @see resto.RestoEnvironment#instanceId
     */
    @XmlElement
    private UUID instanceId;

    /**
     * Статистика в разрезе по классам
     */
    @XmlElement
    @NotNull
    private Map<String, ChangeStatisticItem> classStatistics;

    public ChangeStatistic(@NotNull Guid configurationId, @NotNull Guid instanceId) {
        this.configurationId = Guid.toUUID(configurationId);
        this.instanceId = Guid.toUUID(instanceId);
        this.classStatistics = new HashMap<>();
    }

    public ChangeStatistic(int revisionTo, @NotNull Map<String, ChangeStatisticItem> classStatistics) {
        this.revisionTo = revisionTo;
        this.classStatistics = new HashMap<>(classStatistics);
    }

    public void append(@NotNull ChangeStatisticItem statisticItem) {
        classStatistics.computeIfAbsent(statisticItem.getEntityClassName(),
            className -> new ChangeStatisticItem(statisticItem.getEntityClassName()))
            .append(statisticItem);
    }

    @NotNull
    public Map<String, ChangeStatisticItem> getClassStatistics() {
        return Collections.unmodifiableMap(classStatistics);
    }

    public int getRevisionFrom() {
        return revisionFrom;
    }

    public void setRevisionFrom(int revisionFrom) {
        this.revisionFrom = revisionFrom;
    }

    public int getRevisionTo() {
        return revisionTo;
    }

    public void setRevisionTo(int revisionTo) {
        this.revisionTo = revisionTo;
    }

    @Nullable
    public UUID getConfigurationId() {
        return configurationId;
    }

    public UUID getInstanceId() {
        return instanceId;
    }

    @Override
    public String toString() {
        return "ChangeStatistic@" + System.identityHashCode(this) + '{' +
               "classes: " + classStatistics.size() +
               ", revisionTo: " + revisionTo + '}';
    }
}