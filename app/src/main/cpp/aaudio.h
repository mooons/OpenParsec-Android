#pragma once

#include <stdint.h>
#include <stdbool.h>

struct aaudio;

void aaudio_init(struct aaudio **ctx_out);
void aaudio_mute(struct aaudio *ctx, bool muted);
void aaudio_destroy(struct aaudio **ctx_out);
void aaudio_play(int16_t *pcm, uint32_t frames, void *opaque);
