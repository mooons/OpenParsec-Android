#include <stdlib.h>
#include <stdbool.h>
#include <string.h>
#include <pthread.h>
#include <stdatomic.h>
#include <unistd.h>
#include <jni.h>
#include <android/log.h>
#include "parsec.h"
#include "aaudio.h"

#define MAX_USER_DATA (1024 * 1024)
#define MAX_CURSOR_PIXELS (1024 * 1024)

/* Java serializes JNI calls; the audio worker owns only audio polling/playback. */
struct client_state {
    Parsec *parsec;
    struct aaudio *audio;
    pthread_t audio_thread;
    bool audio_started;
    atomic_bool stop_audio;
    int relative;
    int rumble;
    char *user_data[2];
    ParsecCursor cursor;
    int cursor_flags;
    uint8_t *cursor_png;
    size_t cursor_size;
};

static void *getPointer(JNIEnv *env, jobject instance, const char *name)
{
    jclass cls = (*env)->GetObjectClass(env, instance);
    jfieldID id = (*env)->GetFieldID(env, cls, name, "J");
    (*env)->DeleteLocalRef(env, cls);
    return (void *) (intptr_t) (*env)->GetLongField(env, instance, id);
}

static void setPointer(JNIEnv *env, jobject instance, const char *name, void *ptr)
{
    jclass cls = (*env)->GetObjectClass(env, instance);
    jfieldID id = (*env)->GetFieldID(env, cls, name, "J");
    (*env)->DeleteLocalRef(env, cls);
    (*env)->SetLongField(env, instance, id, (jlong) (intptr_t) ptr);
}

static void logCallback(ParsecLogLevel level, char *msg, void *opaque)
{
    if (msg) __android_log_print(ANDROID_LOG_INFO, "PARSEC", "%s", msg);
}

static void *audioWorker(void *opaque)
{
    struct client_state *s = opaque;
    while (!atomic_load(&s->stop_audio)) {
        ParsecStatus rc = ParsecClientPollAudio(s->parsec, aaudio_play, 16, s->audio);
        if (rc == PARSEC_NOT_RUNNING) usleep(20000);
    }
    return NULL;
}

static void stopAudio(struct client_state *s)
{
    if (s && s->audio_started) {
        atomic_store(&s->stop_audio, true);
        pthread_join(s->audio_thread, NULL);
        s->audio_started = false;
    }
}

JNIEXPORT void JNICALL
Java_parsec_bindings_Parsec_setLogCallback(JNIEnv *env, jobject instance)
{
    ParsecSetLogCallback(logCallback, NULL);
}

JNIEXPORT jint JNICALL
Java_parsec_bindings_Parsec_version(JNIEnv *env, jobject instance)
{
    return (jint) ParsecVersion();
}

JNIEXPORT void JNICALL
Java_parsec_bindings_Parsec_init(JNIEnv *env, jobject instance)
{
    if (getPointer(env, instance, "state")) return;
    struct client_state *s = calloc(1, sizeof(*s));
    ParsecStatus rc = s ? ParsecInit(PARSEC_VER, NULL, NULL, &s->parsec) : ERR_DEFAULT;
    if (rc != PARSEC_OK || !s->parsec) {
        free(s);
        jclass cls = (*env)->FindClass(env, "java/lang/IllegalStateException");
        (*env)->ThrowNew(env, cls, "Unable to initialize Parsec SDK");
        (*env)->DeleteLocalRef(env, cls);
        return;
    }
    atomic_init(&s->stop_audio, false);
    s->rumble = -1;
    aaudio_init(&s->audio);
    setPointer(env, instance, "state", s);
    setPointer(env, instance, "parsec", s->parsec);
}

JNIEXPORT void JNICALL
Java_parsec_bindings_Parsec_destroy(JNIEnv *env, jobject instance)
{
    struct client_state *s = getPointer(env, instance, "state");
    if (!s) return;
    stopAudio(s);
    ParsecClientDisconnect(s->parsec);
    ParsecDestroy(s->parsec);
    aaudio_destroy(&s->audio);
    free(s->user_data[0]);
    free(s->user_data[1]);
    free(s->cursor_png);
    free(s);
    setPointer(env, instance, "state", NULL);
    setPointer(env, instance, "parsec", NULL);
}

