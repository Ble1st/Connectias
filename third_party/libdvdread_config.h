/* Minimal config.h for libdvdread when building for Android with stream callbacks */
#ifndef CONFIG_H
#define CONFIG_H

#define PACKAGE_VERSION "7.1.0"
#define HAVE_DVDCSS_DVDCSS_H 1
#define HAVE_STRERROR_R 1
#define HAVE_DECL_STRERROR_R 1
#define HAVE_GETMNTENT_R 0
#define UNUSED __attribute__((unused))

#endif
