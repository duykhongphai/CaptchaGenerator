package captcha;

import java.util.concurrent.ConcurrentHashMap;
import com.girlkun.models.player.Player;
import com.girlkun.services.Service;

public class CaptchaManager {

    private static volatile CaptchaManager instance;
    private static final Object lock = new Object();

    private final ConcurrentHashMap<Integer, CaptchaSession> activeCaptchas;

    private static class CaptchaSession {

        final CaptchaResult captchaResult;

        CaptchaSession(CaptchaResult captchaResult) {
            this.captchaResult = captchaResult;
        }

        void dispose() {
            if (captchaResult != null && !captchaResult.isDisposed()) {
                captchaResult.dispose();
            }
        }
    }

    private CaptchaManager() {
        this.activeCaptchas = new ConcurrentHashMap<>();
    }

    public static CaptchaManager getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new CaptchaManager();
                }
            }
        }
        return instance;
    }

    public void generateCaptchaForPlayer(Player player) {
        try {
            int sessionId = generateCaptcha(player, player.getSession().getZoomLevel());
            CaptchaResult captcha = getCaptcha(sessionId);
            if (captcha != null) {
                Service.gI().sendCaptcha(player);
            }

        } catch (Exception e) {
        }
    }

    public int generateCaptcha(Player player, int zoomLevel) {
        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null");
        }
        try {
            int sessionId = player.getSession().getUserId();
            CaptchaResult captchaResult = CaptchaGenerator.createCaptchaImage(zoomLevel);
            CaptchaSession session = new CaptchaSession(captchaResult);
            activeCaptchas.put(sessionId, session);
            return sessionId;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate CAPTCHA", e);
        }
    }

    public boolean containsCaptcha(int sessionId) {
        return activeCaptchas.containsKey(sessionId);
    }

    public CaptchaResult getCaptcha(int sessionId) {
        CaptchaSession session = activeCaptchas.get(sessionId);
        if (session == null) {
            return null;
        }
        return session.captchaResult;
    }

    public void handlePlayerCaptchaInput(Player player, char input) {
        int sessionId = player.getSession().getUserId();
        CaptchaSession session = activeCaptchas.get(sessionId);
        if (session == null) {
            return;
        }
        CaptchaResult captchaResult = session.captchaResult;
        if (captchaResult.isDisposed()) {
            return;
        }
        boolean completed = captchaResult.addInput(input);
        if (completed) {
            removeCaptcha(sessionId);
            Service.gI().sendFinishCaptcha(player);
        } else if (captchaResult.captchaEnterd.length() == 6) {
            if (captchaResult.captchaFailCount >= 10) {
                captchaResult.captchaFailCount = 0;
                generateCaptchaForPlayer(player);
            } else {
                captchaResult.captchaFailCount++;
            }
        }
    }

    public void removeCaptcha(int sessionId) {
        CaptchaSession session = activeCaptchas.get(sessionId);
        if (session != null) {
            removeSessionAndCleanup(sessionId, session);
        }
    }

    private void removeSessionAndCleanup(int sessionId, CaptchaSession session) {
        activeCaptchas.remove(sessionId);
        session.dispose();
    }
}
