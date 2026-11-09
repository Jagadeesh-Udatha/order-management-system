package dev.jagadeesh.oms.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean CatalogClient catalog;

    static RequestPostProcessor customer(String id) {
        return jwt().jwt(j -> j.subject(id).claim("roles", List.of("CUSTOMER"))).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    @Test
    void placeReturns202ThenReplayReturns200WithTheSameOrder() throws Exception {
        UUID product = UUID.randomUUID();
        when(catalog.product(any())).thenReturn(Optional.of(new CatalogClient.ProductInfo(product, "Blood pressure monitor", new BigDecimal("1899"), true)));
        String body = "{\"lines\":[{\"productId\":\"" + product + "\",\"quantity\":2}]}";

        String first = mvc.perform(post("/api/orders").with(customer("u-1")).header("Idempotency-Key", "checkout-0001")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.total").value(3798))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(first).get("id").asText();

        mvc.perform(post("/api/orders").with(customer("u-1")).header("Idempotency-Key", "checkout-0001")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void someoneElsesOrderIsNotFound() throws Exception {
        UUID product = UUID.randomUUID();
        when(catalog.product(any())).thenReturn(Optional.of(new CatalogClient.ProductInfo(product, "Router", new BigDecimal("2499"), true)));
        String res = mvc.perform(post("/api/orders").with(customer("owner")).header("Idempotency-Key", "checkout-0002")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lines\":[{\"productId\":\"" + product + "\",\"quantity\":1}]}"))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(res).get("id").asText();

        mvc.perform(get("/api/orders/" + id).with(customer("owner"))).andExpect(status().isOk());
        mvc.perform(get("/api/orders/" + id).with(customer("intruder"))).andExpect(status().isNotFound());
    }

    @Test
    void emptyOrderAndMissingKeyAreRejected() throws Exception {
        mvc.perform(post("/api/orders").with(customer("u-2")).header("Idempotency-Key", "checkout-0003")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lines\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/orders").with(customer("u-2"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lines\":[{\"productId\":\"" + UUID.randomUUID() + "\",\"quantity\":1}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
    }
}
