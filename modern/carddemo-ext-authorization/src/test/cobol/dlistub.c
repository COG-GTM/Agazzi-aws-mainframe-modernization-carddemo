/* Harness-only DL/I emulation for DBPAUTP0 (PAUTSUM0 root, PAUTDTL1 child), used by
 * run-x1-golden.sh. The database is loaded from PAUDBUNL-format files (X1_ROOT_IN: 100-byte
 * roots, X1_CHILD_IN: 6-byte ACCNTID + 200-byte child) and written back on every CHKP. */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define RLEN 100
#define CLEN 200
#define MAXR 4096
#define MAXC 65536

static unsigned char roots[MAXR][RLEN], kids[MAXC][6 + CLEN];
static int nroot, nkid, rdel[MAXR], kdel[MAXC], loaded;
static int cur_root = -1, cur_kid = -1;

static int load(const char *env, unsigned char *buf, int len, int max) {
  FILE *f = fopen(getenv(env), "rb");
  int n = 0;
  if (!f) { fprintf(stderr, "dlistub: cannot open %s\n", env); exit(12); }
  while (n < max && fread(buf + (size_t)n * len, len, 1, f) == 1) n++;
  fclose(f);
  return n;
}

static void dump(void) {
  FILE *r = fopen(getenv("X1_ROOT_OUT"), "wb"), *c = fopen(getenv("X1_CHILD_OUT"), "wb");
  for (int i = 0; i < nroot; i++) if (!rdel[i]) fwrite(roots[i], RLEN, 1, r);
  for (int i = 0; i < nkid; i++) if (!kdel[i]) fwrite(kids[i], 6 + CLEN, 1, c);
  fclose(r);
  fclose(c);
}

int DLISTUB(char *func, char *seg, unsigned char *area, char *dibstat) {
  if (!loaded) {
    nroot = load("X1_ROOT_IN", &roots[0][0], RLEN, MAXR);
    nkid = load("X1_CHILD_IN", &kids[0][0], 6 + CLEN, MAXC);
    loaded = 1;
  }
  memcpy(dibstat, "  ", 2);
  int root_seg = memcmp(seg, "PAUTSUM0", 8) == 0;
  if (memcmp(func, "GN  ", 4) == 0) {
    int i = cur_root + 1;
    while (i < nroot && rdel[i]) i++;
    if (i >= nroot) { memcpy(dibstat, "GB", 2); return 0; }
    cur_root = i;
    cur_kid = -1;
    memcpy(area, roots[i], RLEN);
  } else if (memcmp(func, "GNP ", 4) == 0) {
    int i = cur_kid + 1;
    for (; i < nkid; i++)
      if (!kdel[i] && memcmp(kids[i], roots[cur_root], 6) == 0) break;
    if (i >= nkid) { memcpy(dibstat, "GE", 2); return 0; }
    cur_kid = i;
    memcpy(area, kids[i] + 6, CLEN);
  } else if (memcmp(func, "DLET", 4) == 0) {
    if (root_seg) {
      rdel[cur_root] = 1;
      for (int i = 0; i < nkid; i++)
        if (memcmp(kids[i], roots[cur_root], 6) == 0) kdel[i] = 1;
    } else {
      kdel[cur_kid] = 1;
    }
  } else if (memcmp(func, "CHKP", 4) == 0) {
    dump();
  } else {
    memcpy(dibstat, "AD", 2);
  }
  return 0;
}
