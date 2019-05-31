package resto.push.dto;

import org.jetbrains.annotations.NotNull;
import resto.push.statistic.ChangeStatisticItem;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

/**
 * Dto для передачи статистики в push-уведомлении
 * Является частью API сервера. Но для избежания циклической зависимости между модулями расположен тут.
 *
 * @see <a href='https://wiki.iiko.ru/pages/viewpage.action?pageId=63414583'>Push-уведомления</a>
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class ChangeStatisticItemDto {
    /**
     * Имя класса
     */
    @XmlElement
    @NotNull
    private String entityClassName;

    /**
     * Количство созданных объектов
     */
    @XmlElement
    private int created;

    /**
     * Количество обновленных объектов
     */
    @XmlElement
    private int updated;

    /**
     * Количество удаленных объектов
     */
    @XmlElement
    private int deleted;

    public ChangeStatisticItemDto(@NotNull String className, int created, int updated, int deleted) {
        this.entityClassName = className;
        this.created = created;
        this.updated = updated;
        this.deleted = deleted;
    }

    public static ChangeStatisticItemDto toDto(ChangeStatisticItem statisticItem) {
        return new ChangeStatisticItemDto(statisticItem.getEntityClassName(), statisticItem.getCreated(),
                statisticItem.getUpdated(), statisticItem.getDeleted());
    }
}