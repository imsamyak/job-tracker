package com.jobtracker.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
class ResumeTest {

    @TestConfiguration
    static class Fake {
        @Bean
        @Primary
        ProfileExtractor extractor() {
            return text -> new ExtractedProfile(
                    new ExtractedProfile.Personal("Asha Rao", "Engineer at Acme", "3 years", "Pune, MH", ""),
                    List.of(), List.of(), List.of(new ExtractedProfile.SkillGroup("Languages", List.of("Java", "React"))),
                    new ExtractedProfile.Contact(List.of(new ExtractedProfile.Item("Main", "asha@x.com")), List.of(), List.of()));
        }
    }

    @Autowired ResumeController controller;
    @Autowired ResumeService service;

    @BeforeEach
    void clean() {
        service.delete();
    }

    static byte[] pdf(String text) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void noResumeYet() {
        assertThat(controller.info().present()).isFalse();
    }

    @Test
    void uploadKeepsOneResumeAndReplacesIt() throws Exception {
        controller.upload(new MockMultipartFile("file", "a.pdf", "application/pdf", pdf("first")));
        var info = controller.upload(new MockMultipartFile("file", "C:\\docs\\b.pdf", "application/pdf", pdf("second")));
        assertThat(info.filename()).isEqualTo("b.pdf");
        assertThat(controller.info().filename()).isEqualTo("b.pdf");
        assertThat(service.text()).contains("second");
    }

    @Test
    void onlyRealPdfsAreAccepted() {
        assertThatThrownBy(() -> controller.upload(new MockMultipartFile("file", "x.pdf", "application/pdf", "not a pdf".getBytes())))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(controller.info().present()).isFalse();
    }

    @Test
    void extractReturnsProfileFields() throws Exception {
        controller.upload(new MockMultipartFile("file", "a.pdf", "application/pdf", pdf("Asha Rao")));
        ExtractedProfile p = controller.extract();
        assertThat(p.personal().name()).isEqualTo("Asha Rao");
        assertThat(p.contact().emails()).hasSize(1);
        assertThat(p.skills()).hasSize(1);
        assertThat(p.skills().get(0).items()).containsExactly("Java", "React");
    }

    @Test
    void readingAnotherResumeDoesNotReplaceTheSavedOne() throws Exception {
        controller.upload(new MockMultipartFile("file", "sample.pdf", "application/pdf", pdf("sample")));
        ExtractedProfile p = controller.read(new MockMultipartFile("file", "other.pdf", "application/pdf", pdf("other")));
        assertThat(p.personal().name()).isEqualTo("Asha Rao");
        assertThat(controller.info().filename()).isEqualTo("sample.pdf");
        assertThatThrownBy(() -> controller.read(new MockMultipartFile("file", "x.pdf", "application/pdf", "nope".getBytes())))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void theAddressBehindALinkIsReadAlongsideItsVisibleWords() throws Exception {
        byte[] bytes;
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("Source Code");
                cs.endText();
            }
            var link = new org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink();
            link.setRectangle(new org.apache.pdfbox.pdmodel.common.PDRectangle(48, 696, 80, 16));
            var uri = new org.apache.pdfbox.pdmodel.interactive.action.PDActionURI();
            uri.setURI("https://github.com/me/linkora");
            link.setAction(uri);
            page.getAnnotations().add(link);
            doc.save(out);
            bytes = out.toByteArray();
        }
        assertThat(service.textOf(bytes)).contains("Source Code -> https://github.com/me/linkora");
    }

    @Test
    void extractWithoutResumeIsNotFound() {
        assertThatThrownBy(() -> controller.extract()).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void parseToleratesMissingValues() {
        var node = JsonMapper.builder().build().readTree("{\"personal\":{\"name\":\" Sam \"},\"experience\":[{\"company\":\"A\",\"current\":true,\"end\":\"x\"}],\"skills\":[{\"name\":\" Languages \",\"items\":[\" Java \",\"Java\",\"\"]},{\"name\":\"Empty\",\"items\":[]}],\"contact\":{\"emails\":[{\"label\":\"Main\",\"value\":\"\"}]}}");
        ExtractedProfile p = ClaudeProfileExtractor.parse(node);
        assertThat(p.personal().name()).isEqualTo("Sam");
        assertThat(p.experience().get(0).end()).isEmpty();
        assertThat(p.contact().emails()).isEmpty();
        assertThat(p.skills()).hasSize(1);
        assertThat(p.skills().get(0).name()).isEqualTo("Languages");
        assertThat(p.skills().get(0).items()).containsExactly("Java");
        assertThat(ClaudeProfileExtractor.schema()).contains("\"current\"");
    }
}
