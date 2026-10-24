#include "aaudio.h"
#include <stdlib.h>
#include <stdatomic.h>
#include <aaudio/AAudio.h>
#include <android/log.h>

struct aaudio {
    AAudioStreamBuilder *builder;
    AAudioStream *stream;
    atomic_bool muted;
    atomic_bool disconnected;
    bool was_muted;
};

static void audio_error(AAudioStream *stream, void *opaque, aaudio_result_t error)
{
    struct aaudio *ctx = opaque;
    atomic_store(&ctx->disconnected, true);
    __android_log_print(ANDROID_LOG_WARN, "PARSEC", "Audio stream error: %d", error);
}

static bool open_stream(struct aaudio *ctx)
{
    aaudio_result_t rc = AAudioStreamBuilder_openStream(ctx->builder, &ctx->stream);
    if (rc != AAUDIO_OK) {
        ctx->stream = NULL;
        __android_log_print(ANDROID_LOG_WARN, "PARSEC", "Audio unavailable: %d", rc);
        return false;
    }
    return true;
}

void aaudio_init(struct aaudio **ctx_out)
{
    struct aaudio *ctx = calloc(1, sizeof(*ctx));
    *ctx_out = ctx;
    if (!ctx) return;
    atomic_init(&ctx->muted, false);
    atomic_init(&ctx->disconnected, false);
    if (AAudio_createStreamBuilder(&ctx->builder) != AAUDIO_OK) {
        free(ctx);
        *ctx_out = NULL;
        return;
    }
    AAudioStreamBuilder_setSampleRate(ctx->builder, 48000);
    AAudioStreamBuilder_setChannelCount(ctx->builder, 2);
    AAudioStreamBuilder_setFormat(ctx->builder, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setPerformanceMode(ctx->builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setUsage(ctx->builder, AAUDIO_USAGE_MEDIA);
    AAudioStreamBuilder_setContentType(ctx->builder, AAUDIO_CONTENT_TYPE_MOVIE);
    AAudioStreamBuilder_setErrorCallback(ctx->builder, audio_error, ctx);
    open_stream(ctx);
}

void aaudio_mute(struct aaudio *ctx, bool muted)
{
    if (ctx) atomic_store(&ctx->muted, muted);
}

void aaudio_destroy(struct aaudio **ctx_out)
{
    if (!ctx_out || !*ctx_out) return;
    struct aaudio *ctx = *ctx_out;
    if (ctx->stream) {
        AAudioStream_requestStop(ctx->stream);
        AAudioStream_close(ctx->stream);
    }
    AAudioStreamBuilder_delete(ctx->builder);
    free(ctx);
    *ctx_out = NULL;
}

void aaudio_play(int16_t *pcm, uint32_t frames, void *opaque)
{
    struct aaudio *ctx = opaque;
    if (!ctx || !pcm || !frames) return;
    // Reopen on this worker, never inside AAudio's error callback.
    if (atomic_exchange(&ctx->disconnected, false)) {
        if (ctx->stream) AAudioStream_close(ctx->stream);
        ctx->stream = NULL;
        open_stream(ctx);
    }
    if (!ctx->stream) return;
    bool muted = atomic_load(&ctx->muted);
    if (muted) {
        if (!ctx->was_muted) {
            AAudioStream_requestPause(ctx->stream);
            AAudioStream_requestFlush(ctx->stream);
        }
        ctx->was_muted = true;
        return;
    }
    if (ctx->was_muted || AAudioStream_getState(ctx->stream) != AAUDIO_STREAM_STATE_STARTED)
        AAudioStream_requestStart(ctx->stream);
    ctx->was_muted = false;
    AAudioStream_write(ctx->stream, pcm, frames, 40000000);
}