JNIEXPORT jint JNICALL
Java_parsec_bindings_Parsec_clientConnect(JNIEnv *env, jobject instance, jstring sessionID,
    jstring peerID, jint decoderSoftware, jint resolutionX, jint resolutionY, jint refreshRate)
{
    struct client_state *s = getPointer(env, instance, "state");
    if (!s || !sessionID || !peerID) return PARSEC_NOT_RUNNING;
    const char *session = (*env)->GetStringUTFChars(env, sessionID, NULL);
    if (!session) return ERR_DEFAULT;
    const char *peer = (*env)->GetStringUTFChars(env, peerID, NULL);
    if (!peer) { (*env)->ReleaseStringUTFChars(env, sessionID, session); return ERR_DEFAULT; }
    ParsecClientConfig cfg = PARSEC_CLIENT_DEFAULTS;
    cfg.decoderSoftware = decoderSoftware ? 1 : 0;
    cfg.resolutionX = resolutionX;
    cfg.resolutionY = resolutionY;
    cfg.refreshRate = refreshRate;
    // The bundled SDK's raw RGBA conversion returns blank images on current hosts.
    cfg.pngCursor = true;
    ParsecStatus rc = ParsecClientConnect(s->parsec, &cfg, (char *) session, (char *) peer);
    (*env)->ReleaseStringUTFChars(env, sessionID, session);
    (*env)->ReleaseStringUTFChars(env, peerID, peer);
    if (rc == PARSEC_OK && !s->audio_started) {
        atomic_store(&s->stop_audio, false);
        s->audio_started = pthread_create(&s->audio_thread, NULL, audioWorker, s) == 0;
    }
    return rc;
}

JNIEXPORT void JNICALL
Java_parsec_bindings_Parsec_clientDestroy(JNIEnv *env, jobject instance)
{
    struct client_state *s = getPointer(env, instance, "state");
    if (s) { stopAudio(s); ParsecClientDisconnect(s->parsec); }
}

JNIEXPORT void JNICALL
Java_parsec_bindings_Parsec_clientSetMuted(JNIEnv *env, jobject instance, jboolean muted)
{
    struct client_state *s = getPointer(env, instance, "state");
    if (s) aaudio_mute(s->audio, muted);
}

JNIEXPORT jint JNICALL
Java_parsec_bindings_Parsec_clientGetStatus(JNIEnv *env, jobject instance)
{
    Parsec *ps = getPointer(env, instance, "parsec");
    ParsecClientStatus status = {0};
    return ps ? ParsecClientGetStatus(ps, &status) : PARSEC_NOT_RUNNING;
}

JNIEXPORT void JNICALL
Java_parsec_bindings_Parsec_clientSetDimensions(JNIEnv *env, jobject instance, jint x, jint y)
{
    Parsec *ps = getPointer(env, instance, "parsec");
    if (ps && x > 0 && y > 0) ParsecClientSetDimensions(ps, (uint32_t) x, (uint32_t) y, 1.0f);
}

JNIEXPORT jint JNICALL
Java_parsec_bindings_Parsec_clientGLRenderFrame(JNIEnv *env, jobject instance)
{
    Parsec *ps = getPointer(env, instance, "parsec");
    return ps ? ParsecClientGLRenderFrame(ps, 0) : PARSEC_NOT_RUNNING;
}

JNIEXPORT void JNICALL
Java_parsec_bindings_Parsec_clientGLDestroy(JNIEnv *env, jobject instance)
{
    Parsec *ps = getPointer(env, instance, "parsec");
    if (ps) ParsecClientGLDestroy(ps);
}

JNIEXPORT jint JNICALL
Java_parsec_bindings_Parsec_clientSendMouseMotion(JNIEnv *env, jobject instance,
    jboolean relative, jint x, jint y)
{
    Parsec *parsec = getPointer(env, instance, "parsec");
    if (!parsec) return PARSEC_NOT_RUNNING;

    ParsecMessage msg = {};
    msg.type = MESSAGE_MOUSE_MOTION;
    msg.mouseMotion.relative = relative;
    msg.mouseMotion.x = x;
    msg.mouseMotion.y = y;

    return (jint) ParsecClientSendMessage(parsec, &msg);
}

