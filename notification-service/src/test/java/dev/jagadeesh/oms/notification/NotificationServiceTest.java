package dev.jagadeesh.oms.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationServiceTest {

    @Autowired NotificationService service;
    @Autowired ObjectMapper json;
    @Autowired MockMvc mvc;
    @MockitoSpyBean EmailSender email;

    @Test
    void confirmedOrderSendsOneEmailEvenIfTheEventComesTwice() throws Exception {
        String customer = "cust-" + UUID.randomUUID();
        var event = json.readTree("{\"orderId\":\"o-1\",\"customerId\":\"" + customer + "\",\"total\":2499}");

        assertThat(service.handle("evt-1-" + customer, "OrderConfirmed", event)).isTrue();
        assertThat(service.handle("evt-1-" + customer, "OrderConfirmed", event)).isFalse();

        verify(email, times(1)).send(eq(customer), eq("Order confirmed"), anyString());
    }

    @Test
    void customerSeesOnlyTheirOwnNotifications() throws Exception {
        String me = "cust-" + UUID.randomUUID();
        service.handle("evt-a-" + me, "OrderCancelled", json.readTree("{\"orderId\":\"o-2\",\"customerId\":\"" + me + "\",\"reason\":\"Not enough stock\"}"));
        service.handle("evt-b-" + me, "OrderConfirmed", json.readTree("{\"orderId\":\"o-3\",\"customerId\":\"someone-else\",\"total\":10}"));

        mvc.perform(get("/api/notifications").with(jwt().jwt(j -> j.subject(me))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].body").value("Your order o-2 was cancelled: Not enough stock."));
    }

    @Test
    void eventsItDoesNotCareAboutAreSkipped() throws Exception {
        assertThat(service.handle("evt-x", "OrderCreated", json.readTree("{\"orderId\":\"o-4\",\"customerId\":\"c\"}"))).isFalse();
    }
}
