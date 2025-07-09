using SkiaSharp;
using System;
using System.Buffers;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.IO;
using System.Linq;
using System.Text;

namespace CaptchaGenerator
{
    public static class CaptchaGenerator
    {
        private const int BASE_WIDTH = 128;
        private const int BASE_HEIGHT = 128;
        private static readonly SKColor BACKGROUND_COLOR = SKColors.White;
        private static readonly SKColor GRAY_AREA_COLOR = new(108, 109, 103);
        private static readonly SKColor TEXT_COLOR = new(129, 129, 129);
        private const string CHARS = "qwertyuioopasdfghjklzxcvbnm0123456789";
        private const string UPPER_CASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        private const string LOWER_CASE = "abcdefghijklmnopqrstuvwxyz";
        private const string NUMBERS = "0123456789";
        private const string SPECIAL_CHARS = "!@#$%&*+=?";
        private const string ALL_CHARS = UPPER_CASE + LOWER_CASE + NUMBERS + SPECIAL_CHARS;
        private const string SHADOW_CHARS = ".,;:'\"!?@#$%^&*()_+-=[]{}|\\";

        private static readonly SKColor[] BRIGHT_COLORS = {
            SKColors.Yellow, SKColors.Cyan, SKColors.Magenta, SKColors.Green,
            SKColors.Orange, SKColors.Pink, new(255, 255, 0), new(0, 255, 255),
            new(255, 0, 255), new(0, 255, 0), new(255, 165, 0), new(255, 105, 180)
        };

        private static readonly ArrayPool<char> CharPool = ArrayPool<char>.Shared;
        private static readonly ThreadLocal<Random> ThreadLocalRandom = new(() => new Random());

        private ref struct CaptchaContext
        {
            public ReadOnlySpan<string> Keys { get; init; }
            public ReadOnlySpan<int> Values { get; init; }
            public ReadOnlySpan<char> DecryptionKey { get; set; }
            public ReadOnlySpan<char> DisplayString { get; set; }
        }

        public static CaptchaResult CreateCaptchaImage(int zoomLevel)
        {
            if (zoomLevel is < 1 or > 4)
                throw new ArgumentOutOfRangeException(nameof(zoomLevel), "Zoom level must be between 1 and 4");

            int width = BASE_WIDTH * zoomLevel;
            int height = BASE_HEIGHT * zoomLevel;

            var imageInfo = new SKImageInfo(width, height, SKColorType.Rgba8888, SKAlphaType.Premul);

            using var surface = SKSurface.Create(imageInfo);
            var canvas = surface.Canvas;
            canvas.Clear(BACKGROUND_COLOR);
            var context = GenerateCaptchaData();
            using var grayPaint = new SKPaint { Color = GRAY_AREA_COLOR, Style = SKPaintStyle.Fill };
            canvas.DrawRect(0, 0, width, 32 * zoomLevel, grayPaint);
            canvas.DrawRect(0, 64 * zoomLevel, width, 54 * zoomLevel, grayPaint);
            DrawStringFillWidth(canvas, 0, 0, width, 32 * zoomLevel, true, false, zoomLevel);
            DrawStringFillWidth(canvas, 0, 64 * zoomLevel, width, 54 * zoomLevel, false, true, zoomLevel);
            DrawKeyValuePairs(canvas, 0, 64 * zoomLevel, width, 54 * zoomLevel, zoomLevel, context);
            DrawTopAreaString(canvas, 0, 0, width, 32 * zoomLevel, zoomLevel, context);
            DrawRandomLines(canvas, 0, 0, width, 32 * zoomLevel, true, zoomLevel);
            DrawRandomLines(canvas, 0, 64 * zoomLevel, width, 54 * zoomLevel, false, zoomLevel);
            AddDistortionEffects(canvas, width, height, zoomLevel);
            using var image = surface.Snapshot();
            using var data = image.Encode(SKEncodedImageFormat.Png, 85);
            var imageBytes = data.ToArray();
            var valuesCopy = context.Values.ToArray();
            var decryptionKey = new string(context.DecryptionKey);
            return new CaptchaResult(imageBytes, valuesCopy, decryptionKey);
        }

