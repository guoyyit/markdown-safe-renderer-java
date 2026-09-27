package com.github.guoyyit.markdownsaferendererjava.web;

import com.github.guoyyit.markdownsaferendererjava.options.RenderOptions;
import com.github.guoyyit.markdownsaferendererjava.service.RenderRequest;
import com.github.guoyyit.markdownsaferendererjava.service.RenderResponse;
import com.github.guoyyit.markdownsaferendererjava.service.RenderService;
import com.github.guoyyit.markdownsaferendererjava.service.RenderStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** REST endpoint for rendering untrusted documents. */
@RestController
@RequestMapping("/api")
public class RenderController {
    private final RenderService service;

    public RenderController(RenderService service) {
        this.service = service;
    }

    @PostMapping("/render")
    public ResponseEntity<RenderDtos.RenderResponseDto> render(
            @RequestBody RenderDtos.RenderRequestDto dto) {
        RenderOptions options;
        try {
            options = RenderDtos.toOptions(dto.options());
        } catch (IllegalArgumentException e) {
            RenderResponse rejected = new RenderResponse(RenderStatus.REJECTED_OPTIONS,
                    null, null, null, List.of(), null, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(RenderDtos.RenderResponseDto.from(rejected));
        }
        RenderRequest request = new RenderRequest(dto.content(), options,
                dto.reservedAnchors() == null ? List.of() : dto.reservedAnchors());
        RenderResponse response = service.render(request);
        HttpStatus status = switch (response.getStatus()) {
            case OK -> HttpStatus.OK;
            case REJECTED_LIMIT -> HttpStatus.UNPROCESSABLE_ENTITY;
            case REJECTED_OPTIONS -> HttpStatus.BAD_REQUEST;
            case FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(RenderDtos.RenderResponseDto.from(response));
    }
}
