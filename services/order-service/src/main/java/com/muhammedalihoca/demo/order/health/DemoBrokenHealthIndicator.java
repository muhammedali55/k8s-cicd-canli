package com.muhammedalihoca.demo.order.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

// Demo için "bozuk sürüm" anahtarı: DEMO_BROKEN=true ise DOWN döner.
// Yalnızca readiness grubuna eklenir (application.yml); böylece K8s pod'u trafikten çıkarır ama yeniden başlatmaz.
// Bean adı "demoBrokenHealthIndicator" olduğu için Actuator'da "demoBroken" adıyla görünür.
@Component
public class DemoBrokenHealthIndicator implements HealthIndicator {

	private final boolean broken;

	public DemoBrokenHealthIndicator(@Value("${demo.broken:false}") boolean broken) {
		this.broken = broken;
	}

	@Override
	public Health health() {
		if (broken) {
			return Health.down().withDetail("reason", "DEMO_BROKEN=true (bilerek bozuk sürüm)").build();
		}
		return Health.up().build();
	}
}