JNIEXPORT jint JNICALL
Java_parsec_bindings_Parsec_clientSendMouseWheel(JNIEnv *env, jobject instance, jint x, jint y)
{
    Parsec *parsec = getPointer(env, instance, "parsec");
    if (!parsec) return PARSEC_NOT_RUNNING;

    ParsecMessage msg = {};
    msg.type = MESSAGE_MOUSE_WHEEL;
    msg.mouseWheel.x = x;
    msg.mouseWheel.y = y;

    return (jint) ParsecClientSendMessage(parsec, &msg);
}

JNIEXPORT jint JNICALL
Java_parsec_bindings_Parsec_clientSendGamepadButton(JNIEnv *env, jobject instance,
        jint gamepadID, jint button, jboolean pressed)
{
    Parsec *parsec = getPointer(env, instance, "parsec");
    if (!parsec) return PARSEC_NOT_RUNNING;

    ParsecMessage msg = {};
    msg.type = MESSAGE_GAMEPAD_BUTTON;
    msg.gamepadButton.button = (ParsecGamepadButton) button;
    msg.gamepadButton.id = (uint32_t) gamepadID;
    msg.gamepadButton.pressed = pressed;

    return (jint) ParsecClientSendMessage(parsec, &msg);
}

JNIEXPORT jint JNICALL
Java_parsec_bindings_Parsec_clientSendGamepadAxis(JNIEnv *env, jobject instance,
        jint gamepadID, jint axis, jint value)
{
    Parsec *parsec = getPointer(env, instance, "parsec");
    if (!parsec) return PARSEC_NOT_RUNNING;

    ParsecMessage msg = {};
    msg.type = MESSAGE_GAMEPAD_AXIS;
    msg.gamepadAxis.axis = (ParsecGamepadAxis) axis;
    msg.gamepadAxis.id = (uint32_t) gamepadID;
    // ParsecGamepadAxisMessage.value is int16_t; cast through that so the
    // sign bit is preserved when the Java side passes a negative axis value.
    msg.gamepadAxis.value = (int16_t) value;

    return (jint) ParsecClientSendMessage(parsec, &msg);
}

JNIEXPORT jint JNICALL
Java_parsec_bindings_Parsec_clientSendGamepadUnplug(JNIEnv *env, jobject instance,
        jint gamepadID)
{
    Parsec *parsec = getPointer(env, instance, "parsec");
    if (!parsec) return PARSEC_NOT_RUNNING;
    ParsecMessage msg = {};
    msg.type = MESSAGE_GAMEPAD_UNPLUG;
    msg.gamepadUnplug.id = (uint32_t) gamepadID;
    return (jint) ParsecClientSendMessage(parsec, &msg);
}

JNIEXPORT jint JNICALL
Java_parsec_bindings_Parsec_clientSendKeyboard(JNIEnv *env, jobject instance,
        jint keyCode, jint keyMod, jboolean pressed)
{
    Parsec *parsec = getPointer(env, instance, "parsec");
    if (!parsec) return PARSEC_NOT_RUNNING;

    ParsecMessage msg = {};
    msg.type = MESSAGE_KEYBOARD;
    msg.keyboard.code = (ParsecKeycode) keyCode;
    msg.keyboard.mod = (ParsecKeymod) keyMod;
    msg.keyboard.pressed = pressed;

    return (jint) ParsecClientSendMessage(parsec, &msg);
}

JNIEXPORT jint JNICALL
Java_parsec_bindings_Parsec_clientSendMouseButton(JNIEnv *env, jobject instance,
    jint button, jboolean pressed)
{
    Parsec *parsec = getPointer(env, instance, "parsec");
    if (!parsec) return PARSEC_NOT_RUNNING;

    ParsecMessage msg = {};
    msg.type = MESSAGE_MOUSE_BUTTON;
    msg.mouseButton.button = (ParsecMouseButton) button;
    msg.mouseButton.pressed = pressed;

    return (jint) ParsecClientSendMessage(parsec, &msg);
}

