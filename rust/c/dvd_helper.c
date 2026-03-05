/**
 * DVD helper: reads IFO structures and returns JSON for titles/chapters.
 * Links against libdvdread. Used by Rust FFI.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <stdint.h>
#include <dvdread/ifo_read.h>
#include <dvdread/ifo_types.h>

#define DVD_BLOCK_LEN 2048

static int snappend(char *buf, size_t *pos, size_t cap, const char *fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    int n = vsnprintf(buf + *pos, cap - *pos, fmt, ap);
    va_end(ap);
    if (n > 0 && (size_t)n < cap - *pos) {
        *pos += (size_t)n;
        return 0;
    }
    return -1;
}

/**
 * Fills buf with JSON array of titles from VMGI TT_SRPT.
 * Returns 0 on success, -1 on error.
 */
int dvd_list_titles_json(void *dvd, char *buf, size_t buf_size) {
    dvd_reader_t *ctx = (dvd_reader_t *)dvd;
    if (!ctx || !buf || buf_size < 16) return -1;

    ifo_handle_t *ifo = ifoOpen(ctx, 0);
    if (!ifo) return -1;

    if (ifoRead_TT_SRPT(ifo) != 1) {
        ifoClose(ifo);
        return -1;
    }

    tt_srpt_t *tt = ifo->tt_srpt;
    if (!tt || tt->nr_of_srpts == 0) {
        ifoClose(ifo);
        buf[0] = '[';
        buf[1] = ']';
        buf[2] = '\0';
        return 0;
    }

    size_t pos = 0;
    if (snappend(buf, &pos, buf_size, "[") < 0) goto fail;
    for (unsigned int i = 0; i < tt->nr_of_srpts; i++) {
        title_info_t *t = &tt->title[i];
        if (i > 0) {
            if (snappend(buf, &pos, buf_size, ",") < 0) goto fail;
        }
        /* title_id is 1-based in DVD spec */
        if (snappend(buf, &pos, buf_size,
            "{\"titleNumber\":%u,\"titleSetNr\":%u,\"vtsTtn\":%u,\"chapters\":%u}",
            i + 1, (unsigned)t->title_set_nr, (unsigned)t->vts_ttn,
            (unsigned)t->nr_of_ptts) < 0) goto fail;
    }
    if (snappend(buf, &pos, buf_size, "]") < 0) goto fail;
    ifoClose(ifo);
    return 0;
fail:
    ifoClose(ifo);
    return -1;
}

/**
 * Fills buf with JSON array of chapters for the given title.
 * title_id is 1-based. Uses VMGI tt_srpt to find title_set_nr/vts_ttn,
 * then opens VTS IFO and reads VTS_PTT_SRPT.
 * Returns 0 on success, -1 on error.
 */
int dvd_list_chapters_json(void *dvd, int title_id, char *buf, size_t buf_size) {
    dvd_reader_t *ctx = (dvd_reader_t *)dvd;
    if (!ctx || !buf || buf_size < 16 || title_id < 1) return -1;

    ifo_handle_t *vmgi = ifoOpen(ctx, 0);
    if (!vmgi) return -1;
    if (ifoRead_TT_SRPT(vmgi) != 1) {
        ifoClose(vmgi);
        return -1;
    }
    tt_srpt_t *tt = vmgi->tt_srpt;
    if (!tt || (unsigned)title_id > tt->nr_of_srpts) {
        ifoClose(vmgi);
        return -1;
    }
    title_info_t *ti = &tt->title[title_id - 1];
    int title_set_nr = ti->title_set_nr;
    int vts_ttn = ti->vts_ttn;
    ifoClose(vmgi);

    ifo_handle_t *vts = ifoOpen(ctx, title_set_nr);
    if (!vts) return -1;
    if (ifoRead_VTS_PTT_SRPT(vts) != 1) {
        ifoClose(vts);
        return -1;
    }
    vts_ptt_srpt_t *ptt = vts->vts_ptt_srpt;
    if (!ptt || (unsigned)vts_ttn > ptt->nr_of_srpts) {
        ifoClose(vts);
        return -1;
    }
    ttu_t *ttu = &ptt->title[vts_ttn - 1];
    unsigned int nr = ttu->nr_of_ptts;
    if (nr == 0) {
        ifoClose(vts);
        buf[0] = '[';
        buf[1] = ']';
        buf[2] = '\0';
        return 0;
    }

    size_t pos = 0;
    if (snappend(buf, &pos, buf_size, "[") < 0) goto fail_ch;
    for (unsigned int i = 0; i < nr; i++) {
        if (i > 0) {
            if (snappend(buf, &pos, buf_size, ",") < 0) goto fail_ch;
        }
        /* Chapter: id (1-based), pgcn, pgn - no duration without TMAP */
        ptt_info_t *pi = &ttu->ptt[i];
        if (snappend(buf, &pos, buf_size, "{\"id\":%u,\"pgcn\":%u,\"pgn\":%u}",
            i + 1, (unsigned)pi->pgcn, (unsigned)pi->pgn) < 0) goto fail_ch;
    }
    if (snappend(buf, &pos, buf_size, "]") < 0) goto fail_ch;
    ifoClose(vts);
    return 0;
fail_ch:
    ifoClose(vts);
    return -1;
}
