package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.bouncycastle.asn1.ASN1IA5String;
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.DistributionPoint;
import org.bouncycastle.asn1.x509.DistributionPointName;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Revocation of a CIE certificate, checked with the revocation list named in the certificate. A list that cannot be
 * downloaded in time, is too large, is not signed by the issuer or is out of date leaves the revocation unchecked: the
 * signature is accepted and the evidence records {@link #UNAVAILABLE}. Lists are kept until their next update, at most
 * for a day.
 */
@Component
public class CieRevocationChecker {

    public static final String GOOD = "GOOD";
    public static final String REVOKED = "REVOKED";
    public static final String UNAVAILABLE = "UNAVAILABLE";

    private static final Logger LOG = LoggerFactory.getLogger(CieRevocationChecker.class);
    static final int MAX_CRL_BYTES = 20 * 1024 * 1024;
    private static final Duration MAX_CACHE = Duration.ofDays(1);

    private final Duration timeout;
    private final HttpClient client;
    private final Cache<String, CachedCrl> cache = Caffeine.newBuilder()
        .maximumSize(32)
        .expireAfter(new Expiry<String, CachedCrl>() {
            @Override
            public long expireAfterCreate(String key, CachedCrl value, long currentTime) {
                return Math.max(0, Duration.between(Instant.now(), value.expiresAt()).toNanos());
            }

            @Override
            public long expireAfterUpdate(String key, CachedCrl value, long currentTime, long currentDuration) {
                return expireAfterCreate(key, value, currentTime);
            }

            @Override
            public long expireAfterRead(String key, CachedCrl value, long currentTime, long currentDuration) {
                return currentDuration;
            }
        })
        .build();

    public CieRevocationChecker(ApplicationProperties applicationProperties) {
        this.timeout = applicationProperties.getSignature().getCie().getRevocationTimeout();
        this.client = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    /**
     * {@link #GOOD}, {@link #REVOKED} or {@link #UNAVAILABLE} for a certificate issued by {@code issuer}.
     */
    public String check(X509Certificate certificate, X509Certificate issuer) {
        List<String> locations = locations(certificate);
        if (locations.isEmpty()) {
            LOG.info("signature_ciesign_revocation_unavailable reason=noDistributionPoint");
            return UNAVAILABLE;
        }
        for (String location : locations) {
            try {
                return crl(location, issuer).isRevoked(certificate) ? REVOKED : GOOD;
            } catch (IOException | GeneralSecurityException | RuntimeException exception) {
                LOG.info("signature_ciesign_revocation_unavailable reason={}", exception.getMessage());
            }
        }
        return UNAVAILABLE;
    }

    private X509CRL crl(String location, X509Certificate issuer) throws IOException, GeneralSecurityException {
        CachedCrl cached = cache.getIfPresent(location);
        if (cached != null && cached.issuer().equals(issuer.getSubjectX500Principal().getName())) return cached.crl();
        X509CRL crl = (X509CRL) CertificateFactory.getInstance("X.509").generateCRL(new ByteArrayInputStream(download(location)));
        if (!crl.getIssuerX500Principal().equals(issuer.getSubjectX500Principal())) throw new GeneralSecurityException("crlIssuerMismatch");
        crl.verify(issuer.getPublicKey());
        Instant now = Instant.now();
        if (crl.getNextUpdate() != null && crl.getNextUpdate().toInstant().isBefore(now)) throw new GeneralSecurityException("crlOutOfDate");
        Instant expiresAt = now.plus(MAX_CACHE);
        if (crl.getNextUpdate() != null && crl.getNextUpdate().toInstant().isBefore(expiresAt)) expiresAt = crl.getNextUpdate().toInstant();
        cache.put(location, new CachedCrl(crl, issuer.getSubjectX500Principal().getName(), expiresAt));
        return crl;
    }

    private byte[] download(String location) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(location)).timeout(timeout).GET().build();
        CompletableFuture<HttpResponse<byte[]>> response = client.sendAsync(request, info -> new LimitedBody(MAX_CRL_BYTES));
        try {
            HttpResponse<byte[]> result = response.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (result.statusCode() != 200) throw new IOException("crlStatus" + result.statusCode());
            return result.body();
        } catch (TimeoutException exception) {
            response.cancel(true);
            throw new IOException("crlTimeout", exception);
        } catch (ExecutionException exception) {
            throw new IOException(exception.getCause() == null ? "crlDownload" : exception.getCause().getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("crlInterrupted", exception);
        }
    }

    /**
     * HTTP addresses of the revocation lists named in the certificate.
     */
    static List<String> locations(X509Certificate certificate) {
        byte[] extension = certificate.getExtensionValue(Extension.cRLDistributionPoints.getId());
        List<String> locations = new ArrayList<>();
        if (extension == null) return locations;
        try {
            CRLDistPoint points = CRLDistPoint.getInstance(JcaX509ExtensionUtils.parseExtensionValue(extension));
            for (DistributionPoint point : points.getDistributionPoints()) {
                DistributionPointName name = point.getDistributionPoint();
                if (name == null || name.getType() != DistributionPointName.FULL_NAME) continue;
                for (GeneralName general : org.bouncycastle.asn1.x509.GeneralNames.getInstance(name.getName()).getNames()) {
                    if (general.getTagNo() != GeneralName.uniformResourceIdentifier) continue;
                    String location = ASN1IA5String.getInstance(general.getName()).getString();
                    String scheme = location.toLowerCase(Locale.ROOT);
                    if (scheme.startsWith("http://") || scheme.startsWith("https://")) locations.add(location);
                }
            }
        } catch (IOException | RuntimeException exception) {
            LOG.info("signature_ciesign_revocation_unavailable reason=invalidDistributionPoint");
        }
        return locations;
    }

    private record CachedCrl(X509CRL crl, String issuer, Instant expiresAt) {
    }

    /**
     * Body of a response read up to a maximum size: a larger list is refused without reading it all.
     */
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {

        private final int maximum;
        private final ByteArrayOutputStream content = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flow.Subscription subscription;

        LimitedBody(int maximum) {
            this.maximum = maximum;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            value.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (result.isDone()) return;
            for (ByteBuffer buffer : buffers) {
                if (content.size() + buffer.remaining() > maximum) {
                    subscription.cancel();
                    result.completeExceptionally(new IOException("crlTooLarge"));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                content.writeBytes(chunk);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            result.complete(content.toByteArray());
        }
    }
}
