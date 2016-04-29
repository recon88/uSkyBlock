package us.talabrek.ultimateskyblock.database.pojo;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

/**
 * The alternative names known for players.
 */
@Entity
@Table(name = "usb_oldnames", uniqueConstraints = {@UniqueConstraint(columnNames = {"player_id", "name"})})
public class OldNamePOJO {
    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "player_id", nullable = false)
    PlayerPOJO player;

    @Column
    String name;

    public PlayerPOJO getPlayer() {
        return player;
    }

    public void setPlayer(PlayerPOJO player) {
        this.player = player;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
