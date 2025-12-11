/*
 * libdvdcss configuration for Android
 * Auto-generated for Connectias project
 */

#ifndef DVDCSS_CONFIG_H
#define DVDCSS_CONFIG_H

/* Package info */
#define PACKAGE "libdvdcss"
#define PACKAGE_VERSION "1.5.0"

/* Standard headers */
#define HAVE_ERRNO_H 1
#define HAVE_FCNTL_H 1
#define HAVE_SYS_IOCTL_H 1
#define HAVE_SYS_PARAM_H 1
#define HAVE_SYS_STAT_H 1
#define HAVE_SYS_TYPES_H 1
#define HAVE_SYS_UIO_H 1
#define HAVE_UNISTD_H 1
#define HAVE_LIMITS_H 1
#define HAVE_INTTYPES_H 1
#define HAVE_STDINT_H 1

/* Android/Linux DVD IOCTL support */
#define DVD_STRUCT_IN_LINUX_CDROM_H 1
#define HAVE_LINUX_DVD_STRUCT 1

/* SCSI Generic support - using compatibility header */
#define HAVE_SCSI_SG_H 1

/* Symbol visibility */
#define SUPPORT_ATTRIBUTE_VISIBILITY_DEFAULT 1

/* We don't have broken mkdir on Android */
/* #define HAVE_BROKEN_MKDIR 1 */

#endif /* DVDCSS_CONFIG_H */
