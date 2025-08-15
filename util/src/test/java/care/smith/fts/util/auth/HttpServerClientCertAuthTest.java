package care.smith.fts.util.auth;

import static care.smith.fts.util.auth.HttpServerClientCertAuth.extractCn;
import static java.lang.String.join;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import javax.security.auth.x500.X500Principal;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class HttpServerClientCertAuthTest {

  @Nested
  class ExtractCn {
    @Test
    void withoutAttributes() {
      var cert = createCert();

      assertThat(extractCn(cert)).isEqualTo("");
    }

    @Test
    void withoutCn() {
      var cert = createCert("C=DE", "O=org");

      assertThat(extractCn(cert)).isEqualTo("");
    }

    @Test
    void withCnOnly() {
      var cert = createCert("CN=test");

      assertThat(extractCn(cert)).isEqualTo("test");
    }

    @Test
    void withCnAndO() {
      var cert = createCert("CN=test", "O=org");

      assertThat(extractCn(cert)).isEqualTo("test");
    }

    @Test
    void withCnOAndC() {
      var cert = createCert("C=DE", "O=org", "CN=test");

      assertThat(extractCn(cert)).isEqualTo("test");
    }

    private static X509Certificate createCert(String... attributes) {
      var cert = mock(X509Certificate.class);
      var principal = mock(X500Principal.class);
      given(cert.getSubjectX500Principal()).willReturn(principal);
      given(principal.getName()).willReturn(join(",", attributes));
      return cert;
    }

    @Test
    void withRealCert() throws CertificateException {
      var cert = readCert();

      assertThat(extractCn(cert)).isEqualTo("default");
    }

    private static X509Certificate readCert() throws CertificateException {
      String certificate =
          // Subject: CN = default, OU = Client, O = FTSnext
          """
          -----BEGIN CERTIFICATE-----
          MIIBCDCBuwIUOYdg+8EMGiwZjqLpvRuNAcP52q0wBQYDK2VwMBkxFzAVBgNVBAMM
          DmZ0cy5zbWl0aC5jYXJlMB4XDTI1MDgxNTA3Mjk0N1oXDTI1MDkxNDA3Mjk0N1ow
          NTEQMA4GA1UEAwwHZGVmYXVsdDEPMA0GA1UECwwGQ2xpZW50MRAwDgYDVQQKDAdG
          VFNuZXh0MCowBQYDK2VwAyEA+W8ORlaDXiVRb3IEr6bbWcwcHvFJ5h0qdRaLs+Yl
          EZowBQYDK2VwA0EAlEqxjgWgt184OlvXKXr47nm9XjqhZG2g5w1uMKIM0xgfpyrv
          94YN7+JyO0MBYnIVzFmh5ifQCTsxP5PfuZbaDA==
          -----END CERTIFICATE-----
          """;
      InputStream targetStream = new ByteArrayInputStream(certificate.getBytes());
      return (X509Certificate)
          CertificateFactory.getInstance("X509").generateCertificate(targetStream);
    }
  }
}
