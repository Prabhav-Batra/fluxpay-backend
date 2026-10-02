package com.fluxpay.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    record CreateThing(@NotBlank String displayName) {}

    @RestController
    static class ThrowingController {
        @GetMapping("/not-found")
        void notFound() {
            throw FluxpayException.notFound("THING_NOT_FOUND", "No such thing");
        }

        @GetMapping("/conflict")
        void conflict() {
            throw FluxpayException.conflict("THING_TAKEN", "Taken");
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("secret internals");
        }

        @PostMapping("/things")
        void create(@Valid @RequestBody CreateThing body) {}
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @Test
    void should_return_404_envelope_when_service_throws_not_found() throws Exception {
        mockMvc.perform(get("/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("THING_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("No such thing"))
                .andExpect(jsonPath("$.error.details").isArray());
    }

    @Test
    void should_return_409_when_service_throws_conflict() throws Exception {
        mockMvc.perform(get("/conflict")).andExpect(status().isConflict());
    }

    @Test
    void should_return_422_with_snake_case_field_details_when_body_is_invalid() throws Exception {
        mockMvc.perform(post("/things").contentType(MediaType.APPLICATION_JSON).content("{\"display_name\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[0].field").value("display_name"));
    }

    @Test
    void should_return_400_when_body_is_malformed_json() throws Exception {
        mockMvc.perform(post("/things").contentType(MediaType.APPLICATION_JSON).content("{nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void should_hide_internals_and_return_500_when_unexpected_exception_is_thrown() throws Exception {
        mockMvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value("An unexpected error occurred"));
    }

    @Test
    void should_return_415_when_content_type_is_not_json() throws Exception {
        mockMvc.perform(post("/things").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }
}
