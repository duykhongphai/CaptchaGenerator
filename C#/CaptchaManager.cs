using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Linq;
using System.Runtime.CompilerServices;
using System.Text;
using System.Threading.Tasks;

namespace CaptchaGenerator
{
    public sealed class CaptchaManager : IDisposable
    {
        private static readonly Lazy<CaptchaManager> _instance = new(() => new CaptchaManager());

        private readonly ConcurrentDictionary<int, CaptchaSession> _activeCaptchas;
        private volatile bool _disposed;
        private IService? _service;

        public static CaptchaManager Instance => _instance.Value;

        private CaptchaManager()
        {
            _activeCaptchas = new ConcurrentDictionary<int, CaptchaSession>();
        }

        public void Initialize(IService service)
        {
            _service = service ?? throw new ArgumentNullException(nameof(service));
        }

        private sealed class CaptchaSession : IDisposable
        {
            public CaptchaResult CaptchaResult { get; }
            private volatile bool _disposed;

            public CaptchaSession(CaptchaResult captchaResult)
            {
                CaptchaResult = captchaResult ?? throw new ArgumentNullException(nameof(captchaResult));
            }

            public void Dispose()
            {
                if (_disposed) return;
                _disposed = true;
                CaptchaResult?.Dispose();
            }

            public bool IsDisposed() => _disposed;
        }

        public async Task<bool> GenerateCaptchaForPlayerAsync(IPlayer player, int? customZoomLevel = null)
        {
            ThrowIfDisposed();
            ArgumentNullException.ThrowIfNull(player);

            try
            {
                int zoomLevel = customZoomLevel ?? player.Session.ZoomLevel;
                int sessionId = await GenerateCaptchaAsync(player, zoomLevel);

                var session = GetSession(sessionId);
                if (session?.CaptchaResult != null)
                {
                    var imageData = session.CaptchaResult.GetImageBytes();
                    if (_service != null)
                    {
                        await _service.SendCaptchaAsync(player, imageData);
                    }
                    return true;
                }
                return false;
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Error generating CAPTCHA for player {player.Session.UserId}: {ex.Message}");
                return false;
            }
        }

        public async Task<int> GenerateCaptchaAsync(IPlayer player, int zoomLevel)
        {
            ThrowIfDisposed();
            ArgumentNullException.ThrowIfNull(player);

            if (zoomLevel is < 1 or > 4)
                throw new ArgumentOutOfRangeException(nameof(zoomLevel));

            try
            {
                int sessionId = player.Session.UserId;
                var captchaResult = await Task.Run(() => CaptchaGenerator.CreateCaptchaImage(zoomLevel));
                var session = new CaptchaSession(captchaResult);
                if (_activeCaptchas.TryRemove(sessionId, out var oldSession))
                {
                    oldSession.Dispose();
                }

                _activeCaptchas[sessionId] = session;
                return sessionId;
            }
            catch (Exception ex)
            {
                throw new InvalidOperationException($"Failed to generate CAPTCHA for player {player.Session.UserId}", ex);
            }
        }

        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        public bool ContainsCaptcha(int sessionId)
        {
            ThrowIfDisposed();
            return _activeCaptchas.ContainsKey(sessionId);
        }

        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        public CaptchaResult? GetCaptcha(int sessionId)
        {
            ThrowIfDisposed();
            var session = GetSession(sessionId);
            return session?.CaptchaResult;
        }

        private CaptchaSession? GetSession(int sessionId)
        {
            if (_activeCaptchas.TryGetValue(sessionId, out var session) && !session.IsDisposed())
            {
                return session;
            }
            return null;
        }

        public async Task HandlePlayerCaptchaInputAsync(IPlayer player, char input)
        {
            ThrowIfDisposed();
            ArgumentNullException.ThrowIfNull(player);

            int sessionId = player.Session.UserId;
            var session = GetSession(sessionId);
            if (session?.CaptchaResult == null) return;

            var captchaResult = session.CaptchaResult;
            if (captchaResult.IsDisposed()) return;

            bool completed = captchaResult.AddInput(input);

            if (completed)
            {
                RemoveCaptcha(sessionId);
                if (_service != null)
                {
                    await _service.SendCaptchaCompletedAsync(player, true);
                }
            }
            else if (captchaResult.GetCurrentInput().Length == 6)
            {
                captchaResult.CaptchaFailCount++;
                if (captchaResult.CaptchaFailCount >= 10)
                {
                    captchaResult.CaptchaFailCount = 0;
                    await GenerateCaptchaForPlayerAsync(player);
                }
                else
                {
                    captchaResult.ClearInput();
                }
            }
        }

        public bool RemoveCaptcha(int sessionId)
        {
            ThrowIfDisposed();
            if (_activeCaptchas.TryRemove(sessionId, out var session))
            {
                session.Dispose();
                return true;
            }
            return false;
        }

        public int GetActiveCaptchaCount()
        {
            ThrowIfDisposed();
            return _activeCaptchas.Count;
        }

        public void ClearAllCaptchas()
        {
            ThrowIfDisposed();
            var sessions = _activeCaptchas.Values;
            _activeCaptchas.Clear();

            Parallel.ForEach(sessions, session => session.Dispose());
        }

        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        private void ThrowIfDisposed()
        {
            if (_disposed)
                throw new ObjectDisposedException(nameof(CaptchaManager));
        }

        public void Dispose()
        {
            if (_disposed) return;
            _disposed = true;

            ClearAllCaptchas();

            GC.SuppressFinalize(this);
        }

        ~CaptchaManager()
        {
            Dispose();
        }
    }
}
