package kr.co.ircp.cms.domain.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import kr.co.ircp.cms.domain.ai.model.AiSimulationSession;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

/**
 * 시뮬레이션 보고서 PDF 생성 서비스 (OpenPDF).
 *
 * <p>SPEC-CMS-AI-001 — 외부 차트 라이브러리 없이 텍스트/표 기반으로
 * 세션 메타 + 연도별 성장단계 투영을 PDF로 렌더링한다.
 * 입력 데이터에는 PII가 없다 (ksicCode/capital/foundingYear 등 비식별 값만).
 */
@Service
public class PdfGeneratorService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 시뮬레이션 세션을 PDF 바이트로 변환한다.
     */
    public byte[] generateSimulationReport(AiSimulationSession session) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 11);

            document.add(new Paragraph("AI Business Simulation Report", titleFont));
            document.add(new Paragraph(" "));
            document.add(new Paragraph(
                    "Session ID: " + session.getId(), normalFont));
            document.add(new Paragraph(
                    "KSIC Code: " + nullToDash(session.getKsicCode()), normalFont));
            document.add(new Paragraph(
                    "Capital Amount: " + nullToDash(session.getCapitalAmount()), normalFont));
            document.add(new Paragraph(
                    "Founding Year: " + nullToDash(session.getFoundingYear()), normalFont));
            if (session.getCreatedAt() != null) {
                document.add(new Paragraph(
                        "Generated At: " + TS.format(
                                session.getCreatedAt().atZone(
                                        java.time.ZoneId.systemDefault())), normalFont));
            }
            document.add(new Paragraph(" "));
            document.add(new Paragraph("3-Year Projection", normalFont));

            PdfPTable table = new PdfPTable(3);
            table.setWidthPercentage(100);
            addHeader(table, "Year");
            addHeader(table, "Stage");
            addHeader(table, "Entry Probabilities");
            renderProjectionRows(table, session.getProjectionResult());
            document.add(table);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            // 생성 실패 — 호출부(SimulationServiceImpl)가 pdf_status=FAILED 처리
            throw new IllegalStateException("PDF 생성 실패: " + e.getMessage(), e);
        }
    }

    private void renderProjectionRows(PdfPTable table, String projectionJson) {
        if (projectionJson == null || projectionJson.isBlank()) {
            addEmptyRow(table);
            return;
        }
        try {
            JsonNode root = MAPPER.readTree(projectionJson);
            JsonNode projection = root.has("projection") ? root.get("projection") : root;
            if (projection == null || !projection.isArray() || projection.isEmpty()) {
                addEmptyRow(table);
                return;
            }
            for (JsonNode point : projection) {
                table.addCell(text(point.path("year").asText("-")));
                table.addCell(text(point.path("stage").asText("-")));
                JsonNode probs = point.get("entryProbabilities");
                table.addCell(text(probs != null ? probs.toString() : "-"));
            }
        } catch (Exception e) {
            addEmptyRow(table);
        }
    }

    private void addEmptyRow(PdfPTable table) {
        table.addCell(text("-"));
        table.addCell(text("No projection data"));
        table.addCell(text("-"));
    }

    private void addHeader(PdfPTable table, String label) {
        PdfPCell cell = new PdfPCell(text(label));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private com.lowagie.text.Phrase text(String value) {
        return new com.lowagie.text.Phrase(
                value, FontFactory.getFont(FontFactory.HELVETICA, 10));
    }

    private String nullToDash(Object v) {
        return v == null ? "-" : String.valueOf(v);
    }
}
