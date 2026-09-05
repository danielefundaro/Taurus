package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.criteria.MediaCriteria;
import com.fundaro.zodiac.taurus.domain.enumeration.MediaAssetStatus;
import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import com.fundaro.zodiac.taurus.repository.MediaRepository;
import com.fundaro.zodiac.taurus.security.AuthoritiesConstants;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.MediaService;
import com.fundaro.zodiac.taurus.service.TenantFeatureService;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import com.fundaro.zodiac.taurus.service.mapper.MediaMapper;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Transactional
public class MediaServiceImpl extends CommonOpenSearchServiceImpl<Media, MediaDTO, MediaCriteria, MediaMapper, MediaRepository>
    implements MediaService {

    private static final String OCTET_STREAM = "application/octet-stream";

    private final TenantStorageService tenantStorageService;
    private final TenantFeatureService tenantFeatureService;

    public MediaServiceImpl(
        MediaRepository repository,
        MediaMapper mapper,
        TenantStorageService tenantStorageService,
        TenantFeatureService tenantFeatureService
    ) {
        super(repository, mapper, MediaService.class, Media.class);
        this.tenantStorageService = tenantStorageService;
        this.tenantFeatureService = tenantFeatureService;
    }

    @Override
    public Resource streamFile(Long id, AbstractAuthenticationToken token) {
        if (!isPrivileged(token) && !getRepository().hasActiveSheetMusicReference(id)) {
            throw notFound("Media asset not found");
        }
        MediaContent content = getContent(id, token);
        return new ByteArrayResource(content.bytes());
    }

    @Override
    public MediaDTO save(MediaDTO dto, AbstractAuthenticationToken token) {
        throw managedOnly();
    }

    @Override
    public MediaDTO update(Long id, MediaDTO dto, AbstractAuthenticationToken token) {
        throw managedOnly();
    }

    @Override
    public MediaDTO partialUpdate(Long id, MediaDTO dto, AbstractAuthenticationToken token) {
        throw managedOnly();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MediaDTO> findOne(Long id, AbstractAuthenticationToken token) {
        if (isPrivileged(token)) {
            requireFeatureAccess(id);
            return super.findOne(id, token);
        }
        return getRepository().findActiveSheetMusicMediaById(id).map(getMapper()::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MediaDTO> findEntitiesByCriteria(MediaCriteria criteria, Pageable pageable, AbstractAuthenticationToken token) {
        if (isPrivileged(token)) return super.findEntitiesByCriteria(criteria, pageable, token);
        return getRepository().findAll(buildSpecification(criteria).and(activeSheetMusicReference()), JpaPageableUtils.normalize(pageable))
            .map(getMapper()::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public long count(MediaCriteria criteria, AbstractAuthenticationToken token) {
        if (isPrivileged(token)) return super.count(criteria, token);
        return getRepository().count(buildSpecification(criteria).and(activeSheetMusicReference()));
    }

    @Override
    public MediaContent getContent(Long id, AbstractAuthenticationToken token) {
        requireFeatureAccess(id);
        return getContent(id, requiredTenant(token));
    }

    @Override
    public MediaContent getContent(Long id, String tenant) {
        requireFeatureAccess(id);
        Media media = getRepository().findByIdAndDeletedFalse(id)
            .orElseThrow(() -> notFound("Media asset not found"));
        if (tenant == null || tenant.isBlank()) throw new RequestAlertException(HttpStatus.UNAUTHORIZED, "Tenant is required", "mediaAsset", "tenant.missing");
        hydrateLegacyMetadata(media, tenant);
        if (media.getStatus() != MediaAssetStatus.READY) {
            throw new RequestAlertException(HttpStatus.CONFLICT, "Media asset is not available", getEntityName(), "media.notReady");
        }
        try {
            byte[] bytes = tenantStorageService.read(tenant, media.getStorageKey());
            if (bytes.length != media.getFileSize() || !sha256(bytes).equals(media.getSha256())) {
                media.setStatus(MediaAssetStatus.INVALID);
                getRepository().save(media);
                throw new RequestAlertException(HttpStatus.CONFLICT, "Media asset integrity check failed", getEntityName(), "media.integrity");
            }
            return new MediaContent(media.getOriginalFilename(), media.getMimeType(), media.getFileSize(), bytes);
        } catch (IOException exception) {
            throw new RequestAlertException(HttpStatus.NOT_FOUND, "Media file not found", getEntityName(), "media.fileNotFound");
        }
    }

    @Override
    public MediaDTO store(
        byte[] content,
        String originalFilename,
        String declaredMimeType,
        String category,
        AbstractAuthenticationToken token
    ) {
        if (content == null || content.length == 0) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "File is empty", getEntityName(), "file.empty");
        }
        String tenant = requiredTenant(token);
        requiredActor(token);
        String safeCategory = requireCategory(category);
        String mimeType = detectMimeType(content, declaredMimeType);
        String extension = extensionFor(mimeType, originalFilename);
        String digest = sha256(content);
        String fileName = sanitizeFileName(originalFilename, extension);
        String storageKey = String.join("/", safeCategory, UUID.randomUUID().toString(), digest + "." + extension);

        try {
            tenantStorageService.writeAtomically(tenant, storageKey, content);
        } catch (IOException exception) {
            throw new RequestAlertException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to store media file", getEntityName(), "file.store");
        }

        try {
            Media media = new Media();
            media.setName(fileName);
            media.setDescription(null);
            media.setStorageKey(storageKey);
            media.setOriginalFilename(fileName);
            media.setMimeType(mimeType);
            media.setFileExtension(extension);
            media.setFileSize(content.length);
            media.setSha256(digest);
            media.setStatus(MediaAssetStatus.READY);
            MediaDTO saved = saveEntity(media, token, true);
            registerRollbackCleanup(tenant, storageKey);
            return saved;
        } catch (RuntimeException exception) {
            deleteQuietly(tenant, storageKey);
            throw exception;
        }
    }

    @Override
    public MediaDTO delete(Long id, AbstractAuthenticationToken token) {
        if (!getRepository().isUnreferenced(id)) {
            throw new RequestAlertException(HttpStatus.CONFLICT, "Media asset is still referenced", getEntityName(), "media.referenced");
        }
        Media media = getRepository().findByIdAndDeletedFalse(id).orElseThrow(() -> notFound("Media asset not found"));
        media.setStatus(MediaAssetStatus.DELETED);
        MediaDTO deleted = super.delete(id, token);
        deleteAfterCommit(requiredTenant(token), media.getStorageKey());
        return deleted;
    }

    @Override
    public void deleteIfUnreferenced(Long id, AbstractAuthenticationToken token) {
        deleteIfUnreferenced(id, requiredTenant(token), requiredActor(token));
    }

    @Override
    public void deleteIfUnreferenced(Long id, String tenantCode, String actor) {
        if (id == null || !getRepository().isUnreferenced(id)) return;
        getRepository().findByIdAndDeletedFalse(id).ifPresent(media -> {
            media.setDeleted(true);
            media.setStatus(MediaAssetStatus.DELETED);
            media.setEditBy(actor == null || actor.isBlank() ? "system" : actor);
            media.setEditDate(new Date());
            getRepository().save(media);
            deleteAfterCommit(tenantCode, media.getStorageKey());
        });
    }

    private void hydrateLegacyMetadata(Media media, String tenant) {
        if (media.getStatus() != MediaAssetStatus.MIGRATION_PENDING) return;
        try {
            byte[] bytes = tenantStorageService.read(tenant, media.getStorageKey());
            String mimeType = detectMimeType(bytes, media.getMimeType());
            media.setMimeType(mimeType);
            media.setFileExtension(extensionFor(mimeType, media.getOriginalFilename()));
            media.setFileSize(bytes.length);
            media.setSha256(sha256(bytes));
            media.setStatus(MediaAssetStatus.READY);
            getRepository().save(media);
        } catch (IOException exception) {
            media.setStatus(MediaAssetStatus.INVALID);
            getRepository().save(media);
        }
    }

    private void registerRollbackCleanup(String tenant, String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) deleteQuietly(tenant, storageKey);
            }
        });
    }

    private void deleteAfterCommit(String tenant, String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteQuietly(tenant, storageKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteQuietly(tenant, storageKey);
            }
        });
    }

    private void deleteQuietly(String tenant, String storageKey) {
        try {
            tenantStorageService.delete(tenant, storageKey);
        } catch (IOException exception) {
            getLogger().warn("Unable to delete managed media asset {}", storageKey, exception);
        }
    }

    private static String requiredTenant(AbstractAuthenticationToken token) {
        String tenant = SecurityUtils.getTenantIdFromAuthentication(token);
        if (tenant == null || tenant.isBlank()) {
            throw new RequestAlertException(HttpStatus.UNAUTHORIZED, "Tenant is required", "mediaAsset", "tenant.missing");
        }
        return tenant;
    }

    private static String requiredActor(AbstractAuthenticationToken token) {
        String actor = SecurityUtils.getUserIdFromAuthentication(token);
        if (actor == null || actor.isBlank()) {
            throw new RequestAlertException(HttpStatus.UNAUTHORIZED, "Identity is required", "mediaAsset", "identity.missing");
        }
        return actor;
    }

    private static boolean isPrivileged(AbstractAuthenticationToken token) {
        return token != null && token.getAuthorities().stream().anyMatch(authority ->
            AuthoritiesConstants.SUPER_ADMIN.equals(authority.getAuthority()) ||
            AuthoritiesConstants.ADMIN.equals(authority.getAuthority()) ||
            AuthoritiesConstants.ARCHIVIST.equals(authority.getAuthority())
        );
    }

    private static org.springframework.data.jpa.domain.Specification<Media> activeSheetMusicReference() {
        return (root, query, cb) -> {
            var subquery = query.subquery(Long.class);
            var track = subquery.from(com.fundaro.zodiac.taurus.domain.Tracks.class);
            var score = track.join("scores");
            var media = score.join("media");
            subquery.select(media.get("id"));
            subquery.where(
                cb.equal(media.get("id"), root.get("id")),
                cb.isFalse(score.get("deleted")),
                cb.isFalse(track.get("deleted"))
            );
            return cb.exists(subquery);
        };
    }

    private static String requireCategory(String category) {
        if (category == null || !category.matches("[a-z0-9][a-z0-9-]{0,63}")) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "Invalid media category", "mediaAsset", "category.invalid");
        }
        return category;
    }

    private static String sanitizeFileName(String originalFilename, String extension) {
        String source = Objects.requireNonNullElse(originalFilename, "file." + extension);
        String leaf = source.replace('\\', '/');
        leaf = leaf.substring(leaf.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        String suffix = "." + extension;
        if (leaf.isBlank()) return "file" + suffix;
        if (!leaf.toLowerCase(Locale.ROOT).endsWith(suffix)) {
            int dot = leaf.lastIndexOf('.');
            leaf = (dot > 0 ? leaf.substring(0, dot) : leaf) + suffix;
        }
        if (leaf.length() <= 500) return leaf;
        return leaf.substring(0, 500 - suffix.length()) + suffix;
    }

    private static String detectMimeType(byte[] bytes, String declaredMimeType) {
        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47) {
            return "image/png";
        }
        if (bytes.length >= 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff) {
            return "image/jpeg";
        }
        if (bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-') {
            return "application/pdf";
        }
        return OCTET_STREAM;
    }

    private static String extensionFor(String mimeType, String originalFilename) {
        return switch (mimeType) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "application/pdf" -> "pdf";
            default -> {
                String value = Objects.requireNonNullElse(originalFilename, "");
                int dot = value.lastIndexOf('.');
                String extension = dot < 0 ? "bin" : value.substring(dot + 1).toLowerCase(Locale.ROOT);
                yield extension.matches("[a-z0-9]{1,31}") ? extension : "bin";
            }
        };
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private RequestAlertException notFound(String message) {
        return new RequestAlertException(HttpStatus.NOT_FOUND, message, getEntityName(), "id.notFound");
    }

    private RequestAlertException managedOnly() {
        return new RequestAlertException(
            HttpStatus.METHOD_NOT_ALLOWED,
            "Media assets can only be created from managed file content",
            getEntityName(),
            "media.managedOnly"
        );
    }

    private void requireFeatureAccess(Long id) {
        boolean finance = getRepository().hasFinanceReference(id);
        boolean inventory = getRepository().hasInventoryReference(id);
        if ((!finance && !inventory) || getRepository().hasUnrestrictedReference(id)) return;
        if (finance && tenantFeatureService.isEnabled(TenantFeature.FINANCE)) return;
        if (inventory && tenantFeatureService.isEnabled(TenantFeature.INVENTORY)) return;
        tenantFeatureService.requireEnabled(finance ? TenantFeature.FINANCE : TenantFeature.INVENTORY);
    }
}
