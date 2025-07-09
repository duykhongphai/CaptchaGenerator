using System;
using System.Buffers;
using System.Collections.Generic;
using System.Linq;
using System.Runtime.CompilerServices;
using System.Text;
using System.Threading.Tasks;

namespace CaptchaGenerator
{
    public sealed class CaptchaResult : IDisposable
    {
        private readonly Memory<byte> _imageBytes;
        private readonly Memory<int> _globalValues;
        private readonly string _decryptionKey;
        private readonly char[] _captchaEntered;
        private int _enteredLength;
        private volatile bool _disposed;

        private readonly object _inputLock = new();
        private readonly object _resourceLock = new();

        public int CaptchaFailCount { get; set; }

        public CaptchaResult(byte[] imageBytes, int[] globalValues, string decryptionKey)
        {
            _imageBytes = new Memory<byte>(imageBytes);
            _globalValues = new Memory<int>(globalValues);
            _decryptionKey = decryptionKey ?? throw new ArgumentNullException(nameof(decryptionKey));
            _captchaEntered = ArrayPool<char>.Shared.Rent(6);
            _enteredLength = 0;
        }

        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        public ReadOnlyMemory<byte> GetImageBytes()
        {
            ThrowIfDisposed();
            lock (_resourceLock)
            {
                return _imageBytes;
            }
        }

        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        public ReadOnlySpan<int> GetGlobalValues()
        {
            ThrowIfDisposed();
            lock (_resourceLock)
            {
                return _globalValues.Span;
            }
        }

        public string GetValue()
        {
            var values = GetGlobalValues();
            if (values.IsEmpty) return string.Empty;
            Span<char> buffer = stackalloc char[values.Length];
            for (int i = 0; i < values.Length; i++)
            {
                buffer[i] = (char)('0' + values[i]);
            }
            return new string(buffer);
        }

        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        public bool Verify()
        {
            lock (_inputLock)
            {
                if (_enteredLength != 6) return false;

                var enteredSpan = _captchaEntered.AsSpan(0, _enteredLength);
                return enteredSpan.SequenceEqual(_decryptionKey.AsSpan());
            }
        }

        public void Dispose()
        {
            if (_disposed) return;

            lock (_resourceLock)
            {
                if (_disposed) return;
                _disposed = true;
            }

            lock (_inputLock)
            {
                _enteredLength = 0;
                if (_captchaEntered != null)
                {
                    ArrayPool<char>.Shared.Return(_captchaEntered, clearArray: true);
                }
            }

            GC.SuppressFinalize(this);
        }

        public bool AddInput(char input)
        {
            if (_disposed) return false;

            lock (_inputLock)
            {
                if (_enteredLength == 6)
                {
                    _captchaEntered.AsSpan(1, 5).CopyTo(_captchaEntered.AsSpan(0, 5));
                    _enteredLength = 5;
                }

                _captchaEntered[_enteredLength++] = input;

                bool completed = _enteredLength == 6 && Verify();
                if (completed)
                {
                    Dispose();
                }
                return completed;
            }
        }

        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        public bool IsDisposed() => _disposed;

        public ReadOnlySpan<char> GetCurrentInput()
        {
            lock (_inputLock)
            {
                return _captchaEntered.AsSpan(0, _enteredLength);
            }
        }

        public void ClearInput()
        {
            lock (_inputLock)
            {
                _enteredLength = 0;
                _captchaEntered.AsSpan(0, 6).Clear();
            }
        }

        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        private void ThrowIfDisposed()
        {
            if (_disposed)
                throw new ObjectDisposedException(nameof(CaptchaResult));
        }

        ~CaptchaResult()
        {
            Dispose();
        }
    }
}
