package resto.push.statistic;

import org.jetbrains.annotations.NotNull;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

/**
 * Статистика изменений в разрезе одного класса
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class ChangeStatisticItem {

    /**
     * Имя класса
     *
     * @see resto.db.metadata.ClassResolver#getName(Class)
     * @see <a href='https://wiki.iiko.ru/pages/viewpage.action?pageId=63414583'>Push-уведомления</a>
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

    public ChangeStatisticItem(@NotNull String entityClassName) {
        this.entityClassName = entityClassName;
    }

    public void incCreated() {
        created++;
    }

    public void incUpdated() {
        updated++;
    }

    public void incDeleted() {
        deleted++;
    }

    public void append(@NotNull ChangeStatisticItem changeStatisticItem) {
        created += changeStatisticItem.created;
        updated += changeStatisticItem.updated;
        deleted += changeStatisticItem.deleted;
    }

    @NotNull
    public String getEntityClassName() {
        return entityClassName;
    }

    public int getCreated() {
        return created;
    }

    public int getUpdated() {
        return updated;
    }

    public int getDeleted() {
        return deleted;
    }

    @Override
    public String toString() {
        return "ChangeStatisticItem@" + System.identityHashCode(this) + '{' +
                "entityClassName: " + entityClassName +
                ", created: " + created +
                ", updated: " + updated +
                ", deleted: " + deleted +
                '}';
    }
}