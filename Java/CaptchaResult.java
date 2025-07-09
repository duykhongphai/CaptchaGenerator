package captcha;

import java.util.concurrent.atomic.AtomicBoolean;

public class CaptchaResult implements AutoCloseable {

    private byte[] imageBytes;
    private int[] globalValues;
    private final String decryptionKey;
    public final StringBuilder captchaEnterd;
    private final Object inputLock = new Object();
    public int captchaFailCount;
    private final AtomicBoolean disposed = new AtomicBoolean(false);
    private final Object resourceLock = new Object();

    public CaptchaResult(byte[] imageBytes, int[] globalValues, String decryptionKey) {
        this.imageBytes = imageBytes;
        this.globalValues = globalValues;
        this.decryptionKey = decryptionKey;
        this.captchaEnterd = new StringBuilder();
    }

    public byte[] getImageBytes() {
        checkNotDisposed();
        synchronized (resourceLock) {
            return imageBytes;
        }
    }

    public int[] getGlobalValues() {
        checkNotDisposed();
        synchronized (resourceLock) {
            return globalValues != null ? globalValues.clone() : new int[0];
        }
    }

    public String getValue() {
        int[] values = getGlobalValues();
        if (values.length == 0) {
            return "";
        }

        StringBuilder info = new StringBuilder();
        for (int value : values) {
            info.append(value);
        }
        return info.toString();
    }

    public boolean verify() {
        synchronized (inputLock) {
            return captchaEnterd != null
                    && captchaEnterd.toString().trim().equalsIgnoreCase(decryptionKey);
        }
    }

    public void dispose() {
        if (disposed.compareAndSet(false, true)) {
            synchronized (resourceLock) {
                imageBytes = null;
                globalValues = null;
            }
            synchronized (inputLock) {
                captchaEnterd.setLength(0);
            }
        }
    }

    public boolean addInput(char input) {
        if (isDisposed()) {
            return false;
        }

        synchronized (inputLock) {
            if (captchaEnterd.length() == 6) {
                captchaEnterd.deleteCharAt(0);
            }
            captchaEnterd.append(input);
            boolean completed = captchaEnterd.length() == 6 && verify();
            if (completed) {
                dispose();
            }
            return completed;
        }
    }

    @Override
    public void close() {
        dispose();
    }

    public boolean isDisposed() {
        return disposed.get();
    }

    private void checkNotDisposed() {
        if (isDisposed()) {
            throw new IllegalStateException("CaptchaResult has been disposed");
        }
    }
}
