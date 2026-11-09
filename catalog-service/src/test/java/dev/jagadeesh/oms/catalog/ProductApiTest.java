package dev.jagadeesh.oms.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductApiTest {

    @Autowired MockMvc mvc;

    @Test
    void adminCreatesProductAnyoneSearchesIt() throws Exception {
        mvc.perform(post("/api/products").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"MED-500\",\"name\":\"Paracetamol 500mg strip\",\"category\":\"pharmacy\",\"price\":35.50,\"stock\":200}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/products").param("q", "paracetamol").param("category", "pharmacy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("MED-500"))
                .andExpect(jsonPath("$.content[0].available").value(200));
    }

    @Test
    void customerCannotCreateProducts() throws Exception {
        mvc.perform(post("/api/products").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"X-1\",\"name\":\"X\",\"category\":\"misc\",\"price\":1,\"stock\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void duplicateSkuIsAConflict() throws Exception {
        String body = "{\"sku\":\"DUP-1\",\"name\":\"Cable\",\"category\":\"electronics\",\"price\":199,\"stock\":10}";
        var admin = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        mvc.perform(post("/api/products").with(admin).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        mvc.perform(post("/api/products").with(admin).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SKU_TAKEN"));
    }
}
