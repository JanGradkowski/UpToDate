package org.example.uptodate.controller;

import org.example.uptodate.model.Conversation;
import org.example.uptodate.model.MessageType;
import org.example.uptodate.model.User;
import org.example.uptodate.repository.ConversationRepository;
import org.example.uptodate.repository.UserRepository;
import org.example.uptodate.services.FileStorageService;
import org.example.uptodate.services.MediaValidationService;
import org.example.uptodate.services.MessageService;
import org.example.uptodate.services.PrivacyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

@Controller
public class MessageController {

    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final MessageService messageService;
    private final PrivacyService privacyService;
    private final MediaValidationService mediaValidationService;
    private final FileStorageService fileStorageService;


    private final RestTemplate restTemplate = new RestTemplate();

    public MessageController(UserRepository userRepository, ConversationRepository conversationRepository,
                             MessageService messageService, PrivacyService privacyService,
                             MediaValidationService mediaValidationService, FileStorageService fileStorageService) {
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
        this.messageService = messageService;
        this.privacyService = privacyService;
        this.mediaValidationService = mediaValidationService;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/inbox")
    public String showInbox(Authentication authentication, Model model) {
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        model.addAttribute("currentUser", currentUser);

        List<Conversation> allConvos = conversationRepository.findByParticipantsContainingOrderByUpdatedAtDesc(currentUser);

        List<Conversation> activeChats = allConvos.stream()
                .filter(c -> !c.isPending() || c.getInitiator().equals(currentUser))
                .toList();

        model.addAttribute("conversations", activeChats);
        return "inbox";
    }

    @GetMapping("/inbox/requests")
    public String showRequests(Authentication authentication, Model model) {
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        model.addAttribute("currentUser", currentUser);

        List<Conversation> requests = conversationRepository.findByParticipantsContainingOrderByUpdatedAtDesc(currentUser).stream()
                .filter(c -> c.isPending() && !c.getInitiator().equals(currentUser))
                .toList();

        model.addAttribute("requests", requests);
        return "inbox-requests";
    }

    @GetMapping("/direct/{username}")
    public String showChatWindow(@PathVariable("username") String username, Authentication authentication, Model model) {
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        User targetUser = userRepository.findByUsername(username).orElseThrow();

        if (privacyService.isBlockedBetween(currentUser, targetUser)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        Conversation conversation = conversationRepository.findExistingConversation(currentUser, targetUser).orElse(null);

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("targetUser", targetUser);
        model.addAttribute("conversation", conversation);

        return "chat";
    }

    @PostMapping("/direct/{username}/send")
    public String sendMessage(@PathVariable("username") String username, @RequestParam("text") String text, Authentication authentication) {
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        User targetUser = userRepository.findByUsername(username).orElseThrow();

        if (!privacyService.isBlockedBetween(currentUser, targetUser)) {
            messageService.sendMessage(currentUser, targetUser, text);
        }

        return "redirect:/direct/" + username;
    }


    @PostMapping("/api/direct/{username}/send-media")
    public ResponseEntity<?> sendMedia(@PathVariable("username") String username,
                                       @RequestParam("file") MultipartFile file,
                                       @RequestParam(value = "kind", defaultValue = "media") String kind,
                                       @RequestParam(value = "caption", required = false) String caption,
                                       @RequestParam(value = "durationSeconds", required = false) Integer durationSeconds,
                                       Authentication authentication) {
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        User targetUser = userRepository.findByUsername(username).orElseThrow();

        if (privacyService.isBlockedBetween(currentUser, targetUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You can't message this user.");
        }

        try {
            MediaValidationService.DetectedMedia detected = "voice".equals(kind)
                    ? mediaValidationService.detectAndValidateVoice(file)
                    : mediaValidationService.detectAndValidate(file);

            String mediaUrl = fileStorageService.saveChatMedia(file, detected.extension);

            messageService.sendMediaMessage(currentUser, targetUser, detected.messageType,
                    mediaUrl, detected.contentType, caption, durationSeconds);

            return ResponseEntity.ok().build();

        } catch (IllegalArgumentException badFile) {
            // Validation failure (wrong/unsupported type, too large) - safe to
            // show this message directly, it never includes raw file content.
            return ResponseEntity.badRequest().body(badFile.getMessage());
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Could not process the file.");
        }
    }


    @PostMapping("/api/direct/{username}/send-gif")
    public ResponseEntity<?> sendGif(@PathVariable("username") String username,
                                     @RequestParam("gifUrl") String gifUrl,
                                     Authentication authentication) {
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        User targetUser = userRepository.findByUsername(username).orElseThrow();

        if (privacyService.isBlockedBetween(currentUser, targetUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You can't message this user.");
        }

        if (!isAllowedGifHost(gifUrl)) {
            return ResponseEntity.badRequest().body("That GIF source isn't allowed.");
        }

        try {
            // 1. Spoof a real web browser to prevent CDNs from blocking the download
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);

            org.springframework.http.ResponseEntity<byte[]> response = restTemplate.exchange(
                    gifUrl, org.springframework.http.HttpMethod.GET, entity, byte[].class);
            byte[] bytes = response.getBody();

            if (bytes == null || bytes.length == 0) {
                return ResponseEntity.badRequest().body("Could not fetch that GIF.");
            }

            MultipartFile asMultipart = new InMemoryMultipartFile(bytes, "gif_download");
            MediaValidationService.DetectedMedia detected = mediaValidationService.detectAndValidate(asMultipart);

            // 2. Allow both GIF and VIDEO (since many modern GIFs are actually MP4s)
            if (detected.messageType != MessageType.GIF && detected.messageType != MessageType.VIDEO) {
                return ResponseEntity.badRequest().body("That wasn't a valid GIF or Video.");
            }

            String mediaUrl = fileStorageService.saveChatMedia(asMultipart, detected.extension);
            messageService.sendMediaMessage(currentUser, targetUser, detected.messageType,
                    mediaUrl, detected.contentType, null, null);

            return ResponseEntity.ok().build();

        } catch (IllegalArgumentException badFile) {
            return ResponseEntity.badRequest().body(badFile.getMessage());
        } catch (Exception e) {
            // 3. Print the actual error to the console so we aren't guessing!
            System.out.println("--- GIF DOWNLOAD FAILED ---");
            System.out.println("URL attempted: " + gifUrl);
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Could not send that GIF.");
        }
    }



    private boolean isAllowedGifHost(String url) {
        if (url == null) return false;
        try {
            java.net.URI uri = java.net.URI.create(url);
            String host = uri.getHost();
            if (host == null || !"https".equalsIgnoreCase(uri.getScheme())) {
                return false;
            }

            // Check standard allowed hosts
            boolean isAllowed = host.equals("klipy.com") || host.endsWith(".klipy.com")
                    || host.equals("media.tenor.com") || host.endsWith(".media.tenor.com")
                    || host.equals("c.tenor.com") || host.endsWith(".c.tenor.com")
                    // Added broader fallbacks often used by API CDNs
                    || host.contains("giphy.com")
                    || host.contains("amazonaws.com")
                    || host.contains("cloudinary.com");

            if (!isAllowed) {
                System.out.println("--- BLOCKED GIF HOST: " + host + " ---");
                System.out.println("To fix this, add this host to MessageController.isAllowedGifHost()");
            }

            return isAllowed;

        } catch (Exception e) {
            return false;
        }
    }

    @PostMapping("/direct/accept/{id}")
    public String acceptRequest(@PathVariable("id") Long id, Authentication authentication) {
        User currentUser = userRepository.findByUsername(authentication.getName()).orElseThrow();
        Conversation conversation = conversationRepository.findById(id).orElseThrow();

        if (conversation.isPending() && !conversation.getInitiator().equals(currentUser) && conversation.getParticipants().contains(currentUser)) {
            conversation.setPending(false);
            conversationRepository.save(conversation);
        }
        return "redirect:/inbox";
    }


    private static class InMemoryMultipartFile implements MultipartFile {
        private final byte[] bytes;
        private final String filename;

        InMemoryMultipartFile(byte[] bytes, String filename) {
            this.bytes = bytes;
            this.filename = filename;
        }

        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return filename; }
        @Override public String getContentType() { return null; }
        @Override public boolean isEmpty() { return bytes.length == 0; }
        @Override public long getSize() { return bytes.length; }
        @Override public byte[] getBytes() { return bytes; }
        @Override public java.io.InputStream getInputStream() { return new java.io.ByteArrayInputStream(bytes); }
        @Override public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            try (var out = new java.io.FileOutputStream(dest)) {
                out.write(bytes);
            }
        }
    }
}