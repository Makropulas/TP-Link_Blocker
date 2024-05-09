package ru.dmalomoshin.tplink_blocker.domain;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Entity
@Table(name = "devices")
public class Device {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(nullable = false,
            name = "name")
    private String name;

    @Column(nullable = false,
            name = "mac_address",
            unique = true)
    private String macAddress;

    /**
     * Состояние устройства (заблокировано или нет).
     * По умолчанию - доступно
     */
    @Column(nullable = false,
            name = "state")
    private boolean state = true;

    /**
     * Индекс устройства в списке узлов роутера
     * По умолчанию индекс -1
     */
    @Column(name = "host_index")
    private int indexHosts = -1;

    /**
     * Индекс устройства в списке правил роутера
     * По умолчанию индекс -1
     */
    @Column(name = "rule_index")
    private int indexRules = -1;


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Device device = (Device) o;
        return this.macAddress.equals(((Device) o).macAddress);
    }

}
