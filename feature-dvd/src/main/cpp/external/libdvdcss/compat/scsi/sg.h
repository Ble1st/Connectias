/*
 * SCSI Generic (sg) header compatibility layer for Android
 * Provides minimal definitions needed by libdvdcss
 */

#ifndef _SCSI_SG_H
#define _SCSI_SG_H

#include <stdint.h>

/* SCSI Generic data direction flags */
#define SG_DXFER_NONE       -1
#define SG_DXFER_TO_DEV     -2
#define SG_DXFER_FROM_DEV   -3
#define SG_DXFER_TO_FROM_DEV -4

/* SCSI Generic IO header structure */
typedef struct sg_io_hdr {
    int interface_id;           /* [i] 'S' for SCSI generic interface */
    int dxfer_direction;        /* [i] data transfer direction */
    unsigned char cmd_len;      /* [i] SCSI command length (<= 16 bytes) */
    unsigned char mx_sb_len;    /* [i] max length to write to sbp */
    unsigned short iovec_count; /* [i] 0 implies no scatter gather */
    unsigned int dxfer_len;     /* [i] byte count of data transfer */
    void *dxferp;               /* [i], [*io] points to data transfer memory */
    unsigned char *cmdp;        /* [i], [*i] points to command to perform */
    unsigned char *sbp;         /* [i], [*o] points to sense_buffer memory */
    unsigned int timeout;       /* [i] MAX_UINT -> no timeout */
    unsigned int flags;         /* [i] 0 -> default, see SG_FLAG... */
    int pack_id;                /* [i->o] unused internally */
    void *usr_ptr;              /* [i->o] unused internally */
    unsigned char status;       /* [o] scsi status */
    unsigned char masked_status;/* [o] shifted, masked scsi status */
    unsigned char msg_status;   /* [o] messaging level data (optional) */
    unsigned char sb_len_wr;    /* [o] byte count actually written to sbp */
    unsigned short host_status; /* [o] errors from host adapter */
    unsigned short driver_status;/* [o] errors from software driver */
    int resid;                  /* [o] dxfer_len - actual_transferred */
    unsigned int duration;      /* [o] time taken by cmd (unit: millisec) */
    unsigned int info;          /* [o] auxiliary information */
} sg_io_hdr_t;

/* SCSI Generic ioctl number - note: this won't work on Android as-is */
#define SG_IO 0x2285

#endif /* _SCSI_SG_H */