        private static CaptchaContext GenerateCaptchaData()
        {
            var rand = ThreadLocalRandom.Value!;
            int pairCount = 5 + rand.Next(2);

            var keys = GenerateRandomKeys(pairCount);
            var values = GenerateRandomValues(pairCount);
            Span<char> displayChars = stackalloc char[6];
            for (int i = 0; i < 6; i++)
            {
                int keyIndex = i % keys.Length;
                displayChars[i] = keys[keyIndex][0];
            }
            for (int i = displayChars.Length - 1; i > 0; i--)
            {
                int j = rand.Next(i + 1);
                (displayChars[i], displayChars[j]) = (displayChars[j], displayChars[i]);
            }
            var keyValueMap = new Dictionary<char, int>(keys.Length);
            for (int i = 0; i < keys.Length; i++)
            {
                keyValueMap[keys[i][0]] = values[i];
            }
            Span<char> decryptionChars = stackalloc char[6];
            for (int i = 0; i < displayChars.Length; i++)
            {
                char ch = displayChars[i];
                decryptionChars[i] = (char)('0' + (keyValueMap.TryGetValue(ch, out int value) ? value : 0));
            }
            CaptchaContext captchaContext = new CaptchaContext
            {
                Keys = keys.AsSpan(),
                Values = values.AsSpan(),
            };
            captchaContext.DecryptionKey = new string(decryptionChars).AsSpan();
            captchaContext.DisplayString = new string(displayChars).AsSpan();
            return captchaContext;
        }

        private static void DrawKeyValuePairs(SKCanvas canvas, int x, int y, int width, int height,
            int zoomLevel, in CaptchaContext context)
        {
            var rand = ThreadLocalRandom.Value!;
            var keys = context.Keys;
            var values = context.Values;
            int pairCount = keys.Length;
            var colors = GenerateRandomColors(pairCount);

            using var paint = new SKPaint
            {
                Color = TEXT_COLOR,
                TextSize = 13 * zoomLevel,
                Typeface = SKTypeface.FromFamilyName("Arial"),
                IsAntialias = false
            };

            int col1Count = (pairCount + 1) / 2;
            int col2Count = pairCount - col1Count;
            int col1X = x + 10 * zoomLevel;
            int col2X = x + width / 2 + 10 * zoomLevel;
            int pairSpacing = 18 * zoomLevel;
            int startY1 = y + 15 * zoomLevel;
            for (int i = 0; i < col1Count; i++)
            {
                string keyValueText = $"{keys[i]}->{values[i]}";
                int posY = startY1 + i * pairSpacing + rand.Next(3 * zoomLevel);
                posY = Math.Max(y + (int)paint.TextSize, Math.Min(posY, y + height - 5 * zoomLevel));
                DrawKeyValuePairWithCorruption(canvas, keyValueText, col1X, posY, colors[i], paint, zoomLevel);
            }
            int startY2 = y + 15 * zoomLevel;
            for (int i = 0; i < col2Count; i++)
            {
                int index = col1Count + i;
                string keyValueText = $"{keys[index]}->{values[index]}";
                int posY = startY2 + i * pairSpacing + rand.Next(3 * zoomLevel);
                posY = Math.Max(y + (int)paint.TextSize, Math.Min(posY, y + height - 5 * zoomLevel));
                DrawKeyValuePairWithCorruption(canvas, keyValueText, col2X, posY, colors[index], paint, zoomLevel);
            }
        }

        private static void DrawTopAreaString(SKCanvas canvas, int x, int y, int width, int height,
            int zoomLevel, in CaptchaContext context)
        {
            var displayString = context.DisplayString;
            if (displayString.IsEmpty) return;

            using var paint = new SKPaint
            {
                TextSize = 20 * zoomLevel,
                Typeface = SKTypeface.FromFamilyName("Arial", SKFontStyle.Bold),
                IsAntialias = false
            };

            var rand = ThreadLocalRandom.Value!;
            float totalWidth = 0;

            foreach (char c in displayString)
            {
                totalWidth += paint.MeasureText(c.ToString());
            }
            float totalSpacing = width - totalWidth - 10 * zoomLevel;
            float spacingPerChar = Math.Max(2 * zoomLevel, totalSpacing / (displayString.Length - 1));
            float currentX = x + 5 * zoomLevel;
            float baseY = y + paint.TextSize + 5 * zoomLevel;

            for (int i = 0; i < displayString.Length; i++)
            {
                char ch = displayString[i];
                paint.Color = BRIGHT_COLORS[rand.Next(BRIGHT_COLORS.Length)];
                float charY = baseY + rand.Next(6 * zoomLevel) - 3 * zoomLevel;

                canvas.Save();
                float angle = rand.NextSingle() > 0.5f
                    ? 0.1f + rand.NextSingle() * 0.3f
                    : -0.1f - rand.NextSingle() * 0.3f;

                float charWidth = paint.MeasureText(ch.ToString());
                canvas.RotateRadians(angle, currentX + charWidth / 2, charY);
                canvas.DrawText(ch.ToString(), currentX, charY, paint);
                canvas.Restore();
                paint.Color = paint.Color.WithAlpha(76);
                canvas.DrawText(ch.ToString(), currentX + zoomLevel, charY + zoomLevel, paint);
                currentX += charWidth + spacingPerChar;
            }
        }

