package com.github.guoyyit.markdownsaferendererjava.tests;

import com.github.guoyyit.markdownsaferendererjava.cache.CacheConfig;
import com.github.guoyyit.markdownsaferendererjava.cache.LruRenderCache;
import com.github.guoyyit.markdownsaferendererjava.limits.ResourceLimits;
import com.github.guoyyit.markdownsaferendererjava.options.InMemoryAuditLog;
import com.github.guoyyit.markdownsaferendererjava.options.OptionsValidator;
import com.github.guoyyit.markdownsaferendererjava.parser.MarkdownParser;
import com.github.guoyyit.markdownsaferendererjava.render.HtmlRenderer;
import com.github.guoyyit.markdownsaferendererjava.sanitize.HtmlSanitizer;
import com.github.guoyyit.markdownsaferendererjava.sanitize.SanitizerPolicy;
import com.github.guoyyit.markdownsaferendererjava.service.CachedRender;
import com.github.guoyyit.markdownsaferendererjava.service.RenderService;
import com.github.guoyyit.markdownsaferendererjava.toc.HeadingNumbering;
import com.github.guoyyit.markdownsaferendererjava.web.RenderController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** REST API tests: status codes and response shape. */
class RenderControllerTest {

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        RenderService service = new RenderService(new MarkdownParser(), new HeadingNumbering(),
                new HtmlRenderer(new HtmlSanitizer(SanitizerPolicy.restrictive())),
                new LruRenderCache<CachedRender>(CacheConfig.defaults()),
                new ResourceLimits(1000, 64, 200, 50, 100, 3_000),
                new OptionsValidator(), new InMemoryAuditLog());
        mvc = MockMvcBuilders.standaloneSetup(new RenderController(service)).build();
    }

    @Test
    void rendersDocumentOverHttp() throws Exception {
        mvc.perform(post("/api/render")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"# Hi\\n\\ntext\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.html").isString())
                .andExpect(jsonPath("$.toc.children[0].title").value("Hi"))
                .andExpect(jsonPath("$.document.type").value("DOCUMENT"));
    }

    @Test
    void limitRejectionMapsTo422() throws Exception {
        String longContent = "x".repeat(2000);
        mvc.perform(post("/api/render")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"" + longContent + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("REJECTED_LIMIT"))
                .andExpect(jsonPath("$.rejectionReason").value("INPUT_TOO_LONG"));
    }

    @Test
    void unknownOptionRejectedMapsTo400() throws Exception {
        mvc.perform(post("/api/render")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"# A\", \"options\": "
                                + "{\"version\": 1, \"values\": {\"bogus\": true}, "
                                + "\"unknownPolicy\": \"REJECT\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("REJECTED_OPTIONS"));
    }

    @Test
    void unsupportedVersionMapsTo400() throws Exception {
        mvc.perform(post("/api/render")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"# A\", \"options\": {\"version\": 99}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("REJECTED_OPTIONS"));
    }

    @Test
    void parseErrorsSurfacedInResponse() throws Exception {
        mvc.perform(post("/api/render")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"```\\nunclosed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors[0].severity").value("ERROR"))
                .andExpect(jsonPath("$.errors[0].line").value(1))
                .andExpect(jsonPath("$.errors[0].skippedReason").isString());
    }
}
