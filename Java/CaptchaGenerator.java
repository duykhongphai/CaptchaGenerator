package captcha;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class CaptchaGenerator {

    private static final int BASE_WIDTH = 128;
    private static final int BASE_HEIGHT = 128;
    private static final Color BACKGROUND_COLOR = Color.WHITE;
    private static final Color GRAY_AREA_COLOR = new Color(108, 109, 103);
    private static final Color TEXT_COLOR = new Color(129, 129, 129);
    private static final String CHARS = "qwertyuioopasdfghjklzxcvbnm0123456789";
    private static final String UPPER_CASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER_CASE = "abcdefghijklmnopqrstuvwxyz";
    private static final String NUMBERS = "0123456789";
    private static final String SPECIAL_CHARS = "!@#$%&*+=?";
    private static final String ALL_CHARS = UPPER_CASE + LOWER_CASE + NUMBERS + SPECIAL_CHARS;
    private static final String SHADOW_CHARS = ".,;:'\"!?@#$%^&*()_+-=[]{}|\\";
    private static final Color[] BRIGHT_COLORS = {
        Color.YELLOW, Color.CYAN, Color.MAGENTA, Color.GREEN, Color.ORANGE, Color.PINK,
        new Color(255, 255, 0), new Color(0, 255, 255), new Color(255, 0, 255),
        new Color(0, 255, 0), new Color(255, 165, 0), new Color(255, 105, 180)
    };

    private static class CaptchaContext {

        String[] keys;
        int[] values;
        String decryptionKey;
        String displayString;

        CaptchaContext(String[] keys, int[] values, String decryptionKey, String displayString) {
            this.keys = keys;
            this.values = values;
            this.decryptionKey = decryptionKey;
            this.displayString = displayString;
        }
    }

    public static CaptchaResult createCaptchaImage(int zoomLevel) {
        if (zoomLevel < 1 || zoomLevel > 4) {
            throw new IllegalArgumentException("Zoom level must be between 1 and 4");
        }

        int WIDTH = BASE_WIDTH * zoomLevel;
        int HEIGHT = BASE_HEIGHT * zoomLevel;

        BufferedImage image;
        Graphics2D g2d = null;
        try {
            image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_BYTE_INDEXED);
            g2d = image.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_SPEED);
            CaptchaContext context = generateCaptchaData();
            g2d.setColor(BACKGROUND_COLOR);
            g2d.fillRect(0, 0, WIDTH, HEIGHT);
            g2d.setColor(GRAY_AREA_COLOR);
            g2d.fillRect(0, 0, WIDTH, 32 * zoomLevel);
            g2d.fillRect(0, 64 * zoomLevel, WIDTH, 54 * zoomLevel);
            drawStringFillWidth(g2d, 0, 0, WIDTH, 32 * zoomLevel, true, false, zoomLevel);
            drawStringFillWidth(g2d, 0, 64 * zoomLevel, WIDTH, 54 * zoomLevel, false, true, zoomLevel);
            drawKeyValuePairs(g2d, 0, 64 * zoomLevel, WIDTH, 54 * zoomLevel, zoomLevel, context);

            drawTopAreaString(g2d, 0, 0, WIDTH, 32 * zoomLevel, zoomLevel, context);
            drawRandomLines(g2d, 0, 0, WIDTH, 32 * zoomLevel, true, zoomLevel);
            drawRandomLines(g2d, 0, 64 * zoomLevel, WIDTH, 54 * zoomLevel, false, zoomLevel);
            addDistortionEffects(g2d, WIDTH, HEIGHT, zoomLevel);
            addImageCorruption(g2d, image, WIDTH, HEIGHT, zoomLevel);
            byte[] imageBytes = compressImage(image);
            int[] valuesCopy = context.values.clone();
            return new CaptchaResult(imageBytes, valuesCopy, context.decryptionKey);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate CAPTCHA", e);
        } finally {
            if (g2d != null) {
                g2d.dispose();
            }
        }
    }

    private static CaptchaContext generateCaptchaData() {
        Random rand = ThreadLocalRandom.current();
        int pairCount = 5 + rand.nextInt(2);

        String[] keys = generateRandomKeys(pairCount);
        int[] values = generateRandomValues(pairCount);
        StringBuilder displayResult = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            int keyIndex = i % keys.length;
            displayResult.append(keys[keyIndex]);
        }
        char[] displayChars = displayResult.toString().toCharArray();
        for (int i = 0; i < displayChars.length; i++) {
            int randomIndex = rand.nextInt(displayChars.length);
            char temp = displayChars[i];
            displayChars[i] = displayChars[randomIndex];
            displayChars[randomIndex] = temp;
        }
        String displayString = new String(displayChars);
        Map<String, Integer> keyValueMap = new HashMap<>();
        for (int i = 0; i < keys.length; i++) {
            keyValueMap.put(keys[i], values[i]);
        }
        StringBuilder decryptionResult = new StringBuilder();
        for (char ch : displayChars) {
            String key = String.valueOf(ch);
            Integer value = keyValueMap.get(key);
            if (value != null) {
                decryptionResult.append(value);
            } else {
                decryptionResult.append("0");
            }
        }
        return new CaptchaContext(keys, values, decryptionResult.toString(), displayString);
    }

    private static byte[] compressImage(BufferedImage image) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageOutputStream ios = null;
        ImageWriter writer = null;
        try {
            ios = ImageIO.createImageOutputStream(baos);
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("png");
            writer = writers.next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(0.1f);
            }
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
            return baos.toByteArray();
        } finally {
            if (writer != null) {
                writer.dispose();
            }
            if (ios != null) {
                ios.close();
            }
        }
    }

    private static void drawKeyValuePairs(Graphics2D g2d, int x, int y, int width, int height,
            int zoomLevel, CaptchaContext context) {
        Random rand = ThreadLocalRandom.current();
        String[] keys = context.keys;
        int[] values = context.values;
        int pairCount = keys.length;
        Color[] colors = generateRandomColors(pairCount);
        Font keyValueFont = new Font("Arial", Font.PLAIN, 13 * zoomLevel);
        g2d.setFont(keyValueFont);
        FontMetrics fm = g2d.getFontMetrics();
        int col1Count = (pairCount + 1) / 2;
        int col2Count = pairCount - col1Count;
        int col1X = x + 10 * zoomLevel;
        int col2X = x + width / 2 + 10 * zoomLevel;
        int pairSpacing = 18 * zoomLevel;
        int startY1 = y + 15 * zoomLevel;
        for (int i = 0; i < col1Count; i++) {
            String keyValueText = keys[i] + "->" + values[i];
            int posY = startY1 + i * pairSpacing + rand.nextInt(3 * zoomLevel);
            posY = Math.max(y + fm.getAscent(), Math.min(posY, y + height - 5 * zoomLevel));
            drawKeyValuePairWithCorruption(g2d, keyValueText, col1X, posY, colors[i], fm, zoomLevel);
        }
        int startY2 = y + 15 * zoomLevel;
        for (int i = 0; i < col2Count; i++) {
            int index = col1Count + i;
            String keyValueText = keys[index] + "->" + values[index];
            int posY = startY2 + i * pairSpacing + rand.nextInt(3 * zoomLevel);
            posY = Math.max(y + fm.getAscent(), Math.min(posY, y + height - 5 * zoomLevel));
            drawKeyValuePairWithCorruption(g2d, keyValueText, col2X, posY, colors[index], fm, zoomLevel);
        }
    }

    private static void drawTopAreaString(Graphics2D g2d, int x, int y, int width, int height,
            int zoomLevel, CaptchaContext context) {
        String displayString = context.displayString;
        if (displayString == null) {
            return;
        }

        Font topFont = new Font("Arial", Font.BOLD, 20 * zoomLevel);
        g2d.setFont(topFont);
        FontMetrics fm = g2d.getFontMetrics();
        int totalWidth = 0;
        for (char c : displayString.toCharArray()) {
            totalWidth += fm.charWidth(c);
        }
        int totalSpacing = width - totalWidth - 10 * zoomLevel;
        int spacingPerChar = Math.max(2 * zoomLevel, totalSpacing / (displayString.length() - 1));
        int currentX = x + 5 * zoomLevel;
        int baseY = y + fm.getAscent() + 5 * zoomLevel;
        Random rand = ThreadLocalRandom.current();
        for (int i = 0; i < displayString.length(); i++) {
            char ch = displayString.charAt(i);
            Color charColor = BRIGHT_COLORS[rand.nextInt(BRIGHT_COLORS.length)];
            g2d.setColor(charColor);
            int charY = baseY + rand.nextInt(6 * zoomLevel) - 3 * zoomLevel;
            Graphics2D g2dRotated = (Graphics2D) g2d.create();
            try {
                double angle = rand.nextBoolean()
                        ? 0.1 + rand.nextDouble() * 0.3
                        : -0.1 - rand.nextDouble() * 0.3;

                g2dRotated.rotate(angle, currentX + fm.charWidth(ch) / 2, charY);
                g2dRotated.drawString(String.valueOf(ch), currentX, charY);
            } finally {
                g2dRotated.dispose();
            }
            Composite originalComposite = g2d.getComposite();
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
            g2d.setColor(charColor.darker());
            g2d.drawString(String.valueOf(ch), currentX + zoomLevel, charY + zoomLevel);
            g2d.setComposite(originalComposite);

            currentX += fm.charWidth(ch) + spacingPerChar;
        }
    }

    private static void drawStringFillWidth(Graphics2D g2d, int x, int y, int targetWidth, int maxHeight,
            boolean isBold, boolean isBottomArea, int zoomLevel) {
        int baseFontSize = isBottomArea ? 12 : 9;
        int fontSize = baseFontSize * zoomLevel;
        Font font;
        FontMetrics fm;
        int actualCharHeight;
        int numberOfLines;

        do {
            int fontStyle = isBold ? Font.BOLD : Font.PLAIN;
            font = new Font("Arial", fontStyle, fontSize);
            fm = g2d.getFontMetrics(font);
            actualCharHeight = fm.getAscent();
            numberOfLines = maxHeight / actualCharHeight;
            fontSize++;
        } while (numberOfLines < 3 && fontSize < 25 * zoomLevel);
        fontSize--;
        int fontStyle = isBold ? Font.BOLD : Font.PLAIN;
        font = new Font("Arial", fontStyle, fontSize);
        g2d.setFont(font);
        g2d.setColor(TEXT_COLOR);
        fm = g2d.getFontMetrics();
        actualCharHeight = fm.getAscent();
        numberOfLines = (maxHeight / actualCharHeight) + (isBottomArea ? 4 : 2);
        int avgCharWidth = fm.charWidth('A');
        int charsPerLine = (targetWidth / avgCharWidth) + 3;
        Random rand = ThreadLocalRandom.current();
        for (int line = 0; line < numberOfLines; line++) {
            int maxOffset = isBottomArea ? actualCharHeight : actualCharHeight / 2;
            int randomOffset = rand.nextInt(Math.max(1, maxOffset)) - maxOffset / 2;
            int currentY = y + fm.getAscent() + (line * (actualCharHeight - Math.abs(randomOffset)));
            if (currentY - fm.getAscent() > y + maxHeight) {
                continue;
            }
            String lineText = randomText(charsPerLine);
            int drawX = x;
            int textIndex = 0;
            while (drawX < x + targetWidth) {
                if (textIndex >= lineText.length()) {
                    lineText = randomText(charsPerLine);
                    textIndex = 0;
                }

                char ch = lineText.charAt(textIndex);
                int charWidth = fm.charWidth(ch);

                if (isBottomArea && rand.nextInt(5) == 0) {
                    Graphics2D g2dRotated = (Graphics2D) g2d.create();
                    try {
                        double angle = (rand.nextDouble() - 0.5) * 0.3;
                        g2dRotated.rotate(angle, drawX + charWidth / 2, currentY);
                        g2dRotated.drawString(String.valueOf(ch), drawX, currentY);
                    } finally {
                        g2dRotated.dispose();
                    }
                } else {
                    if (drawX + charWidth > x + targetWidth) {
                        g2d.setClip(x, y, targetWidth, maxHeight);
                        g2d.drawString(String.valueOf(ch), drawX, currentY);
                        g2d.setClip(null);
                        break;
                    } else {
                        g2d.drawString(String.valueOf(ch), drawX, currentY);
                    }
                }
                drawX += charWidth;
                textIndex++;
            }
        }
        Composite originalComposite = g2d.getComposite();
        int extraLayers = isBottomArea ? numberOfLines : numberOfLines / 2;
        for (int i = 0; i < extraLayers; i++) {
            float alpha = isBottomArea ? 0.1f + rand.nextFloat() * 0.4f : 0.3f;
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            int randomY = y + rand.nextInt(maxHeight);
            int randomX = x + rand.nextInt(Math.max(1, targetWidth / 3));
            String randomChars = randomText(targetWidth / avgCharWidth);
            int drawX = randomX;
            for (int j = 0; j < randomChars.length() && drawX < x + targetWidth; j++) {
                char ch = randomChars.charAt(j);
                if (isBottomArea && rand.nextInt(4) == 0) {
                    Graphics2D g2dScaled = (Graphics2D) g2d.create();
                    try {
                        float scale = 0.7f + rand.nextFloat() * 0.6f;
                        g2dScaled.scale(scale, scale);
                        g2dScaled.drawString(String.valueOf(ch), (int) (drawX / scale), (int) (randomY / scale));
                    } finally {
                        g2dScaled.dispose();
                    }
                } else {
                    g2d.drawString(String.valueOf(ch), drawX, randomY);
                }

                drawX += fm.charWidth(ch);
            }
        }
        g2d.setComposite(originalComposite);
    }

    private static void drawKeyValuePairWithCorruption(Graphics2D g2d, String text, int x, int y,
            Color color, FontMetrics fm, int zoomLevel) {
        Random rand = ThreadLocalRandom.current();
        g2d.setColor(color);
        g2d.drawString(text, x, y);
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            int charX = x;

            for (int j = 0; j < i; j++) {
                charX += fm.charWidth(text.charAt(j));
            }
            if (rand.nextInt(3) == 0) {
                int offsetX = (rand.nextInt(3) - 1) * zoomLevel;
                int offsetY = (rand.nextInt(3) - 1) * zoomLevel;

                Composite originalComposite = g2d.getComposite();
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
                g2d.setColor(color.darker());
                g2d.drawString(String.valueOf(ch), charX + offsetX, y + offsetY);
                g2d.setComposite(originalComposite);
                g2d.setColor(color);
            }
        }
        for (int i = 0; i < 5 * zoomLevel; i++) {
            int noiseX = x + rand.nextInt(fm.stringWidth(text) + 10 * zoomLevel) - 5 * zoomLevel;
            int noiseY = y + rand.nextInt(fm.getHeight()) - fm.getAscent();

            Color noiseColor = new Color(
                    Math.max(0, Math.min(255, color.getRed() + rand.nextInt(60) - 30)),
                    Math.max(0, Math.min(255, color.getGreen() + rand.nextInt(60) - 30)),
                    Math.max(0, Math.min(255, color.getBlue() + rand.nextInt(60) - 30)),
                    100
            );

            g2d.setColor(noiseColor);
            g2d.fillRect(noiseX, noiseY, zoomLevel, zoomLevel);
        }
        Composite originalComposite = g2d.getComposite();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.15f));
        g2d.setColor(Color.WHITE);
        g2d.drawString(text, x + zoomLevel, y);
        g2d.setComposite(originalComposite);
    }

    private static void drawRandomLines(Graphics2D g2d, int x, int y, int width, int height,
            boolean isTopArea, int zoomLevel) {
        Random rand = ThreadLocalRandom.current();
        int lineCount = 5 + rand.nextInt(6);

        for (int i = 0; i < lineCount; i++) {
            if (isTopArea) {
                g2d.setStroke(new BasicStroke(1.2f * zoomLevel));

                Color grayLineColor = new Color(
                        GRAY_AREA_COLOR.getRed() + 40,
                        GRAY_AREA_COLOR.getGreen() + 40,
                        GRAY_AREA_COLOR.getBlue() + 40,
                        180 + rand.nextInt(75)
                );
                g2d.setColor(grayLineColor);

                int lineY = y + rand.nextInt(height);
                int startX = x + rand.nextInt(width / 4);
                int endX = x + width - rand.nextInt(width / 4);

                g2d.drawLine(startX, lineY, endX, lineY);

            } else {
                g2d.setStroke(new BasicStroke(1.0f * zoomLevel));

                Color lineColor = new Color(
                        rand.nextInt(256),
                        rand.nextInt(256),
                        rand.nextInt(256),
                        150 + rand.nextInt(106)
                );

                g2d.setColor(lineColor);

                int x1 = rand.nextInt(width);
                int y1 = y + rand.nextInt(height);

                double angle = rand.nextDouble() * 2 * Math.PI;
                int x2 = x1 + (int) (width * Math.cos(angle));
                int y2 = y1 + (int) (width * Math.sin(angle));

                g2d.drawLine(x1, y1, x2, y2);
            }
        }

        g2d.setStroke(new BasicStroke(1.0f * zoomLevel));
    }

    private static void addDistortionEffects(Graphics2D g2d, int WIDTH, int HEIGHT, int zoomLevel) {
        Random rand = ThreadLocalRandom.current();

        g2d.setStroke(new BasicStroke(0.8f * zoomLevel));
        for (int i = 0; i < 3; i++) {
            Color wavyColor = new Color(rand.nextInt(256), rand.nextInt(256), rand.nextInt(256), 80);
            g2d.setColor(wavyColor);

            int startY = rand.nextInt(HEIGHT);
            int amplitude = (5 + rand.nextInt(10)) * zoomLevel;
            int frequency = (20 + rand.nextInt(20)) * zoomLevel;

            for (int x = 0; x < WIDTH - 1; x++) {
                int y1 = startY + (int) (amplitude * Math.sin(2 * Math.PI * x / frequency));
                int y2 = startY + (int) (amplitude * Math.sin(2 * Math.PI * (x + 1) / frequency));
                g2d.drawLine(x, y1, x + 1, y2);
            }
        }

        for (int i = 0; i < 30 * zoomLevel; i++) {
            Color dotColor = new Color(rand.nextInt(256), rand.nextInt(256), rand.nextInt(256), 60 + rand.nextInt(100));
            g2d.setColor(dotColor);

            int x = rand.nextInt(WIDTH);
            int y = rand.nextInt(HEIGHT);
            int size = (1 + rand.nextInt(3)) * zoomLevel;

            g2d.fillOval(x, y, size, size);
        }

        Font shadowFont = new Font("Arial", Font.BOLD, 8 * zoomLevel);
        g2d.setFont(shadowFont);

        for (int i = 0; i < 15 * zoomLevel; i++) {
            Composite shadowComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.1f + rand.nextFloat() * 0.2f);
            g2d.setComposite(shadowComposite);

            Color shadowColor = new Color(rand.nextInt(150), rand.nextInt(150), rand.nextInt(150));
            g2d.setColor(shadowColor);

            char ch = SHADOW_CHARS.charAt(rand.nextInt(SHADOW_CHARS.length()));
            int x = rand.nextInt(WIDTH - 10 * zoomLevel);
            int y = 10 * zoomLevel + rand.nextInt(HEIGHT - 20 * zoomLevel);

            g2d.drawString(String.valueOf(ch), x, y);
        }
    }

    private static void addImageCorruption(Graphics2D g2d, BufferedImage image, int WIDTH, int HEIGHT, int zoomLevel) {
        Random rand = ThreadLocalRandom.current();
        for (int i = 0; i < 25 * zoomLevel; i++) {
            int x = rand.nextInt(WIDTH);
            int y = rand.nextInt(HEIGHT);
            int corruptionSize = (rand.nextInt(2) + 1) * zoomLevel;

            Color originalColor = new Color(image.getRGB(x, y), true);
            int r = Math.max(0, Math.min(255, originalColor.getRed() + rand.nextInt(60) - 30));
            int g = Math.max(0, Math.min(255, originalColor.getGreen() + rand.nextInt(60) - 30));
            int b = Math.max(0, Math.min(255, originalColor.getBlue() + rand.nextInt(60) - 30));

            g2d.setColor(new Color(r, g, b));
            g2d.fillRect(x, y, corruptionSize, corruptionSize);
        }
    }

    private static String[] generateRandomKeys(int count) {
        String[] keys = new String[count];
        Set<String> usedKeys = new HashSet<>();
        Random rand = ThreadLocalRandom.current();

        for (int i = 0; i < count; i++) {
            String newKey;
            do {
                newKey = String.valueOf(ALL_CHARS.charAt(rand.nextInt(ALL_CHARS.length())));
            } while (usedKeys.contains(newKey));

            keys[i] = newKey;
            usedKeys.add(newKey);
        }
        return keys;
    }

    private static int[] generateRandomValues(int count) {
        int[] values = new int[count];
        Set<Integer> usedValues = new HashSet<>();
        Random rand = ThreadLocalRandom.current();

        for (int i = 0; i < count; i++) {
            int newValue;
            do {
                newValue = rand.nextInt(10);
            } while (usedValues.contains(newValue));

            values[i] = newValue;
            usedValues.add(newValue);
        }
        return values;
    }

    private static Color[] generateRandomColors(int count) {
        Color[] colors = new Color[count];
        Random rand = ThreadLocalRandom.current();

        for (int i = 0; i < count; i++) {
            colors[i] = BRIGHT_COLORS[rand.nextInt(BRIGHT_COLORS.length)];
        }
        return colors;
    }

    private static String randomText(int length) {
        StringBuilder sb = new StringBuilder();
        Random rand = ThreadLocalRandom.current();
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(rand.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
