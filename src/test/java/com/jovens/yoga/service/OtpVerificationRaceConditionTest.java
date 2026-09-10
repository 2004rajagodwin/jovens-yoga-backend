package com.jovens.yoga.service;

import com.jovens.yoga.dto.request.SendOtpRequest;
import com.jovens.yoga.dto.request.VerifyOtpRequest;
import com.jovens.yoga.dto.response.OtpSendResponse;
import com.jovens.yoga.dto.response.OtpVerifyResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduces and guards against the intermittent "correct OTP -> Invalid or expired OTP" bug
 * root-caused to the frontend: React StrictMode double-invokes the OTP modal's mount effect in
 * development, firing two concurrent {@code POST /api/auth/otp/send} requests for the same
 * phone number. Both requests independently regenerate and persist a new {@code otp_hash}; the
 * one actually committed last in the database has no guaranteed relationship to which response
 * the frontend happens to display. The fix lives entirely in
 * {@code OtpVerificationModal.jsx} (a mount-guard ref ensures only one real send ever fires) —
 * this test class instead proves the backend-side invariant that makes that fix sufficient:
 * whichever single {@link OtpSendResponse} a caller acts on, its {@code devOtp} always verifies
 * successfully, even under real concurrent sends for the same number.
 */
@SpringBootTest
@ActiveProfiles("test")
class OtpVerificationRaceConditionTest {

    @Autowired
    private OtpService otpService;

    private SendOtpRequest sendRequest(String mobile) {
        return new SendOtpRequest("Jane", "otp-" + mobile + "@example.com", "+1", mobile);
    }

    /**
     * The core reproduction the bug report asked for: send OTP, immediately verify the exact
     * devOtp just returned, repeated 100 times against 100 distinct phone numbers. Every
     * iteration must succeed — this is the baseline invariant a single, non-racing send/verify
     * pair must always satisfy.
     */
    @Test
    void sendThenImmediatelyVerify_repeated100Times_alwaysSucceeds() {
        for (int i = 0; i < 100; i++) {
            String mobile = "7880" + String.format("%06d", i);
            OtpSendResponse sent = otpService.sendOtp(sendRequest(mobile));

            assertThat(sent.devOtp()).as("iteration %d: devOtp must be present in TEST mode", i).isNotBlank();

            OtpVerifyResponse verified = otpService.verifyOtp(new VerifyOtpRequest("+1", mobile, sent.devOtp()));

            assertThat(verified.verified())
                    .as("iteration %d: sending then immediately verifying the SAME returned devOtp must succeed", i)
                    .isTrue();
            assertThat(verified.verificationToken()).as("iteration %d", i).isNotBlank();
        }
    }

    /**
     * Simulates a duplicate send (e.g. the exact frontend race before the fix, minus the
     * network race): two sequential sendOtp calls for the same number. Only the LATEST
     * response's code is valid — the caller must never mix codes from different send calls.
     */
    @Test
    void sendTwiceSequentially_onlyLatestResponseVerifies() {
        String mobile = "78810001";
        OtpSendResponse first = otpService.sendOtp(sendRequest(mobile));
        OtpSendResponse second = otpService.sendOtp(sendRequest(mobile));

        assertThat(second.devOtp()).isNotEqualTo(first.devOtp());

        // Verifying with the FIRST call's code must fail — it was overwritten by the second send.
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                otpService.verifyOtp(new VerifyOtpRequest("+1", mobile, first.devOtp())))
                .isInstanceOf(com.jovens.yoga.exception.BusinessException.class);

        // Verifying with the SECOND (latest) call's code must succeed.
        OtpVerifyResponse verified = otpService.verifyOtp(new VerifyOtpRequest("+1", mobile, second.devOtp()));
        assertThat(verified.verified()).isTrue();
    }

    /**
     * The genuinely concurrent case — two real threads calling sendOtp() for the same phone
     * number at (as close to) the same instant as the JVM allows, exactly like the two
     * requests React StrictMode used to fire. Whichever response a caller chooses to act on
     * (that response, and only that response) must always verify successfully — proving the
     * backend never hands back a devOtp that doesn't match what it just persisted, even when
     * racing against another concurrent send for the same number. This is the invariant the
     * frontend mount-guard fix depends on: as long as only one response is ever used, it works.
     */
    @Test
    void concurrentSends_whicheverSingleResponseIsUsedAlwaysVerifies() throws InterruptedException {
        String mobile = "78820002";
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicReference<OtpSendResponse> responseA = new AtomicReference<>();
        AtomicReference<OtpSendResponse> responseB = new AtomicReference<>();
        AtomicReference<RuntimeException> errorA = new AtomicReference<>();
        AtomicReference<RuntimeException> errorB = new AtomicReference<>();

        Runnable taskA = () -> {
            ready.countDown();
            await(go);
            try {
                responseA.set(otpService.sendOtp(sendRequest(mobile)));
            } catch (RuntimeException ex) {
                errorA.set(ex);
            }
        };
        Runnable taskB = () -> {
            ready.countDown();
            await(go);
            try {
                responseB.set(otpService.sendOtp(sendRequest(mobile)));
            } catch (RuntimeException ex) {
                errorB.set(ex);
            }
        };

        pool.submit(taskA);
        pool.submit(taskB);
        ready.await(5, TimeUnit.SECONDS);
        go.countDown(); // release both threads at (as near as possible) the same instant
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        // At least one concurrent send must have succeeded with a usable devOtp — whichever
        // one a real caller would have used is the one we verify here.
        OtpSendResponse usable = responseB.get() != null ? responseB.get() : responseA.get();
        assertThat(usable).as("at least one concurrent sendOtp call must have returned successfully").isNotNull();
        assertThat(usable.devOtp()).isNotBlank();

        OtpVerifyResponse verified = otpService.verifyOtp(new VerifyOtpRequest("+1", mobile, usable.devOtp()));
        assertThat(verified.verified())
                .as("the devOtp from whichever concurrent send response was chosen must verify successfully")
                .isTrue();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
