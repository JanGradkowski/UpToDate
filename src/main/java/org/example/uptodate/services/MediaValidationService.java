package org.example.uptodate.services;

import org.example.uptodate.model.MessageType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;


@Service
public class MediaValidationService {

    public static class DetectedMedia {
        public final MessageType messageType;
        public final String contentType;
        public final String extension;

        public DetectedMedia(MessageType messageType, String contentType, String extension) {
            this.messageType = messageType;
            this.contentType = contentType;
            this.extension = extension;
        }
    }

    // Per-category size caps (bytes). Keep these in sync with
    // spring.servlet.multipart.max-file-size in application.properties (that's the
    // hard ceiling Spring enforces before we even get here) - these are the
    // feature-specific limits on top of it.
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;   // 10MB
    private static final long MAX_GIF_BYTES = 15L * 1024 * 1024;     // 15MB
    private static final long MAX_VIDEO_BYTES = 50L * 1024 * 1024;   // 50MB
    private static final long MAX_VOICE_BYTES = 10L * 1024 * 1024;   // 10MB

    /**
     * Validates the file and returns its real detected type, or throws
     * IllegalArgumentException with a user-safe message if it doesn't match
     * anything on the whitelist or fails its size cap.
     */
    public DetectedMedia detectAndValidate(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file was uploaded.");
        }

        byte[] header = readHeader(file, 32);

        // --- Images (excluding SVG/anything XML-based - those are rejected) ---
        if (matches(header, 0x89, 0x50, 0x4E, 0x47)) { // PNG
            checkSize(file, MAX_IMAGE_BYTES, "image");
            return new DetectedMedia(MessageType.IMAGE, "image/png", ".png");
        }
        if (matches(header, 0xFF, 0xD8, 0xFF)) { // JPEG
            checkSize(file, MAX_IMAGE_BYTES, "image");
            return new DetectedMedia(MessageType.IMAGE, "image/jpeg", ".jpg");
        }
        if (matches(header, 0x52, 0x49, 0x46, 0x46) && matchesAt(header, 8, 0x57, 0x45, 0x42, 0x50)) { // WEBP (RIFF....WEBP)
            checkSize(file, MAX_IMAGE_BYTES, "image");
            return new DetectedMedia(MessageType.IMAGE, "image/webp", ".webp");
        }

        // --- GIF (separate category from static images, per the chat feature) ---
        if (matchesAscii(header, "GIF8")) { // GIF87a or GIF89a
            checkSize(file, MAX_GIF_BYTES, "GIF");
            return new DetectedMedia(MessageType.GIF, "image/gif", ".gif");
        }

        // --- Video ---
        if (matchesAsciiAt(header, 4, "ftyp")) { // MP4/MOV "ftyp" box at offset 4
            checkSize(file, MAX_VIDEO_BYTES, "video");
            return new DetectedMedia(MessageType.VIDEO, "video/mp4", ".mp4");
        }
        if (matches(header, 0x1A, 0x45, 0xDF, 0xA3)) { // WebM/Matroska EBML header
            checkSize(file, MAX_VIDEO_BYTES, "video");
            return new DetectedMedia(MessageType.VIDEO, "video/webm", ".webm");
        }

        // --- Voice / audio (file-upload fallback path; OGG/MP3 only here since WebM
        //     is ambiguous with video and is handled explicitly by detectAndValidateVoice) ---
        if (matchesAscii(header, "OggS")) {
            checkSize(file, MAX_VOICE_BYTES, "voice message");
            return new DetectedMedia(MessageType.VOICE, "audio/ogg", ".ogg");
        }
        if (matchesAscii(header, "ID3") || matches(header, 0xFF, 0xFB)) { // MP3 (ID3 tag or frame sync)
            checkSize(file, MAX_VOICE_BYTES, "voice message");
            return new DetectedMedia(MessageType.VOICE, "audio/mpeg", ".mp3");
        }

        throw new IllegalArgumentException(
                "Unsupported or unrecognized file type. Allowed: JPEG, PNG, WEBP, GIF, MP4, WEBM, OGG, MP3.");
    }



    public DetectedMedia detectAndValidateVoice(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No audio was recorded.");
        }
        byte[] header = readHeader(file, 32);

        if (matches(header, 0x1A, 0x45, 0xDF, 0xA3)) { // WebM container (audio/webm;codecs=opus)
            checkSize(file, MAX_VOICE_BYTES, "voice message");
            return new DetectedMedia(MessageType.VOICE, "audio/webm", ".webm");
        }
        if (matchesAscii(header, "OggS")) {
            checkSize(file, MAX_VOICE_BYTES, "voice message");
            return new DetectedMedia(MessageType.VOICE, "audio/ogg", ".ogg");
        }
        if (matchesAscii(header, "ID3") || matches(header, 0xFF, 0xFB)) { // MP3
            checkSize(file, MAX_VOICE_BYTES, "voice message");
            return new DetectedMedia(MessageType.VOICE, "audio/mpeg", ".mp3");
        }


        // Apple devices record voice notes inside an MP4 container.
        if (matchesAsciiAt(header, 4, "ftyp")) {
            checkSize(file, MAX_VOICE_BYTES, "voice message");
            return new DetectedMedia(MessageType.VOICE, "audio/mp4", ".m4a");
        }

        throw new IllegalArgumentException("Unrecognized audio format for voice message.");
    }

    private void checkSize(MultipartFile file, long maxBytes, String label) {
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException(
                    "That " + label + " is too large (max " + (maxBytes / (1024 * 1024)) + "MB).");
        }
    }

    private byte[] readHeader(MultipartFile file, int numBytes) throws IOException {
        byte[] buffer = new byte[numBytes];
        try (InputStream in = file.getInputStream()) {
            int read = in.read(buffer);
            if (read < 0) {
                return new byte[0];
            }
        }
        return buffer;
    }

    private boolean matches(byte[] header, int... signature) {
        return matchesAt(header, 0, signature);
    }

    private boolean matchesAt(byte[] header, int offset, int... signature) {
        if (header.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            int unsigned = header[offset + i] & 0xFF;
            if (unsigned != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesAscii(byte[] header, String ascii) {
        return matchesAsciiAt(header, 0, ascii);
    }

    private boolean matchesAsciiAt(byte[] header, int offset, String ascii) {
        if (header.length < offset + ascii.length()) {
            return false;
        }
        for (int i = 0; i < ascii.length(); i++) {
            if (header[offset + i] != (byte) ascii.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}