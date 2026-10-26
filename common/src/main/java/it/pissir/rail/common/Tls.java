package it.pissir.rail.common;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

/** Connessioni TLS verso il broker: ci si fida solo dei certificati firmati dalla CA del progetto. */
public final class Tls {

    private Tls() {
    }

    public static SSLSocketFactory socketFactory(String caFile) {
        try (InputStream in = Files.newInputStream(Path.of(caFile))) {
            Certificate ca = CertificateFactory.getInstance("X.509").generateCertificate(in);

            KeyStore trusted = KeyStore.getInstance(KeyStore.getDefaultType());
            trusted.load(null, null);
            trusted.setCertificateEntry("ca", ca);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trusted);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, tmf.getTrustManagers(), null);
            return context.getSocketFactory();
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Impossibile caricare il certificato della CA " + caFile, e);
        }
    }
}
