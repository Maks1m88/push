package resto.push.dto;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import resto.push.configuration.PushSubscriberConfiguration;
import resto.push.statistic.ChangeStatistic;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Dto для передачи статистики в push-уведомлении.
 * Является частью API сервера. Но для избежания циклической зависимости между модулями расположен тут.
 *
 * @see <a href='https://wiki.iiko.ru/pages/viewpage.action?pageId=63414583'>Push-уведомления</a>
 */
@XmlAccessorType(XmlAccessType.NONE)
public class ChangeStatisticDto {
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
    private Map<String, ChangeStatisticItemDto> classStatistics;

    public ChangeStatisticDto(
        int revisionFrom,
        int revisionTo,
        @NotNull UUID configurationId,
        @NotNull UUID instanceId,
        @NotNull Map<String, ChangeStatisticItemDto> classStatistics
    ) {
        this.revisionFrom = revisionFrom;
        this.revisionTo = revisionTo;
        this.configurationId = configurationId;
        this.instanceId = instanceId;
        this.classStatistics = new HashMap<>(classStatistics);
    }

    public static ChangeStatisticDto toDto(ChangeStatistic statistic) {
        Map<String, ChangeStatisticItemDto> items = new HashMap<>();
        statistic.getClassStatistics().forEach((className, statisticItem) -> items.put(className,
            ChangeStatisticItemDto.toDto(statisticItem)));
        return new ChangeStatisticDto(statistic.getRevisionFrom(), statistic.getRevisionTo(),
            statistic.getConfigurationId(), statistic.getInstanceId(), items);
    }
}