        private static void DrawStringFillWidth(SKCanvas canvas, int x, int y, int targetWidth, int maxHeight,
            bool isBold, bool isBottomArea, int zoomLevel)
        {
            int baseFontSize = isBottomArea ? 12 : 9;
            float fontSize = baseFontSize * zoomLevel;

            using var paint = new SKPaint
            {
                Color = TEXT_COLOR,
                TextSize = fontSize,
                Typeface = SKTypeface.FromFamilyName("Arial", isBold ? SKFontStyle.Bold : SKFontStyle.Normal),
                IsAntialias = false
            };

            float actualCharHeight = paint.TextSize;
            int numberOfLines = (int)(maxHeight / actualCharHeight) + (isBottomArea ? 4 : 2);
            float avgCharWidth = paint.MeasureText("A");
            int charsPerLine = (int)(targetWidth / avgCharWidth) + 3;
            var rand = ThreadLocalRandom.Value!;

            var textBuffer = CharPool.Rent(charsPerLine);
            try
            {
                for (int line = 0; line < numberOfLines; line++)
                {
                    int maxOffset = isBottomArea ? (int)actualCharHeight : (int)actualCharHeight / 2;
                    int randomOffset = rand.Next(Math.Max(1, maxOffset)) - maxOffset / 2;
                    float currentY = y + paint.TextSize + (line * (actualCharHeight - Math.Abs(randomOffset)));

                    if (currentY - paint.TextSize > y + maxHeight) continue;

                    GenerateRandomText(textBuffer.AsSpan(0, charsPerLine));
                    var lineText = new string(textBuffer, 0, charsPerLine);
                    float drawX = x;

                    foreach (char ch in lineText)
                    {
                        if (drawX >= x + targetWidth) break;

                        float charWidth = paint.MeasureText(ch.ToString());

                        if (isBottomArea && rand.Next(5) == 0)
                        {
                            canvas.Save();
                            float angle = (rand.NextSingle() - 0.5f) * 0.3f;
                            canvas.RotateRadians(angle, drawX + charWidth / 2, currentY);
                            canvas.DrawText(ch.ToString(), drawX, currentY, paint);
                            canvas.Restore();
                        }
                        else
                        {
                            canvas.DrawText(ch.ToString(), drawX, currentY, paint);
                        }

                        drawX += charWidth;
                    }
                }
            }
            finally
            {
                CharPool.Return(textBuffer);
            }
        }

        private static void DrawKeyValuePairWithCorruption(SKCanvas canvas, string text, int x, int y,
            SKColor color, SKPaint basePaint, int zoomLevel)
        {
            var rand = ThreadLocalRandom.Value!;

            using var paint = basePaint.Clone();
            paint.Color = color;
            canvas.DrawText(text, x, y, paint);
            for (int i = 0; i < text.Length; i++)
            {
                if (rand.Next(3) != 0) continue;

                char ch = text[i];
                float charX = x + paint.MeasureText(text.AsSpan(0, i));
                int offsetX = (rand.Next(3) - 1) * zoomLevel;
                int offsetY = (rand.Next(3) - 1) * zoomLevel;

                paint.Color = color.WithAlpha(102);
                canvas.DrawText(ch.ToString(), charX + offsetX, y + offsetY, paint);
            }
            using var noisePaint = new SKPaint { Style = SKPaintStyle.Fill };
            for (int i = 0; i < 5 * zoomLevel; i++)
            {
                float textWidth = paint.MeasureText(text);
                int noiseX = x + rand.Next((int)textWidth + 10 * zoomLevel) - 5 * zoomLevel;
                int noiseY = y + rand.Next((int)paint.TextSize) - (int)paint.TextSize;

                noisePaint.Color = new SKColor(
                    (byte)Math.Max(0, Math.Min(255, color.Red + rand.Next(60) - 30)),
                    (byte)Math.Max(0, Math.Min(255, color.Green + rand.Next(60) - 30)),
                    (byte)Math.Max(0, Math.Min(255, color.Blue + rand.Next(60) - 30)),
                    100);

                canvas.DrawRect(noiseX, noiseY, zoomLevel, zoomLevel, noisePaint);
            }
        }

