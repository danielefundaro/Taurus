package com.fundaro.zodiac.taurus.service.impl;

import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class TenantLogoLoader {

    private static final Logger log = LoggerFactory.getLogger(TenantLogoLoader.class);
    private static final int MAX_DOWNLOAD_SIZE = 2 * 1024 * 1024;
    private static final long MAX_SOURCE_PIXELS = 25_000_000L;
    private static final int MAX_LOGO_DIMENSION = 1200;

    private final RestClient restClient;

    public TenantLogoLoader() {
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public Optional<byte[]> load(String logoUrl) {
        if (logoUrl == null || logoUrl.isBlank()) return Optional.empty();

        try {
            URI uri = URI.create(logoUrl);
            if (!isHttp(uri) || !isPublicHost(uri)) {
                log.warn("Ignoring unsafe tenant logo URL: {}", uri);
                return Optional.empty();
            }

            Optional<byte[]> downloaded = restClient.get()
                .uri(uri)
                .header(HttpHeaders.USER_AGENT, "Taurus/1.0")
                .accept(MediaType.IMAGE_PNG, MediaType.IMAGE_JPEG)
                .exchange((request, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) return Optional.empty();
                    MediaType contentType = response.getHeaders().getContentType();
                    if (contentType == null || !"image".equalsIgnoreCase(contentType.getType())) return Optional.empty();
                    long contentLength = response.getHeaders().getContentLength();
                    if (contentLength > MAX_DOWNLOAD_SIZE) return Optional.empty();
                    byte[] bytes = response.getBody().readNBytes(MAX_DOWNLOAD_SIZE + 1);
                    return bytes.length > MAX_DOWNLOAD_SIZE ? Optional.empty() : Optional.of(bytes);
                });
            return downloaded.flatMap(this::normalizeAsPng);
        } catch (Exception exception) {
            log.warn("Unable to load tenant logo from {}: {}", logoUrl, exception.getMessage());
            return Optional.empty();
        }
    }

    Optional<byte[]> normalizeAsPng(byte[] bytes) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            BufferedImage image = ImageIO.read(input);
            if (image == null || (long) image.getWidth() * image.getHeight() > MAX_SOURCE_PIXELS) return Optional.empty();
            BufferedImage normalized = resizeForPdf(image);
            if (!ImageIO.write(normalized, "png", output)) return Optional.empty();
            return Optional.of(output.toByteArray());
        } catch (IOException | RuntimeException exception) {
            log.warn("Unable to decode tenant logo: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    private static BufferedImage resizeForPdf(BufferedImage source) {
        int largestDimension = Math.max(source.getWidth(), source.getHeight());
        if (largestDimension <= MAX_LOGO_DIMENSION) return source;

        double scale = (double) MAX_LOGO_DIMENSION / largestDimension;
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    private static boolean isHttp(URI uri) {
        if (uri.getScheme() == null) return false;
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        return (scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null;
    }

    private static boolean isPublicHost(URI uri) throws IOException {
        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            if (
                address.isAnyLocalAddress() ||
                address.isLoopbackAddress() ||
                address.isLinkLocalAddress() ||
                address.isSiteLocalAddress() ||
                address.isMulticastAddress() ||
                isUniqueLocalIpv6(address)
            ) {
                return false;
            }
        }
        return true;
    }

    private static boolean isUniqueLocalIpv6(InetAddress address) {
        return address instanceof Inet6Address && (address.getAddress()[0] & 0xfe) == 0xfc;
    }
}
