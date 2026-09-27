package com.github.guoyyit.markdownsaferendererjava.web;

import com.github.guoyyit.markdownsaferendererjava.ast.Heading;
import com.github.guoyyit.markdownsaferendererjava.ast.Node;
import com.github.guoyyit.markdownsaferendererjava.ast.Text;
import com.github.guoyyit.markdownsaferendererjava.options.OptionsVersion;
import com.github.guoyyit.markdownsaferendererjava.options.RenderOptions;
import com.github.guoyyit.markdownsaferendererjava.options.UnknownOptionPolicy;
import com.github.guoyyit.markdownsaferendererjava.parser.ParseError;
import com.github.guoyyit.markdownsaferendererjava.service.RenderResponse;
import com.github.guoyyit.markdownsaferendererjava.toc.TocEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** JSON DTOs for the REST API. */
public final class RenderDtos {

    private RenderDtos() {
    }

    public record OptionsDto(Integer version, Map<String, Object> values, String unknownPolicy) {
    }

    public record RenderRequestDto(String content, OptionsDto options,
                                   List<String> reservedAnchors) {
    }

    public record ErrorDto(String severity, int line, int column, String message,
                           String skippedReason) {
        static ErrorDto from(ParseError e) {
            return new ErrorDto(e.getSeverity().name(), e.getLocation().getLine(),
                    e.getLocation().getColumn(), e.getMessage(), e.getSkippedReason());
        }
    }

    public record TocDto(String number, String title, String anchor, int level,
                         List<TocDto> children) {
        static TocDto from(TocEntry e) {
            List<TocDto> kids = new ArrayList<>();
            for (TocEntry c : e.getChildren()) {
                kids.add(from(c));
            }
            return new TocDto(e.getNumber(), e.getTitle(), e.getAnchor(), e.getLevel(), kids);
        }
    }

    /** Simplified, JSON-safe view of the AST (no parent cycles). */
    public record NodeDto(String type, Integer level, String anchor, String number,
                          String text, int line, int column, List<NodeDto> children) {
        static NodeDto from(Node n) {
            List<NodeDto> kids = new ArrayList<>();
            for (Node c : n.children()) {
                kids.add(from(c));
            }
            Integer level = null;
            String anchor = null;
            String number = null;
            String text = null;
            if (n instanceof Heading h) {
                level = h.getLevel();
                anchor = h.getAnchor();
                number = h.getNumber();
            } else if (n instanceof Text t) {
                text = t.getContent();
            }
            return new NodeDto(n.type().name(), level, anchor, number, text,
                    n.location().getLine(), n.location().getColumn(), kids);
        }
    }

    public record RenderResponseDto(String status, String html, TocDto toc, NodeDto document,
                                    List<ErrorDto> errors, String rejectionReason,
                                    String message) {
        public static RenderResponseDto from(RenderResponse r) {
            List<ErrorDto> errors = new ArrayList<>();
            for (ParseError e : r.getErrors()) {
                errors.add(ErrorDto.from(e));
            }
            return new RenderResponseDto(
                    r.getStatus().name(),
                    r.getHtml(),
                    r.getToc() == null ? null : TocDto.from(r.getToc()),
                    r.getDocument() == null ? null : NodeDto.from(r.getDocument()),
                    errors,
                    r.getRejectionReason() == null ? null : r.getRejectionReason().name(),
                    r.getMessage());
        }
    }

    public static RenderOptions toOptions(OptionsDto dto) {
        if (dto == null) {
            return RenderOptions.defaults();
        }
        OptionsVersion version = dto.version() == null
                ? OptionsVersion.latest()
                : OptionsVersion.fromNumber(dto.version());
        UnknownOptionPolicy policy = dto.unknownPolicy() == null
                ? UnknownOptionPolicy.IGNORE
                : UnknownOptionPolicy.valueOf(dto.unknownPolicy().toUpperCase());
        return new RenderOptions(version,
                dto.values() == null ? Map.of() : dto.values(), policy);
    }
}
