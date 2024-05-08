package ru.dmalomoshin.tplink_blocker.domain;

import jakarta.persistence.*;
import lombok.Data;

@Data
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
     * Индекс устройства в списке узлов роутера
     */
    @Column(name = "host_index")
    private int indexHosts;

    /**
     * Индекс устройства в списке правил роутера
     */
    @Column(name = "rule_index")
    private int indexRules;

}
