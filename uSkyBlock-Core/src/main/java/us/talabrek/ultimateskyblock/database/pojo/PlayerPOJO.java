package us.talabrek.ultimateskyblock.database.pojo;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import java.util.List;
import java.util.UUID;

/**
 * Contains cached information from Bukkit reg. the player.
 */
@Entity
@Table(name = "usb_player")
public class PlayerPOJO {
    /**
     * The id of the player.
     */
    @Id
    private UUID id;

    /**
     * The last known name of the player
     */
    @Column(unique = true)
    private String name;

    /**
     * The last known displayname of the player.
     */
    @Column
    private String displayName;

    /**
     * Timestamp of last time the player visited a skyworld.
     */
    @Column
    private Long lastActiveInSky;

    @OneToMany(mappedBy = "player", cascade = CascadeType.ALL)
    private List<OldNamePOJO> names;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Long getLastActiveInSky() {
        return lastActiveInSky;
    }

    public void setLastActiveInSky(Long lastActiveInSky) {
        this.lastActiveInSky = lastActiveInSky;
    }

    public List<OldNamePOJO> getNames() {
        return names;
    }

    public void setNames(List<OldNamePOJO> names) {
        this.names = names;
    }

    @Override
    public String toString() {
        return "PlayerPOJO{" +
                "id=" + id +
                ", name='" + name + '\'' +
                '}';
    }
}
