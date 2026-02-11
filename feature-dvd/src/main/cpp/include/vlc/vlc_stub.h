#ifndef VLC_STUB_H
#define VLC_STUB_H

#include <stdint.h>
#include <sys/types.h>

// Minimal definitions extracted from vlc/vlc.h and vlc/libvlc_media.h
// to allow compilation without the full VLC SDK.

#ifdef __cplusplus
extern "C" {
#endif

typedef struct libvlc_instance_t libvlc_instance_t;
typedef struct libvlc_media_t libvlc_media_t;

// Callback function signatures
typedef int (*libvlc_media_open_cb)(void *opaque, void **datap, uint64_t *sizep);
typedef ssize_t (*libvlc_media_read_cb)(void *opaque, unsigned char *buf, size_t len);
typedef int (*libvlc_media_seek_cb)(void *opaque, uint64_t offset);
typedef void (*libvlc_media_close_cb)(void *opaque);

// Function signatures for dlsym
[[maybe_unused]] typedef libvlc_instance_t* (*libvlc_new_t)(int argc, const char * const *argv);
[[maybe_unused]] typedef void (*libvlc_release_t)(libvlc_instance_t *p_instance);

typedef libvlc_media_t* (*libvlc_media_new_callbacks_t)(
    libvlc_instance_t *instance,
    libvlc_media_open_cb open_cb,
    libvlc_media_read_cb read_cb,
    libvlc_media_seek_cb seek_cb,
    libvlc_media_close_cb close_cb,
    void *opaque
);

typedef void (*libvlc_media_release_t)(libvlc_media_t *p_md);

typedef struct libvlc_media_player_t libvlc_media_player_t;
typedef void (*libvlc_media_player_set_media_t)(libvlc_media_player_t *p_mi, libvlc_media_t *p_md);
typedef void (*libvlc_media_add_option_t)(libvlc_media_t *p_md, const char *psz_options);

#ifdef __cplusplus
}
#endif

#endif // VLC_STUB_H
