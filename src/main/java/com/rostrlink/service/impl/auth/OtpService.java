package com.rostrlink.service.impl.auth;

import com.rostrlink.auth.config.AppProperties;
import com.rostrlink.common.ResetChannel;
import com.rostrlink.exception.auth.InvalidOtpException;
import com.rostrlink.exception.auth.OtpThrottledException;
import com.rostrlink.exception.auth.UserNotFoundException;
import com.rostrlink.entity.auth.OtpToken;
import com.rostrlink.entity.auth.User;
import com.rostrlink.repository.auth.OtpTokenRepository;
import com.rostrlink.repository.auth.UserRepository;
import com.rostrlink.util.OtpUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final OtpTokenRepository otpTokenRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final OtpUtil otpUtil;
    private final AppProperties props;

    // ── Send OTP ──────────────────────────────────────────────────────────────

    @Transactional
    public void sendOtp(String email, String purpose, ResetChannel channel) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));

        // Throttle: max N sends per hour
        long sentLastHour = otpTokenRepository.countSentSince(
                user.getUserId(), purpose,
                OffsetDateTime.now().minusHours(1));
        if (sentLastHour >= props.getOtp().getMaxSendsPerHour()) {
            throw new OtpThrottledException();
        }

        String raw = otpUtil.generate(props.getOtp().getLength());
        String hashed = otpUtil.hash(raw);

        OtpToken token = OtpToken.builder()
                .otpId(UUID.randomUUID())
                .user(user)
                .otpHash(hashed)
                .purpose(purpose)
                .channel(channel)   // stored as lower-case string in DB
                .expiresAt(OffsetDateTime.now().plusMinutes(props.getOtp().getTtlMinutes()))
                .build();

        otpTokenRepository.save(token);

        dispatch(user, raw, purpose, channel);
        log.info("OTP sent to user {} via {} for purpose {}", user.getUserId(), channel, purpose);
    }

    // ── Verify OTP ────────────────────────────────────────────────────────────

    @Transactional
    public void verifyOtp(String email, String rawOtp, String purpose) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));

        List<OtpToken> candidates = otpTokenRepository.findValidOtps(
                user.getUserId(), purpose, OffsetDateTime.now());

        OtpToken matched = candidates.stream()
                .filter(t -> otpUtil.verify(rawOtp, t.getOtpHash()))
                .findFirst()
                .orElseThrow(InvalidOtpException::new);

        matched.setUsed(true);
        otpTokenRepository.save(matched);
        log.info("OTP verified for user {} purpose {}", user.getUserId(), purpose);
    }

    // ── Dispatch ──────────────────────────────────────────────────────────────

    private void dispatch(User user, String otp, String purpose, ResetChannel channel) {
        switch (channel) {
            case SMS   -> sendSms(user.getPhoneNumber(), otp);
            case EMAIL -> sendEmail(user.getEmail(), otp, purpose);
        }
    }

    private void sendEmail(String to, String otp, String purpose) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setSubject("Your RostrLink verification code");
        msg.setText(String.format(
                "Your %s code is: %s\n\nThis code expires in %d minutes. Do not share it.",
                purpose.replace("_", " "), otp, props.getOtp().getTtlMinutes()));
        try {
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}", to, e);
            // Do not throw — OTP is already saved; user can request resend
        }
    }

    private void sendSms(String phone, String otp) {
        // TODO: integrate SMS provider (Twilio, AWS SNS, etc.)
        log.warn("SMS dispatch not implemented. OTP for {}: {}", phone, otp);
    }
}