package org.example.uptodate.model;

import jakarta.persistence.*;
import org.example.uptodate.services.MessageService;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import org.example.uptodate.services.MessageService;
@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 1000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageType type = MessageType.TEXT;

    @Column(name = "media_url")
    private String mediaUrl;

    @Column(name = "media_content_type")
    private String mediaContentType;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = false)
    private boolean isRead = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;



    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getId() {
        return id;
    }

    public User getSender() {
        return sender;
    }

    public String getContent() {
        return content;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setSender(User sender) {
        this.sender = sender;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setConversation(Conversation conversation) {
        this.conversation = conversation;
    }

    public boolean isRead() {
        return isRead;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public MessageType getType() {
        return type;
    }

    public String getMediaContentType() {
        return mediaContentType;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }
    public void setMediaContentType(String mediaContentType) {
        this.mediaContentType = mediaContentType;
    }
    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }
    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }
    public void setType(MessageType type) {
        this.type = type;
    }
    public void setIsRead(boolean isRead) {
        this.isRead = isRead;
    }

}