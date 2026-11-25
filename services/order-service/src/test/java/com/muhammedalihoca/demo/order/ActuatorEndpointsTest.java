package com.muhammedalihoca.demo.order;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

// Uygulamanın tamamını (H2 ile) ayağa kaldırıp /api/info ve Actuator probe uçlarını doğrular.
class ActuatorEndpointsTest {

	@Nested
	@SpringBootTest
	@AutoConfigureMockMvc
	@ActiveProfiles("test")
	@DisplayName("Normal sürüm")
	class Healthy {

		@Autowired
		private MockMvc mockMvc;

		@Test
		@DisplayName("GET /api/info servis adını ve varsayılan 'local' sürümünü döner")
		void info() throws Exception {
			// given: APP_VERSION verilmedi

			// when / then
			mockMvc.perform(get("/api/info"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.service").value("order-service"))
					.andExpect(jsonPath("$.version").value("local"));
		}

		@Test
		@DisplayName("Liveness ve readiness UP döner, Prometheus metrikleri açıktır")
		void probesUp() throws Exception {
			// given: DEMO_BROKEN verilmedi

			// when / then
			mockMvc.perform(get("/actuator/health/liveness"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("UP"));
			mockMvc.perform(get("/actuator/health/readiness"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("UP"));
			mockMvc.perform(get("/actuator/prometheus"))
					.andExpect(status().isOk());
		}
	}

	@Nested
	@SpringBootTest(properties = "demo.broken=true")
	@AutoConfigureMockMvc
	@ActiveProfiles("test")
	@DisplayName("Bozuk sürüm (DEMO_BROKEN=true)")
	class Broken {

		@Autowired
		private MockMvc mockMvc;

		@Test
		@DisplayName("Readiness DOWN (503) döner, liveness UP kalır")
		void readinessDown() throws Exception {
			// given: demo.broken=true

			// when / then
			mockMvc.perform(get("/actuator/health/readiness"))
					.andExpect(status().isServiceUnavailable())
					.andExpect(jsonPath("$.status").value("DOWN"));
			mockMvc.perform(get("/actuator/health/liveness"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("UP"));
		}
	}
}
