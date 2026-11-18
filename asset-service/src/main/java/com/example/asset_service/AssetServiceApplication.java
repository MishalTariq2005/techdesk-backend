package com.example.asset_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * BUG FIX (discovered while wiring up 6.1's SLA jobs in ticket-service):
 * @Scheduled methods do nothing unless @EnableScheduling is present
 * SOMEWHERE in the application context — it was missing from every service
 * in this project. WarrantyExpiryJob has had a working @Scheduled(cron=...)
 * method since 5.1 that has never actually fired, silently, with no error at
 * startup or anywhere else. Worth checking notification-service's
 * SseHeartbeatScheduler too if heartbeats ever seem to stop working.
 */
@SpringBootApplication
@EnableScheduling
public class AssetServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AssetServiceApplication.class, args);
	}

}