/* ---- Latency / decoder metrics for the stats overlay ---- */
JNIEXPORT jfloat JNICALL
Java_parsec_bindings_Parsec_clientGetDecodeLatency(JNIEnv *env, jobject instance)
{
    Parsec *parsec = getPointer(env, instance, "parsec");
    if (!parsec) return 0.0f;
    ParsecClientStatus status = {0};
    if (ParsecClientGetStatus(parsec, &status) != PARSEC_OK) return 0.0f;
    return status.metrics.decodeLatency;
}

JNIEXPORT jfloat JNICALL
Java_parsec_bindings_Parsec_clientGetNetworkLatency(JNIEnv *env, jobject instance)
{
    Parsec *parsec = getPointer(env, instance, "parsec");
    if (!parsec) return 0.0f;
    ParsecClientStatus status = {0};
    if (ParsecClientGetStatus(parsec, &status) != PARSEC_OK) return 0.0f;
    return status.metrics.networkLatency;
}

JNIEXPORT jfloat JNICALL
Java_parsec_bindings_Parsec_clientGetEncodeLatency(JNIEnv *env, jobject instance)
{
    Parsec *parsec = getPointer(env, instance, "parsec");
    if (!parsec) return 0.0f;
    ParsecClientStatus status = {0};
    if (ParsecClientGetStatus(parsec, &status) != PARSEC_OK) return 0.0f;
    return status.metrics.encodeLatency;
}

JNIEXPORT jboolean JNICALL
Java_parsec_bindings_Parsec_clientDecoderFellBack(JNIEnv *env, jobject instance)
{
    Parsec *parsec = getPointer(env, instance, "parsec");
    if (!parsec) return JNI_FALSE;
    ParsecClientStatus status = {0};
    if (ParsecClientGetStatus(parsec, &status) != PARSEC_OK) return JNI_FALSE;
    return status.decoderFallback ? JNI_TRUE : JNI_FALSE;
}


JNIEXPORT jlong JNICALL
Java_parsec_bindings_Parsec_clientGetFreezeSignal(JNIEnv *env, jobject instance)
{
    Parsec *ps = getPointer(env, instance, "parsec");
    if (!ps) return 0;
    ParsecClientStatus status = {0};
    if (ParsecClientGetStatus(ps, &status) != PARSEC_OK) return 0;
    jlong dec = (jlong) (status.metrics.decodeLatency * 1000.0f);
    jlong net = (jlong) (status.metrics.networkLatency * 1000.0f);
    return (dec << 32) | (net & 0xffffffffL);
}

JNIEXPORT jboolean JNICALL
Java_parsec_bindings_Parsec_clientHasNetworkFailure(JNIEnv *env, jobject instance)
{
    Parsec *ps = getPointer(env, instance, "parsec");
    ParsecClientStatus status = {0};
    return !ps || ParsecClientGetStatus(ps, &status) != PARSEC_OK || status.networkFailure;
}

JNIEXPORT void JNICALL
Java_parsec_bindings_Parsec_clientPollEvents(JNIEnv *env, jobject instance)
{
    struct client_state *s = getPointer(env, instance, "state");
    if (!s) return;
    ParsecClientEvent e;
    while (ParsecClientPollEvents(s->parsec, 0, &e)) {
        if (e.type == CLIENT_EVENT_CURSOR) {
            ParsecCursor c = e.cursor.cursor;
            if (c.modeUpdate) {
                s->relative = c.relative;
                s->cursor.positionX = c.positionX;
                s->cursor.positionY = c.positionY;
                s->cursor_flags |= 1;
            }
            if (c.imageUpdate) {
                void *buffer = ParsecGetBuffer(s->parsec, e.cursor.key);
                size_t count = (size_t) c.width * c.height;
                if (count > 0 && count <= MAX_CURSOR_PIXELS
                        && c.size > 0 && c.size <= MAX_CURSOR_PIXELS * 4 && buffer) {
                    uint8_t *png = malloc(c.size);
                    if (png) {
                        memcpy(png, buffer, c.size);
                        free(s->cursor_png);
                        s->cursor_png = png;
                        s->cursor_size = c.size;
                        s->cursor.width = c.width;
                        s->cursor.height = c.height;
                        s->cursor.hotX = c.hotX < c.width ? c.hotX : 0;
                        s->cursor.hotY = c.hotY < c.height ? c.hotY : 0;
                        s->cursor_flags |= 2;
                    }
                }
                if (buffer) ParsecFree(buffer);
            }
        } else if (e.type == CLIENT_EVENT_RUMBLE) {
            s->rumble = (e.rumble.motorBig << 8) | e.rumble.motorSmall;
        } else if (e.type == CLIENT_EVENT_USER_DATA) {
            char *buffer = ParsecGetBuffer(s->parsec, e.userData.key);
            int slot = e.userData.id == 11 ? 0 : e.userData.id == 12 ? 1 : -1;
            if (buffer && slot >= 0) {
                size_t length = strnlen(buffer, MAX_USER_DATA + 1);
                if (length <= MAX_USER_DATA) {
                    char *copy = strndup(buffer, length);
                    if (copy) { free(s->user_data[slot]); s->user_data[slot] = copy; }
                }
            }
            if (buffer) ParsecFree(buffer);
        }
    }
}

