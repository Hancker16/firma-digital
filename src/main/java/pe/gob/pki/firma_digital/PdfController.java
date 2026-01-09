package pe.gob.pki.firma_digital;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@RestController
@RequestMapping("/pdf")
public class PdfController {

    private static final String UPLOAD_DIR = "uploads";

    @PostMapping("/upload")
    public String uploadPdf(@RequestParam("file") MultipartFile file) throws Exception {

        if (file.isEmpty()) {
            throw new RuntimeException("Archivo vacío");
        }

        if (!file.getOriginalFilename().toLowerCase().endsWith(".pdf")) {
            throw new RuntimeException("No es un PDF");
        }

        Files.createDirectories(Path.of(UPLOAD_DIR));

        Path filePath = Path.of(UPLOAD_DIR, "documento.pdf");

        Files.copy(
                file.getInputStream(),
                filePath,
                StandardCopyOption.REPLACE_EXISTING);

        // Retorna la URL para el visor
        return "/pdf/view";
    }

    @GetMapping("/view")
    public ResponseEntity<byte[]> viewPdf() throws Exception {

        byte[] pdfBytes = Files.readAllBytes(
                Path.of(UPLOAD_DIR, "documento.pdf"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=documento.pdf");

        return ResponseEntity
                .ok()
                .headers(headers)
                .body(pdfBytes);
    }

}
