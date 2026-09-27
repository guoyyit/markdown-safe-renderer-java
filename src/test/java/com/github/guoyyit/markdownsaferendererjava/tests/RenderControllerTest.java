package com.github.guoyyit.markdownsaferendererjava.tests;

import com.github.guoyyit.markdownsaferendererjava.Application;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
class RenderControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void rendersDocumentOverHttp() throws Exception {
        mvc.perform(post("/api/render")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"# Hello\\n\\nworld [l](https://e.com)","options":{}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.html").value(org.hamcrest.Matchers.containsString("<h1")))
                .andExpect(jsonPath("$.toc[0].anchor").isNotEmpty())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void oversizedPayloadReturnsDistinctReasonAnd413() throws Exception {
        String big = "x".repeat(2_000_000);
        mvc.perform(post("/api/render")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + big + "\"}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.reason").value("INPUT_TOO_LONG"));
    }

    @Test
    void unknownOptionRejectedWith400() throws Exception {
        mvc.perform(post("/api/render")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"# x","options":{"unknownFutureThing":true}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("INVALID_OPTIONS"));
    }

    @Test
    void unknownOptionIgnoredWhenConfigured() throws Exception {
        mvc.perform(post("/api/render")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"# x","options":{"unknownFutureThing":true,"unknownOptionHandling":"IGNORE"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.html").isString());
    }

    @Test
    void dangerousScriptIsNeutralizedOverHttp() throws Exception {
        mvc.perform(post("/api/render")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"<script>alert(1)</script>"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.html",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<script>"))));
    }

    @Test
    void auditEndpointRecordsEventsAndCacheCanBeInvalidated() throws Exception {
        mvc.perform(post("/api/render")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"# x","options":{"headingNumbers":false}}
                                """))
                .andExpect(status().isOk());
        mvc.perform(get("/api/render/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.kind=='OPTION_SWITCHED')]").isNotEmpty());
        mvc.perform(delete("/api/render/cache"))
                .andExpect(status().isNoContent());
    }
}