        private static void DrawRandomLines(SKCanvas canvas, int x, int y, int width, int height,
            bool isTopArea, int zoomLevel)
        {
            var rand = ThreadLocalRandom.Value!;
            int lineCount = 5 + rand.Next(6);

            using var paint = new SKPaint { Style = SKPaintStyle.Stroke, IsAntialias = false };

            for (int i = 0; i < lineCount; i++)
            {
                if (isTopArea)
                {
                    paint.StrokeWidth = 1.2f * zoomLevel;
                    paint.Color = new SKColor(
                        (byte)(GRAY_AREA_COLOR.Red + 40),
                        (byte)(GRAY_AREA_COLOR.Green + 40),
                        (byte)(GRAY_AREA_COLOR.Blue + 40),
                        (byte)(180 + rand.Next(75)));

                    int lineY = y + rand.Next(height);
                    int startX = x + rand.Next(width / 4);
                    int endX = x + width - rand.Next(width / 4);

                    canvas.DrawLine(startX, lineY, endX, lineY, paint);
                }
                else
                {
                    paint.StrokeWidth = 1.0f * zoomLevel;
                    paint.Color = new SKColor(
                        (byte)rand.Next(256),
                        (byte)rand.Next(256),
                        (byte)rand.Next(256),
                        (byte)(150 + rand.Next(106)));

                    int x1 = rand.Next(width);
                    int y1 = y + rand.Next(height);

                    double angle = rand.NextDouble() * 2 * Math.PI;
                    int x2 = x1 + (int)(width * Math.Cos(angle));
                    int y2 = y1 + (int)(width * Math.Sin(angle));

                    canvas.DrawLine(x1, y1, x2, y2, paint);
                }
            }
        }

        private static void AddDistortionEffects(SKCanvas canvas, int width, int height, int zoomLevel)
        {
            var rand = ThreadLocalRandom.Value!;
            using var wavePaint = new SKPaint { Style = SKPaintStyle.Stroke, StrokeWidth = 0.8f * zoomLevel };
            for (int i = 0; i < 3; i++)
            {
                wavePaint.Color = new SKColor((byte)rand.Next(256), (byte)rand.Next(256), (byte)rand.Next(256), 80);
                int startY = rand.Next(height);
                int amplitude = (5 + rand.Next(10)) * zoomLevel;
                int frequency = (20 + rand.Next(20)) * zoomLevel;

                using var path = new SKPath();
                for (int x = 0; x < width - 1; x++)
                {
                    float y1 = startY + amplitude * MathF.Sin(2 * MathF.PI * x / frequency);
                    if (x == 0) path.MoveTo(x, y1);
                    else path.LineTo(x, y1);
                }
                canvas.DrawPath(path, wavePaint);
            }
            using var dotPaint = new SKPaint { Style = SKPaintStyle.Fill };
            for (int i = 0; i < 30 * zoomLevel; i++)
            {
                dotPaint.Color = new SKColor((byte)rand.Next(256), (byte)rand.Next(256), (byte)rand.Next(256), (byte)(60 + rand.Next(100)));
                int x = rand.Next(width);
                int y = rand.Next(height);
                int size = (1 + rand.Next(3)) * zoomLevel;
                canvas.DrawCircle(x, y, size, dotPaint);
            }
        }

        private static string[] GenerateRandomKeys(int count)
        {
            var keys = new string[count];
            var usedKeys = new HashSet<string>(count);
            var rand = ThreadLocalRandom.Value!;

            for (int i = 0; i < count; i++)
            {
                string newKey;
                do
                {
                    newKey = ALL_CHARS[rand.Next(ALL_CHARS.Length)].ToString();
                } while (usedKeys.Contains(newKey));

                keys[i] = newKey;
                usedKeys.Add(newKey);
            }
            return keys;
        }

        private static int[] GenerateRandomValues(int count)
        {
            var values = new int[count];
            var usedValues = new HashSet<int>(count);
            var rand = ThreadLocalRandom.Value!;

            for (int i = 0; i < count; i++)
            {
                int newValue;
                do
                {
                    newValue = rand.Next(10);
                } while (usedValues.Contains(newValue));

                values[i] = newValue;
                usedValues.Add(newValue);
            }
            return values;
        }

        private static SKColor[] GenerateRandomColors(int count)
        {
            var colors = new SKColor[count];
            var rand = ThreadLocalRandom.Value!;

            for (int i = 0; i < count; i++)
            {
                colors[i] = BRIGHT_COLORS[rand.Next(BRIGHT_COLORS.Length)];
            }
            return colors;
        }

        private static void GenerateRandomText(Span<char> buffer)
        {
            var rand = ThreadLocalRandom.Value!;
            for (int i = 0; i < buffer.Length; i++)
            {
                buffer[i] = CHARS[rand.Next(CHARS.Length)];
            }
        }
    }
}
