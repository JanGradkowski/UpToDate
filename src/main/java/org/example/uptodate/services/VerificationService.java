package org.example.uptodate.services;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
@Service
public class VerificationService {
    private final Map<String, String> otpStorage = new ConcurrentHashMap<>();

    public String generateAndStoreCode(String email) {
        String code = String.format("%06d", new Random().nextInt(999999));
        otpStorage.put(email, code);
        return code;
    }

    public boolean verifyCode(String email, String inputCode) {
        String code = otpStorage.get(email);
        if(code != null && code.equals(inputCode)) {
            otpStorage.remove(email);
            return true;
        }
        return false;
    }
}
