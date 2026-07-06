package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "quick_commands")
public class QuickCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 500)
    private String command;


    private String deviceType;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @Column(nullable = false)
    private Boolean enabled = true;
}
