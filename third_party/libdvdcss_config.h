/* Minimal config.h for libdvdcss when building for Android with stream callbacks */
#ifndef CONFIG_H
#define CONFIG_H

#define PACKAGE_VERSION "1.6.0"
#define HAVE_ERRNO_H 1
#define HAVE_FCNTL_H 1
#define HAVE_SYS_PARAM_H 1
#define HAVE_SYS_STAT_H 1
#define HAVE_SYS_TYPES_H 1
#define HAVE_SYS_UIO_H 1
#define HAVE_UNISTD_H 1
/* Android: no DVD ioctls (we use stream callbacks only); device.c/ioctl.c will have empty stubs */
#define UNUSED __attribute__((unused))

#endif
