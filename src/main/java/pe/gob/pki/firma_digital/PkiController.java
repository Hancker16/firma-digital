package pe.gob.pki.firma_digital;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.Signature;
import java.util.Enumeration;
import java.util.Base64;

@RestController
public class PkiController {

    @GetMapping("/pki/status")
    public String pkiStatus() {
        try {
            // Accede al almacén de certificados de Windows
            KeyStore keyStore = KeyStore.getInstance("Windows-MY");
            keyStore.load(null, null);

            Enumeration<String> aliases = keyStore.aliases();

            StringBuilder sb = new StringBuilder();
            sb.append("Certificados en Windows-MY:\n\n");

            boolean found = false;

            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();

                X509Certificate cert = (X509Certificate) keyStore.getCertificate(alias);

                if (cert == null)
                    continue;

                // Filtramos solo DNIe (RENIEC / ECEP)
                String issuer = cert.getIssuerX500Principal().getName();
                if (!issuer.contains("RENIEC"))
                    continue;

                found = true;

                sb.append("Alias: ").append(alias).append("\n");
                sb.append("Sujeto: ")
                        .append(cert.getSubjectX500Principal().getName()).append("\n");
                sb.append("Emisor: ")
                        .append(cert.getIssuerX500Principal().getName()).append("\n");
                sb.append("Válido desde: ").append(cert.getNotBefore()).append("\n");
                sb.append("Válido hasta: ").append(cert.getNotAfter()).append("\n");
                sb.append("--------------------------------------------------\n");
            }

            if (!found) {
                return "No se encontraron certificados DNIe en Windows-MY";
            }

            return sb.toString();

        } catch (Exception e) {
            return "ERROR MSCAPI: " +
                    e.getClass().getSimpleName() +
                    " - " + e.getMessage();
        }
    }

    @GetMapping("/pki/sign")
    public String signText() {
        try {
            KeyStore keyStore = KeyStore.getInstance("Windows-MY");
            keyStore.load(null, null);

            String firAlias = null;

            for (Enumeration<String> e = keyStore.aliases(); e.hasMoreElements();) {
                String alias = e.nextElement();
                X509Certificate cert = (X509Certificate) keyStore.getCertificate(alias);
                if (cert == null)
                    continue;

                String subject = cert.getSubjectX500Principal().getName();

                if (subject.contains(" FIR ")) {
                    firAlias = alias;
                    break;
                }
            }

            if (firAlias == null) {
                return "No se encontró certificado FIR";
            }

            // Obtener clave privada (AQUÍ SE PEDIRÁ EL PIN)
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(firAlias, null);

            // Datos a firmar
            byte[] data = "Prueba de firma con DNIe".getBytes();

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(data);

            byte[] signed = signature.sign();

            String base64 = Base64.getEncoder().encodeToString(signed);

            return "Firma OK (Base64):\n" + base64;

        } catch (Exception e) {
            return "ERROR FIRMA: " +
                    e.getClass().getSimpleName() +
                    " - " + e.getMessage();
        }
    }

}