JNIEXPORT jboolean JNICALL
Java_parsec_bindings_Parsec_clientGetCursorRelative(JNIEnv *env, jobject instance)
{
    struct client_state *s = getPointer(env, instance, "state");
    return s && s->relative;
}

JNIEXPORT jintArray JNICALL
Java_parsec_bindings_Parsec_clientPollCursor(JNIEnv *env, jobject instance)
{
    struct client_state *s = getPointer(env, instance, "state");
    if (!s || !s->cursor_flags) return NULL;
    jint header[] = {s->relative, s->cursor_flags & 1, (s->cursor_flags & 2) != 0,
        (jint) s->cursor.positionX, (jint) s->cursor.positionY,
        s->cursor.width, s->cursor.height, s->cursor.hotX, s->cursor.hotY};
    jintArray result = (*env)->NewIntArray(env, 9);
    if (!result) return NULL;
    (*env)->SetIntArrayRegion(env, result, 0, 9, header);
    s->cursor_flags = 0;
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_parsec_bindings_Parsec_clientGetCursorPng(JNIEnv *env, jobject instance)
{
    struct client_state *s = getPointer(env, instance, "state");
    if (!s || !s->cursor_png) return NULL;
    jbyteArray result = (*env)->NewByteArray(env, s->cursor_size);
    if (result) (*env)->SetByteArrayRegion(env, result, 0, s->cursor_size, (jbyte *) s->cursor_png);
    return result;
}

JNIEXPORT jint JNICALL
Java_parsec_bindings_Parsec_clientPollRumble(JNIEnv *env, jobject instance)
{
    struct client_state *s = getPointer(env, instance, "state");
    if (!s) return -1;
    int value = s->rumble;
    s->rumble = -1;
    return value;
}

JNIEXPORT jbyteArray JNICALL
Java_parsec_bindings_Parsec_clientPollUserData(JNIEnv *env, jobject instance, jint id)
{
    struct client_state *s = getPointer(env, instance, "state");
    int slot = id == 11 ? 0 : id == 12 ? 1 : -1;
    if (!s || slot < 0 || !s->user_data[slot]) return NULL;
    char *data = s->user_data[slot];
    size_t length = strlen(data);
    jbyteArray result = (*env)->NewByteArray(env, length);
    if (!result) return NULL;
    (*env)->SetByteArrayRegion(env, result, 0, length, (const jbyte *) data);
    free(data);
    s->user_data[slot] = NULL;
    return result;
}

JNIEXPORT jint JNICALL
Java_parsec_bindings_Parsec_clientSendUserDataBytes(JNIEnv *env, jobject instance,
                                                  jint id, jbyteArray bytes)
{
    Parsec *ps = getPointer(env, instance, "parsec");
    if (!ps || !bytes) return PARSEC_NOT_RUNNING;
    jsize length = (*env)->GetArrayLength(env, bytes);
    if (length > MAX_USER_DATA || (id != 9 && id != 10 && id != 11)) return ERR_DEFAULT;
    char *data = calloc((size_t) length + 1, 1);
    if (!data) return ERR_DEFAULT;
    (*env)->GetByteArrayRegion(env, bytes, 0, length, (jbyte *) data);
    ParsecStatus rc = (*env)->ExceptionCheck(env) ? ERR_DEFAULT : ParsecClientSendUserData(ps, id, data);
    free(data);
    return rc;
}
