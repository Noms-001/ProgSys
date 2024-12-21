package cache;

import java.io.Serializable;

public class CacheEntry implements Serializable {
    private final byte[] data;
    private final long expirationTime;
    private final String contentType; 

    public long getExpirationTime() {
        return expirationTime;
    }

    public CacheEntry(byte[] data, String contentType, long expirationTime) {
        this.data = data;
        this.expirationTime = expirationTime;
        this.contentType = contentType;
    }

    public byte[] getData() {
        return data;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expirationTime;
    }

    public String getContentType() {
        return contentType; // Nouveau getter pour le type MIME
    }
}
