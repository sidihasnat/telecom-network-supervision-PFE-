package com.example.demo.repository;

import com.example.demo.model.MetricData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MetricRepository extends JpaRepository<MetricData, Long> {

    List<MetricData> findTop50ByOrderByTimestampDesc();
    List<MetricData> findTop20ByDeviceNameOrderByTimestampDesc(String deviceName);


    List<MetricData> findByDeviceNameAndTimestampBetweenOrderByTimestampAsc(
            String deviceName, LocalDateTime start, LocalDateTime end);

    java.util.Optional<MetricData> findTop1ByDeviceNameOrderByTimestampDesc(String deviceName);
}